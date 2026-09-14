package io.github.bingkkni.noloadingscreen.platform;

import io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig;
import net.minecraft.client.Minecraft;

/** The 1.21 disconnect hosts keep live pointers longer than the scene-installation boundary. */
public final class DisconnectHandoff {
	private DisconnectHandoff() {}

	/**
	 * Called only for a saving/KickWarn scene, after vanilla captured its registry-reversion
	 * decision and fired logout/unload events. gameMode is already null. Relinquish the other
	 * live fields without detaching meshes; shared adoption still requires a genuinely empty
	 * client binding. Vanilla retains its own final assignments, engine cleanup and registry call.
	 */
	public static void releaseLiveFields(Minecraft client) {
		if (!NoLoadingScreenConfig.get().enabled) return;
		client.level = null;
		client.player = null;
	}
}
