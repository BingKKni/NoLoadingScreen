package io.github.bingkkni.noloadingscreen.platform;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.stats.StatsCounter;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.ServerLinks;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.jspecify.annotations.Nullable;

/** Mapped 1.21 client construction, with a non-networked, non-modded listener. */
public final class SceneFactory {
	private SceneFactory() {}
	public record Scene(ClientLevel level, ClientPacketListener listener) {}

	public static Scene createLevel(final Minecraft minecraft, final Connection connection,
		final RegistryAccess.Frozen registries, final FeatureFlagSet enabledFeatures, final Holder<DimensionType> type,
		final @Nullable ResourceKey<Level> dimension, final int radius, final int seaLevel) {
		GameProfile profile = new GameProfile(minecraft.getUser().getProfileId(), minecraft.getUser().getName());
		CommonListenerCookie cookie = new CommonListenerCookie(new LevelLoadTracker(), profile,
			minecraft.getTelemetryManager().createWorldSessionManager(false, null, null), registries, enabledFeatures,
			"noloadingscreen:placeholder", null, null, Map.of(), null, Map.of(), ServerLinks.EMPTY, Map.of(), true,
			ConnectionType.OTHER);
		ClientPacketListener listener = new ClientPacketListener(minecraft, connection, cookie);
		// Seed both time fields before ClientLevel samples sky brightness during construction.
		ClientLevel.ClientLevelData data = new ClientLevel.ClientLevelData(Difficulty.NORMAL, false, false);
		data.setGameTime(6000L);
		data.setDayTime(6000L);
		ClientLevel level = new ClientLevel(listener, data, dimension != null ? dimension : Level.OVERWORLD,
			type, radius, radius, minecraft.levelRenderer, false, 0L, seaLevel);
		return new Scene(level, listener);
	}

	public static LocalPlayer createPlayer(final MultiPlayerGameMode gameMode, final ClientLevel level) {
		return gameMode.createPlayer(level, new StatsCounter(), new ClientRecipeBook());
	}
}
