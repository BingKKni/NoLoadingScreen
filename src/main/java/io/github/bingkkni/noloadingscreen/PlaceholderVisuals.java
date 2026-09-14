package io.github.bingkkni.noloadingscreen;

import io.github.bingkkni.noloadingscreen.mixin.LivingEntityAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Visual-only pieces of the vanilla player tick. Never calls tick, aiStep, move or sends packets. */
public final class PlaceholderVisuals {
	private PlaceholderVisuals() {}

	/** Deltas are collision-resolved simulation movement, never per-frame interpolated positions. */
	public static void tick(final LocalPlayer player, final double dx, final double dy, final double dz) {
		Vec3 motion = new Vec3(dx, dy, dz);
		float distance = (float) motion.horizontalDistance();
		player.setDeltaMovement(motion);
		player.tickCount++; // model idle animation age, not an entity/gameplay tick
		player.avatarState().tick(player.position(), motion);
		player.avatarState().addWalkDistance(distance * 0.6F);
		player.avatarState().updateBob(player.onGround() && !player.isSwimming() ? Math.min(0.1F, distance) : 0.0F);
		player.walkAnimation.update(Math.min(distance * 4.0F, 1.0F), 0.4F, 1.0F);

		LivingEntityAccessor visuals = (LivingEntityAccessor) player;
		visuals.nls$setItemSwapTicker(visuals.nls$itemSwapTicker() + 1);
		player.oAttackAnim = player.attackAnim;
		visuals.nls$updateSwingTime(); // finish a captured swing rather than hold its pose forever
		if (player.hurtTime > 0) player.hurtTime--;
		player.yBodyRotO = player.yBodyRot;
		player.yHeadRotO = player.yHeadRot;
		player.yHeadRot = player.getYRot();
		float bodyTarget = player.yBodyRot;
		if (dx * dx + dz * dz > 0.0025000002F) {
			float direction = (float) (Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
			float facingDifference = Math.abs(Mth.wrapDegrees(player.getYRot() - direction));
			bodyTarget = facingDifference > 95.0F ? direction - 180.0F : direction;
		}
		if (player.attackAnim > 0.0F) bodyTarget = player.getYRot();
		visuals.nls$tickHeadTurn(bodyTarget);
		player.yBodyRotO = player.yBodyRot - Mth.wrapDegrees(player.yBodyRot - player.yBodyRotO);
		player.yHeadRotO = player.yHeadRot - Mth.wrapDegrees(player.yHeadRot - player.yHeadRotO);
		player.elytraAnimationState.tick();
	}
}
