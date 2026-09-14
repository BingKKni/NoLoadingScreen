package io.github.bingkkni.noloadingscreen.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityAccessor {
	@Invoker("collide") Vec3 nls$collide(Vec3 movement);
	@Accessor("fluidInteraction") EntityFluidInteraction nls$fluidInteraction();
	@Accessor("wasTouchingWater") void nls$setTouchingWater(boolean value);
	@Accessor("wasEyeInWater") void nls$setEyeInWater(boolean value);
}
