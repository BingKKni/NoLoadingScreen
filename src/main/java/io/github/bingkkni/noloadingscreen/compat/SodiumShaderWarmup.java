package io.github.bingkkni.noloadingscreen.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/** Compile the actual Sodium pipeline objects before quick play/title-screen world entry. */
public final class SodiumShaderWarmup {
	private static final String CHUNK = "net.caffeinemc.mods.sodium.client.render.chunk.";
	private static boolean attempted;

	private SodiumShaderWarmup() {}

	public static void prepare() {
		if (attempted || !NoLoadingScreenConfig.get().enabled || !SodiumCompatibility.supported()) return;
		attempted = true;
		long start = System.nanoTime();
		try {
			Class<?> vertexType = Class.forName(CHUNK + "vertex.format.ChunkVertexType");
			Object format = Class.forName(CHUNK + "vertex.format.ChunkMeshFormats").getMethod("getCurrent").invoke(null);
			Class<?> rendererType = Class.forName(CHUNK + "DefaultChunkRenderer");
			Object renderer = rendererType.getConstructor(vertexType).newInstance(format);
			try {
				compilePipelines(renderer, pipeline -> {
					if (!RenderSystem.getDevice().precompilePipeline(pipeline).isValid()) {
						throw new IllegalStateException("Invalid Sodium terrain pipeline: " + pipeline.getLocation());
					}
				});
			} finally {
				rendererType.getMethod("delete").invoke(renderer);
			}
			NoLoadingScreen.LOGGER.info("Prepared Sodium terrain pipelines before first world entry in {} ms", (System.nanoTime() - start) / 1_000_000L);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
			NoLoadingScreen.LOGGER.warn("Could not warm Sodium terrain pipelines; normal first-use compilation remains available", failure);
		}
	}

	/** Reuse Sodium's cache and shader defines; never duplicate GLSL or invent pipeline variants. */
	static void compilePipelines(final Object renderer, final Consumer<RenderPipeline> compiler) throws ReflectiveOperationException {
		Class<?> passType = Class.forName(CHUNK + "terrain.TerrainRenderPass");
		Method compile = Class.forName(CHUNK + "ShaderChunkRenderer").getDeclaredMethod("compileProgram", passType);
		compile.setAccessible(true);
		Class<?> passes = Class.forName(CHUNK + "terrain.DefaultTerrainRenderPasses");
		for (String name : new String[]{"SOLID", "CUTOUT", "TRANSLUCENT"}) {
			compiler.accept((RenderPipeline) compile.invoke(renderer, passes.getField(name).get(null)));
		}
	}
}
