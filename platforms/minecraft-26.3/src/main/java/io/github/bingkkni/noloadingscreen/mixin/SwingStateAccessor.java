package io.github.bingkkni.noloadingscreen.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The current swing progress, which {@code LivingEntity.tick} reads directly rather than through a getter. */
@Mixin(LivingEntity.SwingState.class)
public interface SwingStateAccessor {
	@Accessor("animation") float nls$animation();
}
