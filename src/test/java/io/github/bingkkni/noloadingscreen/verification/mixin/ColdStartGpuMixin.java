package io.github.bingkkni.noloadingscreen.verification.mixin;

import io.github.bingkkni.noloadingscreen.verification.ColdStartGpuVerification;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ColdStartGpuMixin {
	@Inject(method = "runTick", at = @At("RETURN"))
	private void nls$verifyColdGpuFrame(final boolean advanceGameTime, final CallbackInfo ci) throws Exception {
		if (Boolean.getBoolean("nls.verify.firstJoinGpu")) io.github.bingkkni.noloadingscreen.verification.FirstJoinGpuVerification.afterFrame((Minecraft) (Object) this);
		else if (Boolean.getBoolean("nls.verify.coldStartGpu")) ColdStartGpuVerification.afterFrame((Minecraft) (Object) this);
	}
}
