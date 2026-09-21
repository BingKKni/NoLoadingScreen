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
	public static void setLevel(Minecraft client, @Nullable ClientLevel level) { client.levelRenderer.setLevel(level); }
	public static void tickCamera(Minecraft client) { client.gameRenderer.getMainCamera().tick(); }
	public static void tickHands(Minecraft client, LocalPlayer player) { ((GameRendererAccessor) client.gameRenderer).nls$hands().tick(); }
	public static void refreshEnvironment(Minecraft client, ClientLevel level) {
		var camera = client.gameRenderer.getMainCamera();
		camera.attributeProbe().tick(level, camera.position());
	}
	/** Initialize the sky before LevelRenderer extracts the first placeholder frame. */
	public static void ensureSky(Minecraft client) {
		LevelRendererAccessor renderer = (LevelRendererAccessor) client.levelRenderer;
		if (renderer.nls$skyRenderer() == null) renderer.nls$setSkyRenderer(new SkyRenderer(
			client.getTextureManager(), client.getAtlasManager()));
	}
}
