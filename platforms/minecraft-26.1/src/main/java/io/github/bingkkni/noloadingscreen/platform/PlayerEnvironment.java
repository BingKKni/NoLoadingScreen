package io.github.bingkkni.noloadingscreen.platform;

import io.github.bingkkni.noloadingscreen.PlaceholderBlockEffects;
import io.github.bingkkni.noloadingscreen.mixin.EntityAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Local fluid state, transition feedback and native movement modifiers. */
public final class PlayerEnvironment {
	private PlayerEnvironment() {}
	// 26.1 uses the fixed vanilla coefficients already applied by PlaceholderMovement.
	public static double frictionModifier(LocalPlayer player) { return 1.0; }
	public static double airDragModifier(LocalPlayer player) { return 1.0; }
	public static double waterMovementEfficiency(LocalPlayer player) { return player.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY); }
	public static boolean sampleFluids(Entity subject) {
		EntityAccessor entity = (EntityAccessor) subject;
		boolean previous = entity.nls$wasTouchingWater();
		entity.nls$fluidInteraction().update(subject, true);
		boolean touching = entity.nls$fluidInteraction().isInFluid(FluidTags.WATER);
		entity.nls$setTouchingWater(touching);
		entity.nls$setEyeInWater(subject.isEyeInFluid(FluidTags.WATER));
		if (!previous && touching && !subject.isSpectator()) PlaceholderBlockEffects.splash(subject);
		return touching;
	}
}
