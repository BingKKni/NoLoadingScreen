package io.github.bingkkni.noloadingscreen.verification;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import io.github.bingkkni.noloadingscreen.LoadingWork;
import io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import io.github.bingkkni.noloadingscreen.compat.SodiumShaderWarmup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sun.misc.Unsafe;

/** Actual Sodium pipeline definitions and transformed lifecycle hooks, without allocating GPU state. */
final class SodiumColdStartVerification {
	private static int assertions;

	static void run() throws ReflectiveOperationException {
		verifyPipelines();
		verifyFreshRenderer();
		verifyVoidWorkers();
		System.out.println("SodiumColdStartVerification: " + assertions + " assertions passed (shared terrain pipelines, first-reload coalescing, invalidation and worker isolation).");
	}

	private static void verifyPipelines() throws ReflectiveOperationException {
		String prefix = "net.caffeinemc.mods.sodium.client.render.chunk.";
		Class<?> base = Class.forName(prefix + "ShaderChunkRenderer");
		Object renderer = unsafe().allocateInstance(Class.forName(prefix + "DefaultChunkRenderer"));
		Object format = Class.forName(prefix + "vertex.format.ChunkMeshFormats").getMethod("getCurrent").invoke(null);
		Object vertexFormat = Class.forName(prefix + "vertex.format.ChunkVertexType").getMethod("getVertexFormat").invoke(format);
		set(base, renderer, "vertexFormat", vertexFormat);
		Method warm = SodiumShaderWarmup.class.getDeclaredMethod("compilePipelines", Object.class, Consumer.class);
		warm.setAccessible(true);
		List<RenderPipeline> compiled = new ArrayList<>();
		warm.invoke(null, renderer, (Consumer<RenderPipeline>) compiled::add);
		check(compiled.size() == 3 && compiled.stream().distinct().count() == 3, "Warm exactly the three actual terrain pass variants");
		check(compiled.stream().allMatch(p -> p.getLocation().getNamespace().equals("sodium")), "Do not replace vanilla or unrelated shader pipelines");
		List<RenderPipeline> reused = new ArrayList<>();
		warm.invoke(null, renderer, (Consumer<RenderPipeline>) reused::add);
		for (int i = 0; i < 3; i++) check(compiled.get(i) == reused.get(i), "Later terrain rendering uses the exact precompiled pipeline objects");
		try {
			warm.invoke(null, renderer, (Consumer<RenderPipeline>) pipeline -> { throw new IllegalStateException("compiler failure"); });
			throw new AssertionError("Expected failure");
		} catch (java.lang.reflect.InvocationTargetException expected) {
			check(expected.getCause() instanceof IllegalStateException, "Warm-up failure propagates to the startup fallback boundary");
		}
	}

