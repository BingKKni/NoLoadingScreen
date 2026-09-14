package io.github.bingkkni.noloadingscreen.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jspecify.annotations.Nullable;

/** 1.21.10 owns meshes and extraction in LevelRenderer; sky/fog query the level directly. */
public final class SceneRenderer {
	private SceneRenderer() {}
	public static void setLevel(Minecraft client, @Nullable ClientLevel level) { client.levelRenderer.setLevel(level); }
	public static void tickCamera(Minecraft client) {
		((io.github.bingkkni.noloadingscreen.mixin.GameRendererAccessor) client.gameRenderer).nls$tickFov();
		client.gameRenderer.getMainCamera().tick();
	}
	public static void refreshEnvironment(Minecraft client, ClientLevel level) {
		// No EnvironmentAttributeProbe cache in this API: LevelRenderer samples the level per frame.
	}
	public static void ensureSky(Minecraft client) {
		// SkyRenderer is an eagerly constructed final field, initialized by the resource reload.
	}
}
