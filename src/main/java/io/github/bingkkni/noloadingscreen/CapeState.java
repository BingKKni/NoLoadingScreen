package io.github.bingkkni.noloadingscreen;

import net.minecraft.world.phys.Vec3;

/** Visual state only. Positions are relative to the player, never server coordinates. */
public interface CapeState {
	record Snapshot(Vec3 current, Vec3 previous, float walk, float oldWalk, float bob, float oldBob) {}
	Snapshot nls$capture(Vec3 anchor);
	void nls$restore(Snapshot state, Vec3 anchor, float yawDelta);
}
