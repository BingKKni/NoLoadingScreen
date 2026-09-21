package io.github.bingkkni.noloadingscreen.platform;

import net.minecraft.client.player.LocalPlayer;

/** 1.21.10 keeps the loaded flag and its timeout on the player itself, not on the listener. */
public final class ClientReadiness {
	private ClientReadiness() {}
	public static boolean loaded(final LocalPlayer player) { return player.hasClientLoaded(); }
}
