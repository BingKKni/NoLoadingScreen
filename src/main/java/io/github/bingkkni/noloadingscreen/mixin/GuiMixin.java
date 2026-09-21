package io.github.bingkkni.noloadingscreen.mixin;

import io.github.bingkkni.noloadingscreen.platform.ClientUi;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.bingkkni.noloadingscreen.gui.LoadingInventoryScreen;
import io.github.bingkkni.noloadingscreen.gui.LoadingPauseScreen;
import io.github.bingkkni.noloadingscreen.DisconnectedWorldView;
import net.minecraft.client.gui.screens.ChatScreen;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig;
import io.github.bingkkni.noloadingscreen.SavingWorldView;
import io.github.bingkkni.noloadingscreen.gui.LoadingHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Two hooks, for the two halves of "do not show me that screen".
 *
 * <p>{@code setScreen} is where a screen can be dropped outright, which is what we want whenever a
 * {@code ClientLevel} already exists — the mouse stays grabbed, the HUD comes back and the player is
 * simply in the world. It is also the only place that sees a screen change at all, so the timeline
 * is anchored here.
 *
 * <p>The render hook is the fallback for the part of a join where there is no world and no
 * placeholder standing in for one: dropping the screen there is not an option, because
 * {@code Gui#setScreen(null)} answers a null level with the title screen. Those screens stay mounted
 * and keep being ticked by vanilla; they just get drawn down to the parts worth looking at.
 * Vanilla's own background is kept — a bare progress bar on a black window looks like a crash, which
 * is the opposite of the point.
 */
@Mixin(Gui.class)
public abstract class GuiMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;tick(Z)V"))
	private void nls$tickPlaceholderHud(final Hud hud, final boolean paused, final Operation<Void> original) {
		// Rendering is bound, so vanilla can draw the selected-item name. Its countdown, however,
		// lives in Hud.tick and normally sees Minecraft.player == null between placeholder frames.
		// Bind only this existing HUD tick: the timer and item-change detection advance exactly once,
		// without exposing the disposable player to Gui's death/sleep/screen lifecycle checks.
		boolean bound = PlaceholderWorld.bind();
		try {
			original.call(hud, paused);
		} finally {
			if (bound) PlaceholderWorld.unbind();
		}
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;tick()V"))
	private void nls$tickLocalInventory(final Screen screen, final Operation<Void> original) {
		if (!(screen instanceof LoadingInventoryScreen || screen instanceof io.github.bingkkni.noloadingscreen.gui.LoadingCreativeInventoryScreen)) {
			original.call(screen);
			return;
		}
		// AbstractContainerScreen.tick is final and dereferences the player. Scope this binding to
		// just the screen call; the separate wrapper above owns the one HUD tick that needs a player,
		// while Gui's death/sleep/screen lifecycle and the configuration connection remain unbound.
		if (!PlaceholderWorld.bind()) return;
		try {
			original.call(screen);
		} finally {
			PlaceholderWorld.unbind();
		}
	}

	@WrapMethod(method = "setScreen")
	private void nls$returnToPlaceholder(final Screen screen, final Operation<Void> original) {
		io.github.bingkkni.noloadingscreen.ScreenTransitions.setScreen(screen, original);
	}

	@ModifyExpressionValue(method = "setScreen", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Gui;clientLevelTeardownInProgress:Z"))
	private boolean nls$allowLocalSavingUi(final boolean teardown) {
		// Keep the real teardown flag set (including canInterruptWithAnotherScreen). Only the
		// null-screen guard may accept our bound, disconnected saving/KickWarn player.
		return teardown && !((SavingWorldView.visible() || DisconnectedWorldView.visible()) && Minecraft.getInstance().player != null
			&& PlaceholderWorld.owns(Minecraft.getInstance().player));
	}

	@Redirect(method = "setScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;releaseAll()V"))
	private void nls$keepHeldTransferKeys() {
		// The transient reconfiguration screen is removed in the same packet handler. Releasing
		// here invents a key-up/key-down edge and interrupts sprint-jumps. Real menus still release.
		if (!NoLoadingScreen.shouldKeepEnginesForAdoption()) KeyMapping.releaseAll();
	}

	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen nls$interceptScreen(final Screen screen) {
		return io.github.bingkkni.noloadingscreen.ScreenTransitions.intercept(screen);
	}

	/**
	 * {@code require = 0} on purpose. This one only replaces what a loading screen <em>draws</em>,
	 * and it is the fallback for the case where the placeholder world is already off — losing it
	 * costs a nicer-looking screen, nothing else. Any other mod that redirects the same call rewrites
	 * the instruction before this can find it, and a cosmetic touch is not worth refusing to boot a
	 * modpack over.
	 */
	@Redirect(
		method = "extractRenderState",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"
		),
		require = 0
	)
	private void nls$stripMountedScreen(
		final Screen screen, final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick
	) {
		NoLoadingScreenConfig config = NoLoadingScreenConfig.get();

		if (config.enabled
			&& screen instanceof LevelLoadingScreen loadingScreen
			&& Minecraft.getInstance().level == null) {
			LevelLoadTracker tracker = LoadingHud.trackerOf(loadingScreen);
			// Cosmetic fallback: keep vanilla's background, but only the progress bar and text.
			// The central chunk-status rectangle is intentionally absent.
			screen.extractBackground(graphics, mouseX, mouseY, partialTick);
			LoadingHud.draw(new io.github.bingkkni.noloadingscreen.gui.LoadingCanvas(graphics), tracker);
			return;
		}

		screen.extractRenderStateWithTooltipAndSubtitles(graphics, mouseX, mouseY, partialTick);
	}
}
