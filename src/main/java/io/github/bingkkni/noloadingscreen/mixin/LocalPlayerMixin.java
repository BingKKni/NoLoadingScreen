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
	 * Runs after {@code travel()} has already applied gravity, so this is the last chance to undo a
	 * fall through terrain that has not arrived yet.
	 */
	@Inject(method = "aiStep", at = @At("TAIL"))
	private void nls$holdPosition(final CallbackInfo ci) {
		NoLoadingScreen.onPlayerAiStep((LocalPlayer) (Object) this);
	}
}
