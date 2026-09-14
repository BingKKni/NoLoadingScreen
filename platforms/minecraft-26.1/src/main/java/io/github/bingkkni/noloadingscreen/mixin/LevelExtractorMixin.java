package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Only the owned local player bypasses absent meshes; vanilla camera/frustum rules remain. */
@Mixin(LevelRenderer.class)
public abstract class LevelExtractorMixin {
	@WrapOperation(method = "extractVisibleEntities", at = @At(value = "INVOKE", target =
		"Lnet/minecraft/client/renderer/LevelRenderer;isSectionCompiledAndVisible(Lnet/minecraft/core/BlockPos;)Z"))
	private boolean nls$showLocalPlayerWithoutTerrain(final LevelRenderer renderer, final BlockPos pos,
		final Operation<Boolean> original, @Local final Entity entity) {
		return entity instanceof LocalPlayer player && PlaceholderWorld.owns(player) || original.call(renderer, pos);
	}
}
