package io.github.bingkkni.noloadingscreen.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 26.3 keeps swing progress in a private SwingState instead of the attackAnim fields. */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("attackStrengthTicker") int nls$attackStrengthTicker();
	@Accessor("attackStrengthTicker") void nls$setAttackStrengthTicker(int ticks);
	@Accessor("itemSwapTicker") int nls$itemSwapTicker();
	@Accessor("itemSwapTicker") void nls$setItemSwapTicker(int ticks);
	@Accessor("swingState") LivingEntity.SwingState nls$swingState();
	@Invoker("getJumpPower") float nls$jumpPower();
	@Invoker("updateSwimAmount") void nls$updateSwimAmount();
	@Invoker("updateInvisibilityStatus") void nls$updateInvisibilityStatus();
	@Invoker("tickHeadTurn") void nls$tickHeadTurn(float bodyTarget);
}
