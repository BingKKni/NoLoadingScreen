package io.github.bingkkni.noloadingscreen.mixin;

import io.github.bingkkni.noloadingscreen.platform.ClientUi;
import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.gui.LoadingHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Once the loading screen is gone the progress bar and status text continue on the HUD.
 * The central chunk-status rectangle is intentionally omitted.
 */
@Mixin(Hud.class)
public abstract class HudMixin {
	/**
	 * Deliberately not gated on {@code isHidden()}: this is a loading indicator, not HUD chrome,
	 * and vanilla's own loading screen ignores F1 too.
	 *
	 * <p>Drawn whether or not the tracker has anything in it. On a remote server it never does —
	 * neither the progress bar nor the chunk map is carried by any packet — and that is exactly the
	 * case where vanilla leaves you staring at an unlabelled screen wondering what is happening.
	 * The status line is the answer to that, so it does not get gated on the two sources that are
	 * only ever populated in singleplayer.
	 *
	 * <p>Use every RETURN, not just TAIL: Forge draws its layers and returns before the vanilla
	 * method's final return. A TAIL injection applies successfully but never runs on that path.
	 * Each completed invocation still draws at most once, after the HUD layers.
	 *
	 * <p>{@code require = 0}: an overlay is decoration. If another mod has already reshaped this
	 * method past recognition, the join still works without it.
	 */
	@Inject(method = "extractRenderState", at = @At("RETURN"), require = 0)
	private void nls$drawLoadingOverlay(
		final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker, final CallbackInfo ci
	) {
		if (ClientUi.screen(Minecraft.getInstance()) instanceof LevelLoadingScreen) {
			// Still mounted, so it is drawing the same thing already.
			return;
		}

		if (!NoLoadingScreen.shouldDrawOverlay()) {
			return;
		}

		graphics.nextStratum();
		LoadingHud.draw(new io.github.bingkkni.noloadingscreen.gui.LoadingCanvas(graphics), NoLoadingScreen.overlayTracker());
	}
}
