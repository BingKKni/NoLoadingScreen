package io.github.bingkkni.noloadingscreen.smoke.mixin;

import io.github.bingkkni.noloadingscreen.smoke.RepairGpuVerification;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class RepairFrameMixin {
	@Inject(method = "runTick", at = @At("RETURN"))
	private void nls$repairFrame(final boolean advance, final CallbackInfo ci) throws Exception {
		RepairGpuVerification.afterFrame((Minecraft) (Object) this);
	}

	/** Only newer clients split presentation out of runTick; their synchronous waits draw with it. */
	@Inject(method = "renderFrame", at = @At("RETURN"), require = 0)
	private void nls$repairWaitFrame(final boolean advance, final CallbackInfo ci) throws Exception {
		RepairGpuVerification.afterWaitFrame((Minecraft) (Object) this);
	}
}
