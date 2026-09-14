package io.github.bingkkni.noloadingscreen.platform;

/** 1.21.11 removed RenderSystem's global texture matrix; transforms are per-draw uniforms. */
public final class TextureState {
	private static final Runnable NO_GLOBAL_MATRIX = () -> {};
	private TextureState() {}
	public static Runnable rollback() { return NO_GLOBAL_MATRIX; }
}
