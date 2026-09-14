package io.github.bingkkni.noloadingscreen.mixin;

import io.github.bingkkni.noloadingscreen.PlaceholderBlockEffects;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
	@Inject(method = "add", at = @At("HEAD"), cancellable = true)
	private void nls$captureLocalDebris(final Particle particle, final CallbackInfo ci) {
		if (PlaceholderBlockEffects.capture((ParticleEngine) (Object) this, particle)) ci.cancel();
	}

	@Inject(method = "extract", at = @At("RETURN"))
	private void nls$extractLocalDebris(final ParticlesRenderState state, final Frustum frustum,
		final Camera camera, final float partialTick, final CallbackInfo ci) {
		PlaceholderBlockEffects.extract((ParticleEngine) (Object) this, camera, debris -> state.add(
			debris.extractRenderState(frustum, camera, io.github.bingkkni.noloadingscreen.PlaceholderWorld.localPartialTick(partialTick))));
	}

	@Inject(method = "clearParticles", at = @At("HEAD"))
	private void nls$clearLocalDebris(final CallbackInfo ci) {
		PlaceholderBlockEffects.clearFor((ParticleEngine) (Object) this);
	}
}
