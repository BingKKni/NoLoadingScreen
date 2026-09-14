package io.github.bingkkni.noloadingscreen.smoke.mixin;

import io.github.bingkkni.noloadingscreen.smoke.GpuSmokeVerification;

import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class SmokeRenderMixin {
	@Inject(method = "renderLevel", at = @At("RETURN"))
	private void nls$smokeWorldRendered(final CallbackInfo ci) {
		if (PlaceholderWorld.active()) GpuSmokeVerification.worldFrames++;
	}
	@Inject(method = "render", at = @At(value = "INVOKE", target =
		"Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
	private void nls$smokeRecoverableFailure(final CallbackInfo ci) {
		if (GpuSmokeVerification.failNext && PlaceholderWorld.active()) {
			GpuSmokeVerification.failNext = false;
			GpuSmokeVerification.failureInjected = true;
			throw new IllegalStateException("NLS expected isolated GPU smoke frame failure");
		}
	}
}
