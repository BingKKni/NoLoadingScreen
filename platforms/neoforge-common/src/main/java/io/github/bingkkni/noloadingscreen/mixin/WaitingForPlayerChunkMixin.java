package io.github.bingkkni.noloadingscreen.mixin;

import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep vanilla's ClientLevelReady transition, close delay and loaded notification. */
@Mixin(targets = "net.minecraft.client.multiplayer.LevelLoadTracker$WaitingForPlayerChunk")
public abstract class WaitingForPlayerChunkMixin {
	@Inject(method = "isReady", at = @At("HEAD"), cancellable = true)
	private void nls$releaseChunkReadiness(final CallbackInfoReturnable<Boolean> cir) {
		if (NoLoadingScreenConfig.get().enabled) {
			NoLoadingScreen.onGateReleased();
			cir.setReturnValue(true);
		}
	}
}
