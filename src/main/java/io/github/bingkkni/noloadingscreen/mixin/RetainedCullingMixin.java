package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** EntityCulling's worker cannot see an unbound retained level; its old occlusion result is stale. */
@Mixin(targets = {"net.minecraft.world.entity.Entity", "net.minecraft.world.level.block.entity.BlockEntity"}, priority = 900)
public abstract class RetainedCullingMixin {
	@Dynamic("Public Cullable API added by EntityCulling at priority 1000")
	@ModifyReturnValue(method = "isCulled", at = @At("RETURN"), remap = false)
	private boolean nls$retainedVisibility(boolean original) {
		return original && !PlaceholderWorld.retainsForRendering(this);
	}
}
