package io.github.bingkkni.noloadingscreen.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.bingkkni.noloadingscreen.LoadingWork;
import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig;
import java.lang.reflect.Method;
import java.util.Map;

/** Retains actual GL programs across the temporary shell's deletion, then transfers ownership once. */
public final class SodiumShaderWarmup {
	private static final String CHUNK = "net.caffeinemc.mods.sodium.client.render.chunk.";
	private static final String DEVICE = "net.caffeinemc.mods.sodium.client.gl.device.";
	private static final String DEFAULT_RENDERER = CHUNK + "DefaultChunkRenderer";
	private static boolean attempted;
	private static boolean preparing;
	private static PendingPrograms pending;
	private static Object pendingDevice;
	private static Object pendingVertex;
	private static long generation;

	private SodiumShaderWarmup() {}

	public static void prepare() {
		if (attempted || !NoLoadingScreenConfig.get().enabled || !SodiumCompatibility.supported()) return;
		RenderSystem.assertOnRenderThread();
		attempted = true;
		preparing = true;
		long start = System.nanoTime();
		try {
			managed(() -> {
				Class<?> deviceType = Class.forName(DEVICE + "RenderDevice");
				Object device = deviceType.getField("INSTANCE").get(null);
				Class<?> vertexType = Class.forName(CHUNK + "vertex.format.ChunkVertexType");
				Object vertex = Class.forName(CHUNK + "vertex.format.ChunkMeshFormats").getField("COMPACT").get(null);
				Class<?> rendererType = Class.forName(DEFAULT_RENDERER);
				Object shell = rendererType.getConstructor(deviceType, vertexType).newInstance(device, vertex);
				try {
					compilePrograms(shell, vertex);
					// Detach before shell.delete: its native index buffers may die; these programs must not.
					Map<Object, Object> source = ((SodiumProgramOwner) shell).nls$programs();
					pending = PendingPrograms.detach(source);
					pendingDevice = device;
					pendingVertex = vertex;
					generation = LoadingWork.resourceGeneration();
				} finally {
					Object commands = deviceType.getMethod("createCommandList").invoke(device);
					try { rendererType.getMethod("delete", Class.forName(DEVICE + "CommandList")).invoke(shell, commands); }
					finally { ((AutoCloseable) commands).close(); }
				}
			});
			NoLoadingScreen.LOGGER.info("Retained Sodium GL terrain programs before first scene in {} ms", (System.nanoTime() - start) / 1_000_000L);
		} catch (Exception | LinkageError failure) {
			clear();
			NoLoadingScreen.LOGGER.warn("Could not warm Sodium GL programs; normal on-demand compilation remains available", failure);
		} finally { preparing = false; }
	}

	static void compilePrograms(Object renderer, Object vertex) throws ReflectiveOperationException {
		Class<?> fogType = Class.forName(CHUNK + "shader.ChunkFogMode");
		Class<?> passType = Class.forName(CHUNK + "terrain.TerrainRenderPass");
		Class<?> vertexType = Class.forName(CHUNK + "vertex.format.ChunkVertexType");
		Class<?> optionsType = Class.forName(CHUNK + "shader.ChunkShaderOptions");
		var options = optionsType.getConstructor(fogType, passType, vertexType);
		Method compile = Class.forName(CHUNK + "ShaderChunkRenderer").getDeclaredMethod("compileProgram", optionsType);
		compile.setAccessible(true);
		Class<?> passes = Class.forName(CHUNK + "terrain.DefaultTerrainRenderPasses");
		for (String pass : new String[]{"SOLID", "CUTOUT", "TRANSLUCENT"}) {
			compile.invoke(renderer, options.newInstance(fogType.getField("SMOOTH").get(null), passes.getField(pass).get(null), vertex));
		}
	}

	/** Called only after the complete default renderer constructor succeeds. Never merge caches. */
	public static void adopt(Object renderer, Object device, Object vertex) {
		if (preparing || pending == null) return;
		RenderSystem.assertOnRenderThread();
		Map<Object, Object> target = ((SodiumProgramOwner) renderer).nls$programs();
		if (!NoLoadingScreenConfig.get().enabled || !renderer.getClass().getName().equals(DEFAULT_RENDERER)
			|| device != pendingDevice || vertex != pendingVertex || generation != LoadingWork.resourceGeneration() || !target.isEmpty()) {
			clear();
			return;
		}
		if (!pending.transferTo(target)) { clear(); return; }
		pending = null;
		pendingDevice = null;
		pendingVertex = null;
	}

	/** Resource invalidation and client shutdown call this while the render context still exists. */
	public static void clear() {
		PendingPrograms owned = pending;
		pending = null;
		pendingDevice = null;
		pendingVertex = null;
		if (owned == null) return;
		try {
			RenderSystem.assertOnRenderThread();
			managed(() -> {
				owned.close(program -> {
					try { program.getClass().getMethod("delete").invoke(program); }
					catch (ReflectiveOperationException failure) { throw new IllegalStateException("Could not delete retained Sodium program", failure); }
				});
			});
		} catch (Exception | LinkageError failure) {
			NoLoadingScreen.LOGGER.warn("Could not release pending Sodium shader warmup programs", failure);
		}
	}

	private static void managed(GlAction action) throws Exception {
		Class<?> device = Class.forName(DEVICE + "RenderDevice");
		device.getMethod("enterManagedCode").invoke(null);
		try { action.run(); } finally { device.getMethod("exitManagedCode").invoke(null); }
	}
	@FunctionalInterface private interface GlAction { void run() throws Exception; }
}
