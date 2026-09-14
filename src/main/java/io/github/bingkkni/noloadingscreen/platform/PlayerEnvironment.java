package io.github.bingkkni.noloadingscreen.platform;

import io.github.bingkkni.noloadingscreen.mixin.EntityAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Visual fluid queries without currents, sounds or game events; native movement modifiers. */
public final class PlayerEnvironment {
	private PlayerEnvironment() {}
	public static double frictionModifier(LocalPlayer player) { return player.getAttributeValue(Attributes.FRICTION_MODIFIER); }
	public static double airDragModifier(LocalPlayer player) { return player.getAttributeValue(Attributes.AIR_DRAG_MODIFIER); }
	public static void sampleFluids(LocalPlayer player) {
		EntityAccessor entity = (EntityAccessor) player;
		entity.nls$fluidInteraction().update(player, true);
		entity.nls$setTouchingWater(entity.nls$fluidInteraction().isInFluid(FluidTags.WATER));
		entity.nls$setEyeInWater(player.isEyeInFluid(FluidTags.WATER));
	}
}
