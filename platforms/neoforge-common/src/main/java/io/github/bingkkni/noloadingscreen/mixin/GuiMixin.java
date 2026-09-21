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
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;


/** UI ownership resides on Minecraft in 1.21; policy remains shared. */
@Mixin(Minecraft.class)
public abstract class GuiMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;tick(Z)V"))
	private void nls$tickPlaceholderHud(final Gui hud, final boolean paused, final Operation<Void> original) {
		// Rendering is bound, so vanilla can draw the selected-item name. Its countdown, however,
		// lives in Gui.tick and normally sees Minecraft.player == null between placeholder frames.
		// Bind only this existing HUD tick: the timer and item-change detection advance exactly once,
		// without exposing the disposable player to Minecraft's death/sleep/screen lifecycle checks.
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
		// while Minecraft's death/sleep/screen lifecycle and the configuration connection remain unbound.
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

	@ModifyExpressionValue(method = "setScreen", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;clientLevelTeardownInProgress:Z"))
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

}
