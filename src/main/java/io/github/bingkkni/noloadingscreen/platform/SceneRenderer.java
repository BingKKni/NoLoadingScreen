package io.github.bingkkni.noloadingscreen.platform;

import io.github.bingkkni.noloadingscreen.mixin.GameRendererAccessor;
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
	/** First-person hand raise/lower state lives on the renderer in this API family. */
	public static void tickHands(Minecraft client, LocalPlayer player) { ((GameRendererAccessor) client.gameRenderer).nls$hands().tick(); }
	public static void refreshEnvironment(Minecraft client, ClientLevel level) {
		var camera = client.gameRenderer.mainCamera();
		camera.attributeProbe().tick(level, camera.position());
	}
	/** Lazy 26.2 SkyRenderer must exist before LevelExtractor samples its first skybox. */
	public static void ensureSky(Minecraft client) {
		LevelRendererAccessor renderer = (LevelRendererAccessor) client.levelRenderer;
		if (renderer.nls$skyRenderer() == null) renderer.nls$setSkyRenderer(new SkyRenderer(
			client.getTextureManager(), client.getAtlasManager(), client.gameRenderer.mainRenderTarget()));
	}
}
