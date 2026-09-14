package io.github.bingkkni.noloadingscreen.platform;

import io.github.bingkkni.noloadingscreen.mixin.EntityAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;

/** NeoForge's explicit no-push query avoids splash events and entity/gameplay ticks. */
public final class PlayerEnvironment {
	private PlayerEnvironment() {}
	// 1.21 vanilla has no friction/air-drag attributes: its multipliers are fixed at one.
	public static double frictionModifier(LocalPlayer player) { return 1.0; }
	public static double airDragModifier(LocalPlayer player) { return 1.0; }
	public static void sampleFluids(LocalPlayer player) {
		EntityAccessor entity = (EntityAccessor) player;
		// NeoForge clears cached heights only on its pushing path. Refresh explicitly while
		// querying without currents, otherwise an adopted player stays wet after leaving water.
		entity.nls$fluidTypeHeights().clear();
		entity.nls$fluidHeights().clear();
		entity.nls$setTouchingWater(player.updateFluidHeightAndDoCanPushEntityFluidPushing(false));
		entity.nls$updateFluidOnEyes();
		entity.nls$setEyeInWater(player.isEyeInFluid(FluidTags.WATER));
	}
}
