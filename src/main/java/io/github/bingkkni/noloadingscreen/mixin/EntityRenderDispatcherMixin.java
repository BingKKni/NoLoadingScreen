package io.github.bingkkni.noloadingscreen.mixin;

import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	@ModifyVariable(method = "extractEntity", at = @At("HEAD"), argsOnly = true)
	private float nls$splitEntityClock(final float partialTick, final Entity entity, final float originalPartialTick) {
		return PlaceholderWorld.entityPartialTick(entity, partialTick);
	}
}
