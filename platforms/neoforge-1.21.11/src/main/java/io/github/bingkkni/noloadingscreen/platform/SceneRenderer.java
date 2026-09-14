package io.github.bingkkni.noloadingscreen.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jspecify.annotations.Nullable;

/** 1.21.11 mesh/extraction ownership and camera environment cache. */
public final class SceneRenderer {
	private SceneRenderer() {}
	public static void setLevel(Minecraft client, @Nullable ClientLevel level) { client.levelRenderer.setLevel(level); }
	public static void tickCamera(Minecraft client) {
		((io.github.bingkkni.noloadingscreen.mixin.GameRendererAccessor) client.gameRenderer).nls$tickFov();
		client.gameRenderer.getMainCamera().tick();
	}
	public static void refreshEnvironment(Minecraft client, ClientLevel level) {
		var camera = client.gameRenderer.getMainCamera();
		camera.attributeProbe().tick(level, camera.position());
	}
	public static void ensureSky(Minecraft client) {
		// Unlike 26.2, onResourceManagerReload creates SkyRenderer before any frame extraction.
	}
}
