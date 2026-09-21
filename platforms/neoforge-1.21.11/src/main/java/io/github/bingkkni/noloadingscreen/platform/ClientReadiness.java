package io.github.bingkkni.noloadingscreen.platform;

import net.minecraft.client.player.LocalPlayer;

/** 1.21.11 moved the loaded flag onto the play listener, as 26.x keeps it. */
public final class ClientReadiness {
	private ClientReadiness() {}
	public static boolean loaded(final LocalPlayer player) { return player.connection.hasClientLoaded(); }
}
