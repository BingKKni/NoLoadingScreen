package io.github.bingkkni.noloadingscreen.smoke.mixin;

import io.github.bingkkni.noloadingscreen.smoke.GpuSmokeVerification;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class SmokeFrameMixin {
	@Inject(method = "runTick", at = @At("RETURN"))
	private void nls$smokeAfterFrame(final boolean advance, final CallbackInfo ci) throws Exception {
		GpuSmokeVerification.afterFrame((Minecraft) (Object) this);
	}
}
