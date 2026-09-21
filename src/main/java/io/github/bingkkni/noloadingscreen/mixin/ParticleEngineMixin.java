package io.github.bingkkni.noloadingscreen.mixin;

import io.github.bingkkni.noloadingscreen.PlaceholderBlockEffects;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Local break debris is captured here and extracted at the level extractor's call site. */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
	@Inject(method = "add", at = @At("HEAD"), cancellable = true)
	private void nls$captureLocalDebris(final Particle particle, final CallbackInfo ci) {
		if (PlaceholderBlockEffects.capture((ParticleEngine) (Object) this, particle)) ci.cancel();
	}

	@Inject(method = "clearParticles", at = @At("HEAD"))
	private void nls$clearLocalDebris(final CallbackInfo ci) {
		PlaceholderBlockEffects.clearFor((ParticleEngine) (Object) this);
	}
}
