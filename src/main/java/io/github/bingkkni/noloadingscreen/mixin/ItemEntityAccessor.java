package io.github.bingkkni.noloadingscreen.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemEntity.class)
public interface ItemEntityAccessor {
	@Accessor("age") int nls$age();
	@Accessor("age") void nls$age(int value);
	@Accessor("pickupDelay") int nls$pickupDelay();
	@Accessor("pickupDelay") void nls$pickupDelay(int value);
}
