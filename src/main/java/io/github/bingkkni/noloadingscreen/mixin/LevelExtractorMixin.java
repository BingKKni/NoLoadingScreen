package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.bingkkni.noloadingscreen.LoadingWork;
import io.github.bingkkni.noloadingscreen.PlaceholderBlockEffects;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
	@Inject(method = "onResourceManagerReload", at = @At("HEAD"))
	private void nls$invalidateFreshRenderer(final CallbackInfo ci) {
		LoadingWork.resourcesReloaded();
	}

	@WrapOperation(method = "isEntityVisible", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/LevelRenderer;isSectionCompiledAndVisible(Lnet/minecraft/core/BlockPos;)Z"))
	private boolean nls$showLocalPlayerWithoutTerrain(final LevelRenderer renderer, final BlockPos pos,
		final Operation<Boolean> original, final Entity entity, final Frustum frustum,
		final double camX, final double camY, final double camZ) {
		// The void has no chunk meshes. Bypass only that test for our bound local player; vanilla
		// still owns entity extraction, frustum culling, first/third-person rules, skin and animation.
		return entity instanceof LocalPlayer player && PlaceholderWorld.owns(player) || original.call(renderer, pos);
	}

	/**
	 * Local break debris lives outside the engine's own particle map, so it is extracted at this
	 * call site rather than inside {@code ParticleEngine.extract}: an optimizer that returns early
	 * from an empty engine (BadOptimizations' particle-manager optimization) would skip a hook
	 * placed in the callee, and the engine is always empty while a placeholder is bound.
	 */
	@WrapOperation(method = "extract", at = @At(value = "INVOKE", target =
		"Lnet/minecraft/client/particle/ParticleEngine;extract(Lnet/minecraft/client/renderer/state/level/ParticlesRenderState;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/Camera;F)V"))
	private void nls$extractLocalDebris(final ParticleEngine engine, final ParticlesRenderState state, final Frustum frustum,
		final Camera camera, final float partialTick, final Operation<Void> original) {
		original.call(engine, state, frustum, camera, partialTick);
		PlaceholderBlockEffects.extract(engine, camera, debris -> state.add(
			debris.extractRenderState(frustum, camera, PlaceholderWorld.localPartialTick(partialTick))));
	}
}
