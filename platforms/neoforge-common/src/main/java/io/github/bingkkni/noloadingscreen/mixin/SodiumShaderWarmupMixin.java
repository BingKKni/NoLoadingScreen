package io.github.bingkkni.noloadingscreen.mixin;

import io.github.bingkkni.noloadingscreen.compat.SodiumShaderWarmup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class SodiumShaderWarmupMixin {
	@Inject(method = "<init>", at = @At("RETURN"))
	private void nls$adoptWarmPrograms(@Coerce final Object device, @Coerce final Object vertex, final CallbackInfo ci) {
		SodiumShaderWarmup.adopt(this, device, vertex);
	}
}
