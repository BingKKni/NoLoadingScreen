package io.github.bingkkni.noloadingscreen.compat;

/**
 * 26.3 compiles pipelines through {@code PipelineCache} with a resource-backed shader source; the
 * synchronous {@code precompilePipeline} the 26.2 warm-up used is gone. Sodium's private layouts are
 * not whitelisted on this API family either (see the Fabric SodiumCompatibility), so there is
 * nothing to warm here and the normal first-use compilation stays in place.
 */
public final class SodiumShaderWarmup {
	private SodiumShaderWarmup() {}

	public static void prepare() {
	}
}