	private static void verifyFreshRenderer() throws ReflectiveOperationException {
		Object oldClient = get(Minecraft.class, null, "instance");
		Minecraft minecraft = (Minecraft) unsafe().allocateInstance(Minecraft.class);
		Options options = (Options) unsafe().allocateInstance(Options.class);
		set(Minecraft.class, null, "instance", minecraft);
		set(Minecraft.class, minecraft, "options", options);
		set(Options.class, options, "renderDistance", new OptionInstance<Integer>("test", OptionInstance.noTooltip(),
			(caption, value) -> caption, new OptionInstance.IntRange(2, 32), 16, value -> {}));
		// Pre-launch verification deliberately has no initialized ViaFabricPlus API. Seed the vanilla
		// backing field directly instead of firing third-party option callbacks unrelated to this test.
		set(Options.class, options, "serverRenderDistance", 32);
		Class<?> type = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
		Object renderer = type.getConstructor(Minecraft.class).newInstance(minecraft);
		set(type, renderer, "renderDistance", 16);
		Method fresh = hook(type, "nls$rememberFreshRenderer");
		Method reload = hook(type, "nls$coalesceFirstReload");
		Method used = hook(type, "nls$rendererNoLongerFresh");
		try {
			fresh.invoke(renderer, new CallbackInfo("loadLevel", false));
			check(cancelled(reload, renderer), "First unused same-resource/same-distance renderer is not immediately rebuilt");
			check(!cancelled(reload, renderer), "Only one redundant reload can be coalesced");
			fresh.invoke(renderer, new CallbackInfo("loadLevel", false));
			LoadingWork.resourcesReloaded();
			check(!cancelled(reload, renderer), "Resource reload between world installation and first frame invalidates the optimization");
			fresh.invoke(renderer, new CallbackInfo("loadLevel", false));
			set(type, renderer, "renderDistance", 12);
			check(!cancelled(reload, renderer), "Server/user view-distance change still rebuilds correctly");
			set(type, renderer, "renderDistance", 16);
			fresh.invoke(renderer, new CallbackInfo("loadLevel", false));
			used.invoke(renderer, new CallbackInfo("setupTerrain", false));
			check(!cancelled(reload, renderer), "Used, edited or unloaded renderers cannot skip a later rebuild");
			fresh.invoke(renderer, new CallbackInfo("loadLevel", false));
			NoLoadingScreenConfig.get().enabled = false;
			check(!cancelled(reload, renderer), "Disabling the mod restores the original reload");
		} finally {
			NoLoadingScreenConfig.get().enabled = true;
			set(Minecraft.class, null, "instance", oldClient);
		}
	}

	private static void verifyVoidWorkers() throws ReflectiveOperationException {
		Class<?> type = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder");
		Method workerCount = Arrays.stream(type.getDeclaredMethods()).filter(m -> m.getName().contains("nls$smallVoidWorkerPool"))
			.findFirst().orElseThrow();
		workerCount.setAccessible(true);
		try {
			check((int) workerCount.invoke(null, 10) == 10, "Normal Sodium worker selection unchanged");
			set(PlaceholderWorld.class, null, "installed", true);
			set(PlaceholderWorld.class, null, "synthetic", true);
			check((int) workerCount.invoke(null, 10) == 1, "Empty synthetic world creates only one worker context");
			set(PlaceholderWorld.class, null, "synthetic", false);
			check((int) workerCount.invoke(null, 10) == 10, "Adopted worlds preserve their full worker pool");
			set(PlaceholderWorld.class, null, "synthetic", true);
			NoLoadingScreenConfig.get().enabled = false;
			check((int) workerCount.invoke(null, 10) == 10, "Disabled mod leaves worker settings intact");
		} finally {
			NoLoadingScreenConfig.get().enabled = true;
			set(PlaceholderWorld.class, null, "installed", false);
			set(PlaceholderWorld.class, null, "synthetic", false);
		}
	}

	private static boolean cancelled(final Method hook, final Object renderer) throws ReflectiveOperationException {
		CallbackInfo callback = new CallbackInfo("reload", true);
		hook.invoke(renderer, callback);
		return callback.isCancelled();
	}

	private static Method hook(final Class<?> type, final String fragment) {
		Method method = Arrays.stream(type.getDeclaredMethods()).filter(m -> m.getName().contains(fragment)
			&& Arrays.equals(m.getParameterTypes(), new Class<?>[]{CallbackInfo.class})).findFirst().orElseThrow();
		method.setAccessible(true);
		return method;
	}

	private static Unsafe unsafe() throws ReflectiveOperationException {
		Field field = Unsafe.class.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		return (Unsafe) field.get(null);
	}

	private static void set(final Class<?> type, final Object instance, final String name, final Object value) throws ReflectiveOperationException {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		field.set(instance, value);
	}

	private static Object get(final Class<?> type, final Object instance, final String name) throws ReflectiveOperationException {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(instance);
	}

	private static void check(final boolean condition, final String message) {
		assertions++;
		if (!condition) throw new AssertionError(message);
	}
}
