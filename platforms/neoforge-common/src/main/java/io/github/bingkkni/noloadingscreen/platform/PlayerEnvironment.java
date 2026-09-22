package io.github.bingkkni.noloadingscreen.platform;

import io.github.bingkkni.noloadingscreen.PlaceholderBlockEffects;
import io.github.bingkkni.noloadingscreen.mixin.EntityAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** NeoForge's explicit no-push query avoids gameplay ticks while retaining local feedback. */
public final class PlayerEnvironment {
	private PlayerEnvironment() {}
	// 1.21 vanilla has no friction/air-drag attributes: its multipliers are fixed at one.
	public static double frictionModifier(LocalPlayer player) { return 1.0; }
	public static double airDragModifier(LocalPlayer player) { return 1.0; }
	public static double waterMovementEfficiency(LocalPlayer player) { return player.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY); }
	public static boolean sampleFluids(Entity subject) {
		EntityAccessor entity = (EntityAccessor) subject;
		boolean previous = entity.nls$wasTouchingWater();
		// NeoForge clears cached heights only on its pushing path. Refresh explicitly while
		// querying without currents, otherwise an adopted entity stays wet after leaving water.
		entity.nls$fluidTypeHeights().clear();
		entity.nls$fluidHeights().clear();
		boolean touching = subject.updateFluidHeightAndDoCanPushEntityFluidPushing(false);
		entity.nls$setTouchingWater(touching);
		entity.nls$updateFluidOnEyes();
		entity.nls$setEyeInWater(subject.isEyeInFluid(FluidTags.WATER));
		if (previous != touching && !subject.isSpectator()) PlaceholderBlockEffects.splash(subject);
		return touching;
	}
}
