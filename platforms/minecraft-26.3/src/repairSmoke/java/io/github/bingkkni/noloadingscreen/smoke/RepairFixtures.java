package io.github.bingkkni.noloadingscreen.smoke;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.DeviceInfo;
import java.lang.reflect.Field;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.chunk.LevelChunk;

/** API-family fixture steps for the isolated GPU client. */
public final class RepairFixtures {
	private RepairFixtures() {}

	/**
	 * A 26.x client binds item component maps from the first registry sync it receives. This fixture
	 * never connects, so it runs the same vanilla initializer step over the global registries, with
	 * the private early dynamic registries supplying the referenced data.
	 */
	public static void bindItemComponents(final RegistryAccess.Frozen early) {
		Stream<RegistryAccess.RegistryEntry<?>> builtins = BuiltInRegistries.REGISTRY.stream().map(RepairFixtures::entry);
		Stream<RegistryAccess.RegistryEntry<?>> dynamic = early.registries()
			.filter(registry -> !BuiltInRegistries.REGISTRY.containsKey(registry.key().identifier()));
		HolderLookup.Provider context = new RegistryAccess.ImmutableRegistryAccess(Stream.concat(builtins, dynamic)).freeze();
		BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(context).forEach(DataComponentInitializers.PendingComponents::apply);
	}

	/** 26.3 may run on Vulkan; the device description is backend-neutral. */
	public static String describeGpu() {
		DeviceInfo info = RenderSystem.getDevice().getDeviceInfo();
		return info.vendorName() + " / " + info.name() + " / " + info.backendName() + " " + info.driverInfo();
	}

	/** The packet form of the chunk owns a byte array, so there is no buffer to release. */
	public static void installChunk(final ClientLevel level, final LevelChunk chunk) {
		level.getChunkSource().replaceWithPacketData(chunk.getPos().x(), chunk.getPos().z(), new ClientboundLevelChunkPacketData(chunk));
	}

	/** 26.3 keeps the first-person hand raise height on the player rather than the renderer. */
	public static float mainHandHeight(final Minecraft client, final LocalPlayer player) throws Exception {
		Field field = FirstPersonHandsAndItems.class.getDeclaredField("mainHandHeight");
		field.setAccessible(true);
		return (float) field.get(player.firstPersonHandsAndItems());
	}

	private static <T> RegistryAccess.RegistryEntry<T> entry(final Registry<T> registry) {
		return new RegistryAccess.RegistryEntry<>(registry.key(), registry);
	}
}
