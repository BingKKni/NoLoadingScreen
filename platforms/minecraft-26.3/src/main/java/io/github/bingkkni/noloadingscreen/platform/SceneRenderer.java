package io.github.bingkkni.noloadingscreen.platform;

import io.github.bingkkni.noloadingscreen.mixin.LevelRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SkyRenderer;
import org.jspecify.annotations.Nullable;

/** API-family engine attachment and camera environment access. */
public final class SceneRenderer {
	private SceneRenderer() {}
	public static void setLevel(Minecraft client, @Nullable ClientLevel level) { client.levelExtractor.setLevel(level); }
	public static void tickCamera(Minecraft client) { client.gameRenderer.mainCamera().tick(); }
	/** 26.3 moved first-person hand raise/lower state from the renderer onto the player. */
	public static void tickHands(Minecraft client, LocalPlayer player) { player.firstPersonHandsAndItems().tick(player); }
	public static void refreshEnvironment(Minecraft client, ClientLevel level) {
		var camera = client.gameRenderer.mainCamera();
		camera.attributeProbe().tick(level, camera.position());
	}
	/** The lazy SkyRenderer must exist before LevelExtractor samples its first skybox. */
	public static void ensureSky(Minecraft client) {
		LevelRendererAccessor renderer = (LevelRendererAccessor) client.levelRenderer;
		if (renderer.nls$skyRenderer() == null) renderer.nls$setSkyRenderer(new SkyRenderer(
			client.getTextureManager(), client.getAtlasManager(), client.gameRenderer.mainRenderTarget()));
	}
}
