package io.github.bingkkni.noloadingscreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

/** Immutable pre-teardown mode/ability snapshot: game mode alone cannot reconstruct server policy. */
record PlaceholderPermissions(GameType mode, boolean mayfly, boolean flying, boolean instabuild,
	boolean mayBuild, boolean invulnerable, float walkingSpeed, float flyingSpeed) {
	static PlaceholderPermissions capture(LocalPlayer player, @Nullable MultiPlayerGameMode controller) {
		GameType mode = controller != null ? controller.getPlayerMode() : GameType.SURVIVAL;
		if (controller == null) {
			var info = player.connection.getPlayerInfo(player.getUUID());
			if (info != null) mode = info.getGameMode();
		}
		var abilities = player.getAbilities();
		return new PlaceholderPermissions(mode, abilities.mayfly, abilities.flying, abilities.instabuild,
			abilities.mayBuild, abilities.invulnerable, abilities.getWalkingSpeed(), abilities.getFlyingSpeed());
	}

	void apply(LocalPlayer player) {
		Minecraft.getInstance().gameMode.setLocalMode(mode);
		var abilities = player.getAbilities();
		abilities.mayfly = mayfly; abilities.flying = flying; abilities.instabuild = instabuild;
		abilities.mayBuild = mayBuild; abilities.invulnerable = invulnerable;
		abilities.setWalkingSpeed(walkingSpeed); abilities.setFlyingSpeed(flyingSpeed);
	}
}
