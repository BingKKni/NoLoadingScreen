package io.github.bingkkni.noloadingscreen.mixin;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityAccessor {
	@Invoker("collide") Vec3 nls$collide(Vec3 movement);
	@Invoker("doWaterSplashEffect") void nls$doWaterSplashEffect();
	@Invoker("getSwimSplashSound") SoundEvent nls$swimSplashSound();
	@Invoker("getSwimHighSpeedSplashSound") SoundEvent nls$swimHighSpeedSplashSound();
	@Accessor("fluidInteraction") EntityFluidInteraction nls$fluidInteraction();
	@Accessor("wasTouchingWater") boolean nls$wasTouchingWater();
	@Accessor("wasTouchingWater") void nls$setTouchingWater(boolean value);
	@Accessor("wasEyeInWater") void nls$setEyeInWater(boolean value);
}
