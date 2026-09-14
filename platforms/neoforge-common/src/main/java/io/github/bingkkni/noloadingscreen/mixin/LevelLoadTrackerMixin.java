package io.github.bingkkni.noloadingscreen.mixin;

import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelLoadTracker.class)
public abstract class LevelLoadTrackerMixin {
	@Inject(method = "startClientLoad", at = @At("RETURN"))
	private void nls$onLoadStart(final LocalPlayer player, final ClientLevel level, final net.minecraft.client.renderer.LevelRenderer renderer, final CallbackInfo ci) {
		NoLoadingScreen.onLoadStart((LevelLoadTracker) (Object) this);
	}

	@Inject(method = "loadingPacketsReceived", at = @At("HEAD"))
	private void nls$onLoadingPacketsReceived(final CallbackInfo ci) {
		NoLoadingScreen.onLoadingPacketsReceived();
	}

	@Inject(method = "tickClientLoad", at = @At("HEAD"))
	private void nls$onTick(final CallbackInfo ci) {
		NoLoadingScreen.onTrackerTick((LevelLoadTracker) (Object) this);
	}

	@Inject(method = "isLevelReady", at = @At("RETURN"))
	private void nls$onLevelReady(final CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			NoLoadingScreen.onLevelReady();
		}
	}
}
