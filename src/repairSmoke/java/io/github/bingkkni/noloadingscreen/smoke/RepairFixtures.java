package io.github.bingkkni.noloadingscreen.smoke;

import io.github.bingkkni.noloadingscreen.mixin.GameRendererAccessor;
import java.lang.reflect.Field;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import org.lwjgl.opengl.GL11;

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

	/** This API family always renders through OpenGL. */
	public static String describeGpu() {
		return GL11.glGetString(GL11.GL_VENDOR) + " / " + GL11.glGetString(GL11.GL_RENDERER) + " / " + GL11.glGetString(GL11.GL_VERSION);
	}

	/** The main-hand raise height the first-person renderer interpolates; the state lives on the renderer here. */
	/** The same packet path a real chunk arrives through, with the buffer owned and released here. */
	public static void installChunk(final ClientLevel level, final LevelChunk chunk) {
		ClientboundLevelChunkPacketData packet = new ClientboundLevelChunkPacketData(chunk);
		var buffer = packet.getReadBuffer();
		int x = chunk.getPos().x(), z = chunk.getPos().z();
		try { level.getChunkSource().replaceWithPacketData(x, z, buffer, packet.getHeightmaps(), packet.getBlockEntitiesTagsConsumer(x, z)); }
		finally { buffer.release(); }
	}

	public static float mainHandHeight(final Minecraft client, final LocalPlayer player) throws Exception {
		ItemInHandRenderer hands = ((GameRendererAccessor) client.gameRenderer).nls$hands();
		Field field = ItemInHandRenderer.class.getDeclaredField("mainHandHeight");
		field.setAccessible(true);
		return (float) field.get(hands);
	}

	private static <T> RegistryAccess.RegistryEntry<T> entry(final Registry<T> registry) {
		return new RegistryAccess.RegistryEntry<>(registry.key(), registry);
	}
}
