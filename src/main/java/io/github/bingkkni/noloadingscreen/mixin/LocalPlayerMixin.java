package io.github.bingkkni.noloadingscreen.mixin;

import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	/**
	 * Vanilla skips the whole tick until {@code ServerboundPlayerLoadedPacket} has been sent. With
	 * the loading screen gone that window is on screen, so the visible head/body have to follow
	 * the view in it.
	 */
	@Inject(method = "tick", at = @At("HEAD"))
	private void nls$followViewBeforeLoaded(final CallbackInfo ci) {
		NoLoadingScreen.beforePlayerTick((LocalPlayer) (Object) this);
	}

	@Inject(method = "aiStep", at = @At("HEAD"))
	private void nls$prepareHold(final CallbackInfo ci) {
		NoLoadingScreen.beforePlayerAiStep((LocalPlayer) (Object) this);
	}

	/**
	 * Runs after {@code travel()} has already applied gravity, so this is the last chance to undo a
	 * fall through terrain that has not arrived yet.
	 */
	@Inject(method = "aiStep", at = @At("TAIL"))
	private void nls$holdPosition(final CallbackInfo ci) {
		NoLoadingScreen.onPlayerAiStep((LocalPlayer) (Object) this);
	}
}
