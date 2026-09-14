package io.github.bingkkni.noloadingscreen.platform;

import io.github.bingkkni.noloadingscreen.mixin.EntityAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;

/** Visual fluid queries without currents, sounds or game events; native movement modifiers. */
public final class PlayerEnvironment {
	private PlayerEnvironment() {}
	// 26.1 uses the fixed vanilla coefficients already applied by PlaceholderMovement.
	public static double frictionModifier(LocalPlayer player) { return 1.0; }
	public static double airDragModifier(LocalPlayer player) { return 1.0; }
	public static void sampleFluids(LocalPlayer player) {
		EntityAccessor entity = (EntityAccessor) player;
		entity.nls$fluidInteraction().update(player, true);
		entity.nls$setTouchingWater(entity.nls$fluidInteraction().isInFluid(FluidTags.WATER));
		entity.nls$setEyeInWater(player.isEyeInFluid(FluidTags.WATER));
	}
}
