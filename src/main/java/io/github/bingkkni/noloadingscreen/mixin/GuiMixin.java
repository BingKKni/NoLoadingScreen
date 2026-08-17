package io.github.bingkkni.noloadingscreen.mixin;

import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig;
import io.github.bingkkni.noloadingscreen.gui.LoadingHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen nls$interceptScreen(final Screen screen) {
		if (screen instanceof LevelLoadingScreen) {
			NoLoadingScreen.markTimeline("地形加载界面出现 (加载地形中...)");
		} else if (screen == null && NoLoadingScreen.timelineActive()) {
			NoLoadingScreen.markTimeline("加载界面关闭，画面交还给玩家");
		}

		NoLoadingScreenConfig config = NoLoadingScreenConfig.get();
		if (config.enabled && screen instanceof LevelLoadingScreen) {
			// Only when a level is actually there to look at. Before the login packet arrives there
			// is no ClientLevel, and setScreen(null) would answer that with the title screen — the
			// placeholder world covers that window instead, and drops its own screen from install().
			if (Minecraft.getInstance().level != null) {
				NoLoadingScreen.markTimeline("NoLoadingScreen 隐藏了地形加载界面，直接交还画面");
				return null;
			}
		}

		return screen;
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
			// Vanilla's panorama/menu background, then our own progress bar, chunk map and status
			// line in place of vanilla's — same position, same size, one more line of text.
			screen.extractBackground(graphics, mouseX, mouseY, partialTick);
			LoadingHud.draw(graphics, tracker);
			return;
		}

		screen.extractRenderStateWithTooltipAndSubtitles(graphics, mouseX, mouseY, partialTick);
	}
}
