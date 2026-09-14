package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.bingkkni.noloadingscreen.LoadingWork;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
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
}
