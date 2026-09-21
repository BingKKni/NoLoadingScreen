package io.github.bingkkni.noloadingscreen.compat;

/**
 * Sodium builds for this API family still compile their terrain shaders as GL programs on first
 * use ({@code ShaderChunkRenderer.compileProgram(ChunkShaderOptions)}); the {@code RenderPipeline}
 * precompilation the 26.2 warm-up drives does not exist there. The scheduling hooks stay enabled;
 * only this warm-up is a no-op.
 */
public final class SodiumShaderWarmup {
	private SodiumShaderWarmup() {}

	public static void prepare() {
	}
}
