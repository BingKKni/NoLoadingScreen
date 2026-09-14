package io.github.bingkkni.noloadingscreen.platform;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.ServerLinks;
import net.minecraft.world.Difficulty;
import net.minecraft.world.clock.ClockNetworkState;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import org.jspecify.annotations.Nullable;

/** API-family construction; placeholder ownership and player initialization stay shared. */
public final class SceneFactory {
	private static final long NOON = 6000L;
	private SceneFactory() {}

	public record Scene(ClientLevel level, ClientPacketListener listener) {}

	public static Scene createLevel(final Minecraft minecraft, final Connection connection,
		final RegistryAccess.Frozen registries, final FeatureFlagSet enabledFeatures, final Holder<DimensionType> type,
		final @Nullable ResourceKey<Level> dimension, final int radius, final int seaLevel) {
		// Built from the local session rather than Minecraft#getGameProfile(), which joins on the
		// profile future — a blocking network call is the last thing a join needs.
		GameProfile profile = new GameProfile(minecraft.getUser().getProfileId(), minecraft.getUser().getName());
		CommonListenerCookie cookie = new CommonListenerCookie(
			new LevelLoadTracker(),
			profile,
			minecraft.getTelemetryManager().createWorldSessionManager(false, null, null, UUID.randomUUID()),
			registries,
			enabledFeatures,
			"noloadingscreen:placeholder",
			null,
			null,
			Map.of(),
			null,
			Map.of(),
			ServerLinks.EMPTY,
			Map.of(),
			true
		);
		ClientPacketListener listener = new ClientPacketListener(minecraft, connection, cookie);

		// ClientLevel's constructor samples environment attributes for sky brightness. Seed the
		// clock before that first cached sample, not after it.
		setNoon(listener, registries);
		ClientLevel.ClientLevelData levelData = new ClientLevel.ClientLevelData(Difficulty.NORMAL, false, false);
		levelData.setGameTime(NOON);
		ClientLevel level = new ClientLevel(
			listener,
			levelData,
			dimension != null ? dimension : Level.OVERWORLD,
			type,
			radius,
			radius,
			minecraft.levelExtractor,
			false,
			0L,
			seaLevel
		);
		return new Scene(level, listener);
	}

	private static void setNoon(final ClientPacketListener listener, final RegistryAccess.Frozen registries) {
		try {
			Holder<WorldClock> clock = registries.lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD);
			listener.clockManager().handleUpdates(NOON, Map.of(clock, new ClockNetworkState(NOON, 0.0F, 1.0F)));
		} catch (RuntimeException e) {
			NoLoadingScreen.LOGGER.debug("Placeholder world keeps the default time of day", e);
		}
	}

}
