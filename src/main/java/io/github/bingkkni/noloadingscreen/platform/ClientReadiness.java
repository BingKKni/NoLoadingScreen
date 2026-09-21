package io.github.bingkkni.noloadingscreen.platform;

import net.minecraft.client.player.LocalPlayer;

/** Whether vanilla has sent ServerboundPlayerLoadedPacket and therefore ticks the player for real. */
public final class ClientReadiness {
	private ClientReadiness() {}
	public static boolean loaded(final LocalPlayer player) { return player.connection.hasClientLoaded(); }
}
