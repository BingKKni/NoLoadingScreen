package io.github.bingkkni.noloadingscreen;

import io.github.bingkkni.noloadingscreen.compat.WaveyCapesCompatibility;
import net.minecraft.client.player.LocalPlayer;

/** One login handoff, consumed at the first authoritative position. Never transfers abilities/items. */
public final class CapeContinuity {
	private static CapeState.Snapshot pending;
	private static Object simulation;
	private static float yaw;
	private CapeContinuity() {}

	public static void capture(LocalPlayer player) {
		pending = ((CapeState) player.avatarState()).nls$capture(player.position());
		simulation = WaveyCapesCompatibility.capture(player);
		yaw = player.yBodyRot;
	}

	public static void restore(LocalPlayer player, boolean finalPosition) {
		if (pending == null || player == null) return;
		((CapeState) player.avatarState()).nls$restore(pending, player.position(), player.yBodyRot - yaw);
		WaveyCapesCompatibility.restore(player, simulation);
		if (finalPosition) clear();
	}

	public static void clear() { pending = null; simulation = null; }
}
