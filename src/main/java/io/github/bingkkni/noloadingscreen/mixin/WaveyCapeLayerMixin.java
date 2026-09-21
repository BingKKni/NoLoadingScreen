package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.bingkkni.noloadingscreen.compat.WaveyCapesCompatibility;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** WaveyCapes reads Minecraft's frozen clock instead of the entity extraction clock. */
@Pseudo
@Mixin(targets = "dev.tr7zw.waveycapes.renderlayers.CustomCapeRenderLayer", remap = false)
public abstract class WaveyCapeLayerMixin {
	@ModifyExpressionValue(method = "submit", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/DeltaTracker;getGameTimeDeltaPartialTick(Z)F"))
	private float nls$capeClock(float original, @Local(argsOnly = true) AvatarRenderState state) {
		return WaveyCapesCompatibility.partialTick(state, original);
	}
}
