package io.github.bingkkni.noloadingscreen.platform;

import io.github.bingkkni.noloadingscreen.mixin.LivingEntityAccessor;
import io.github.bingkkni.noloadingscreen.mixin.SwingStateAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

/**
 * 26.3 replaced the attackAnim/oAttackAnim pair with {@code LivingEntity.SwingState}, and every
 * swing carries the item's {@code SwingAnimation} component. Still animation state only: the
 * client-side {@code LivingEntity.swing} sends nothing, and {@code LocalPlayer}'s packet-sending
 * overload no longer exists.
 */
public final class PlayerAnimation {
	private PlayerAnimation() {}

	/** {@code LivingEntity.baseTick}'s swing progress step. */
	public static void tickSwing(final LocalPlayer player) {
		((LivingEntityAccessor) player).nls$swingState().tick();
	}

	/** The condition {@code LivingEntity.tick} uses to point the body at the view while swinging. */
	public static boolean swinging(final LocalPlayer player) {
		return ((SwingStateAccessor) ((LivingEntityAccessor) player).nls$swingState()).nls$animation() > 0.0F;
	}

	public static void swingAttack(final LocalPlayer player) {
		player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getAttackAnimation(), false);
	}

	public static void swingUse(final LocalPlayer player, final InteractionHand hand) {
		player.swing(hand, player.getItemInHand(hand).getInteractAnimation(), false);
	}
}
