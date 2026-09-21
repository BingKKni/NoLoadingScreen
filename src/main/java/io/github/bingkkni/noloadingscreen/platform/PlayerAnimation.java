package io.github.bingkkni.noloadingscreen.platform;

import io.github.bingkkni.noloadingscreen.mixin.LivingEntityAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

/** Arm swing bookkeeping for the disposable player; animation state only, never a packet. */
public final class PlayerAnimation {
	private PlayerAnimation() {}

	/** {@code LivingEntity.tick}'s swing progress step. */
	public static void tickSwing(final LocalPlayer player) {
		player.oAttackAnim = player.attackAnim;
		((LivingEntityAccessor) player).nls$updateSwingTime();
	}

	/** The condition {@code LivingEntity.tick} uses to point the body at the view while swinging. */
	public static boolean swinging(final LocalPlayer player) {
		return player.attackAnim > 0.0F;
	}

	// The one-argument LocalPlayer.swing sends a packet; LivingEntity's overloads only update
	// animation state on a ClientLevel.
	public static void swingAttack(final LocalPlayer player) {
		player.swing(InteractionHand.MAIN_HAND, false);
	}

	public static void swingUse(final LocalPlayer player, final InteractionHand hand) {
		player.swing(hand, false);
	}
}
