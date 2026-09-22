package io.github.bingkkni.noloadingscreen.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("attackStrengthTicker") int nls$attackStrengthTicker();
	@Accessor("attackStrengthTicker") void nls$setAttackStrengthTicker(int ticks);
	@Accessor("itemSwapTicker") int nls$itemSwapTicker();
	@Accessor("itemSwapTicker") void nls$setItemSwapTicker(int ticks);
	@Invoker("getJumpPower") float nls$jumpPower();
	@Invoker("updateSwimAmount") void nls$updateSwimAmount();
	@Invoker("updateInvisibilityStatus") void nls$updateInvisibilityStatus();
	@Invoker("updateSwingTime") void nls$updateSwingTime();
	@Invoker("tickHeadTurn") void nls$tickHeadTurn(float bodyTarget);
}
