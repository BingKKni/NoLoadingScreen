package io.github.bingkkni.noloadingscreen.smoke;

import io.github.bingkkni.noloadingscreen.mixin.GameRendererAccessor;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.chunk.LevelChunk;
import org.lwjgl.opengl.GL11;

/** API-family fixture steps for the isolated GPU client. */
public final class RepairFixtures {
	private RepairFixtures() {}

	/** Mapped 1.21 items carry their component maps from registration; nothing to bind. */
	public static void bindItemComponents(final RegistryAccess.Frozen early) {
	}

	public static String describeGpu() {
		return GL11.glGetString(GL11.GL_VENDOR) + " / " + GL11.glGetString(GL11.GL_RENDERER) + " / " + GL11.glGetString(GL11.GL_VERSION);
	}

	/** The same packet path a real chunk arrives through, with the buffer owned and released here. */
	public static void installChunk(final ClientLevel level, final LevelChunk chunk) {
		ClientboundLevelChunkPacketData packet = new ClientboundLevelChunkPacketData(chunk);
		var buffer = packet.getReadBuffer();
		int x = chunk.getPos().x, z = chunk.getPos().z;
		try { level.getChunkSource().replaceWithPacketData(x, z, buffer, packet.getHeightmaps(), packet.getBlockEntitiesTagsConsumer(x, z)); }
		finally { buffer.release(); }
	}

	public static float mainHandHeight(final Minecraft client, final LocalPlayer player) throws Exception {
		ItemInHandRenderer hands = ((GameRendererAccessor) client.gameRenderer).nls$hands();
		Field field = ItemInHandRenderer.class.getDeclaredField("mainHandHeight");
		field.setAccessible(true);
		return (float) field.get(hands);
	}
}
