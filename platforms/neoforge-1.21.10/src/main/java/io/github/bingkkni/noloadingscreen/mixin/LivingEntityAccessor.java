package io.github.bingkkni.noloadingscreen.mixin;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.21.10 ItemInHandRenderer uses attackStrengthTicker for item raise/lower animation. */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("attackStrengthTicker") int nls$attackStrengthTicker();
	@Accessor("attackStrengthTicker") void nls$setAttackStrengthTicker(int ticks);
	@Accessor("attackStrengthTicker") int nls$itemSwapTicker();
	@Accessor("attackStrengthTicker") void nls$setItemSwapTicker(int ticks);
	@Invoker("getJumpPower") float nls$jumpPower();
	@Invoker("getWaterSlowDown") float nls$waterSlowDown();
	@Invoker("getHurtSound") SoundEvent nls$hurtSound(DamageSource source);
	@Invoker("getDeathSound") SoundEvent nls$deathSound();
	@Invoker("getSoundVolume") float nls$soundVolume();
	@Invoker("updateSwimAmount") void nls$updateSwimAmount();
	@Invoker("updateInvisibilityStatus") void nls$updateInvisibilityStatus();
	@Invoker("updateSwingTime") void nls$updateSwingTime();
	@Invoker("tickHeadTurn") void nls$tickHeadTurn(float bodyTarget);
}
