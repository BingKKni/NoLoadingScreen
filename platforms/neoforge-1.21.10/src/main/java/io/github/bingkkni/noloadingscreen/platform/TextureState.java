package io.github.bingkkni.noloadingscreen.platform;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;

/** 1.21.10 still stores texture transforms in global RenderSystem state. */
public final class TextureState {
	private TextureState() {}
	public static Runnable rollback() {
		Matrix4f texture = new Matrix4f(RenderSystem.getTextureMatrix());
		return () -> RenderSystem.setTextureMatrix(texture);
	}
}
