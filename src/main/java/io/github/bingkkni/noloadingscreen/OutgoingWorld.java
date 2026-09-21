package io.github.bingkkni.noloadingscreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

/** The same disposable scene handoff is used by saving, proxy reconfiguration and KickWarn. */
record OutgoingWorld(ClientLevel level, LocalPlayer player, @Nullable MultiPlayerGameMode gameMode,
	@Nullable PlaceholderPermissions permissions) {
	OutgoingWorld(ClientLevel level, LocalPlayer player, @Nullable MultiPlayerGameMode gameMode) {
		this(level, player, gameMode, null);
	}
	static @Nullable OutgoingWorld capture() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level != null && minecraft.player != null && !minecraft.player.isDeadOrDying()
			? new OutgoingWorld(minecraft.level, minecraft.player, minecraft.gameMode,
				PlaceholderPermissions.capture(minecraft.player, minecraft.gameMode)) : null;
	}

	boolean install(final boolean dismissScreen) {
		return permissions == null ? PlaceholderWorld.adopt(this.level, this.player, this.gameMode, dismissScreen)
			: PlaceholderWorld.adopt(this.level, this.player, this.gameMode, dismissScreen, permissions);
	}
}
