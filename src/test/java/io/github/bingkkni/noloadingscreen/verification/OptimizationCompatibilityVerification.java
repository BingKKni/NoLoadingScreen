package io.github.bingkkni.noloadingscreen.verification;

import io.github.bingkkni.noloadingscreen.compat.PendingChunkBuild;
import io.github.bingkkni.noloadingscreen.compat.SodiumCompatibility;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/** Loads shared targets with supplied optimization-mod jars and checks NoLoadingScreen hooks survive transformation. */
final class OptimizationCompatibilityVerification {
	private static final Map<String, Map<String, List<String>>> SHARED_TARGETS = sharedTargets();

	private OptimizationCompatibilityVerification() {}

	static void run() throws ReflectiveOperationException {
		FabricLoader loader = FabricLoader.getInstance();
		int recognized = 0;
		for (var entry : SHARED_TARGETS.entrySet()) {
			var container = loader.getModContainer(entry.getKey());
			if (container.isEmpty()) continue;
			recognized++;
			for (var target : entry.getValue().entrySet()) {
				Class<?> type = Class.forName(target.getKey(), false, OptimizationCompatibilityVerification.class.getClassLoader());
				for (String hook : target.getValue()) {
					check(hasMethod(type, hook), "NoLoadingScreen hook was removed or conflicted while " + entry.getKey()
						+ " was loaded: " + target.getKey() + "#" + hook);
				}
			}
			System.out.println("Verified NoLoadingScreen target transformation with optimization mod loaded: " + entry.getKey() + " "
				+ container.orElseThrow().getMetadata().getVersion().getFriendlyString());
		}
		check(recognized > 0, "No recognized optimization mod was supplied to the compatibility run");
		if (loader.isModLoaded("sodium")) verifySodiumPrivateHooks();
		System.out.println("NoLoadingScreen target transformation checks passed for " + recognized + " recognized mod(s); this is not a GPU or gameplay verdict.");
	}

	private static void verifySodiumPrivateHooks() throws ReflectiveOperationException {
		if (!SodiumCompatibility.supported()) {
			System.out.println("Sodium is present but its private NoLoadingScreen optimizations are safely disabled for this unverified version.");
			return;
		}
		Map<String, List<String>> targets = Map.of(
			"net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer",
				List.of("nls$measureRendererInitialization", "nls$rememberFreshRenderer", "nls$coalesceFirstReload"),
			"net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager",
				List.of("nls$measureRendererTeardown", "nls$leaveLoadingBuildsOnWorkers"),
			"net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder",
				List.of("nls$smallVoidWorkerPool"),
			"net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobCollector",
				List.of("nls$pending")
		);
		for (var target : targets.entrySet()) {
			Class<?> type = Class.forName(target.getKey(), false, OptimizationCompatibilityVerification.class.getClassLoader());
			for (String hook : target.getValue()) check(hasMethod(type, hook), "Supported Sodium layout is missing " + target.getKey() + "#" + hook);
		}
		Class<?> collector = Class.forName(
			"net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobCollector", false,
			OptimizationCompatibilityVerification.class.getClassLoader());
		check(PendingChunkBuild.class.isAssignableFrom(collector), "Supported Sodium collector lost the non-blocking bridge");
	}

	private static boolean hasMethod(final Class<?> type, final String fragment) {
		return Arrays.stream(type.getDeclaredMethods()).map(Method::getName).anyMatch(name -> name.contains(fragment));
	}

	private static Map<String, Map<String, List<String>>> sharedTargets() {
		Map<String, Map<String, List<String>>> result = new LinkedHashMap<>();
		result.put("sodium", Map.of(
			"net.minecraft.client.Minecraft", List.of("nls$renderFrame"),
			"net.minecraft.client.renderer.GameRenderer", List.of("nls$preparePlaceholderCamera"),
			"net.minecraft.client.renderer.LevelRenderer", List.of("nls$compileLoadingTerrainAsync"),
			"net.minecraft.client.renderer.extract.LevelExtractor", List.of("nls$showLocalPlayerWithoutTerrain")
		));
		result.put("iris", Map.of(
			"net.minecraft.client.Minecraft", List.of("nls$renderFrame"),
			"net.minecraft.client.gui.Gui", List.of("nls$returnToPlaceholder"),
			"net.minecraft.client.gui.Hud", List.of("nls$drawLoadingOverlay"),
			"net.minecraft.client.multiplayer.ClientPacketListener", List.of("nls$configurationStarted"),
			"net.minecraft.client.renderer.GameRenderer", List.of("nls$freezePlaceholderScene"),
			"net.minecraft.client.renderer.LevelRenderer", List.of("nls$compileLoadingTerrainAsync")
		));
		result.put("immediatelyfast", Map.of(
			"net.minecraft.client.Minecraft", List.of("nls$renderFrame"),
			"net.minecraft.client.gui.Gui", List.of("nls$tickPlaceholderHud"),
			"net.minecraft.client.gui.Hud", List.of("nls$drawLoadingOverlay")
		));
		result.put("lithium", Map.of(
			"net.minecraft.client.multiplayer.ClientPacketListener", List.of("nls$configurationStarted")
		));
		result.put("moreculling", Map.of(
			"net.minecraft.client.Minecraft", List.of("nls$renderFrame"),
			"net.minecraft.client.renderer.extract.LevelExtractor", List.of("nls$showLocalPlayerWithoutTerrain")
		));
		result.put("entityculling", Map.of(
			"net.minecraft.client.renderer.entity.EntityRenderDispatcher", List.of("nls$splitEntityClock")
		));
		result.put("dynamic_fps", Map.of(
			"net.minecraft.client.Minecraft", List.of("nls$renderFrame")
		));
		result.put("ferritecore", Map.of(
			"net.minecraft.client.Minecraft", List.of("nls$renderFrame")
		));
		result.put("sodium-extra", Map.of(
			"net.minecraft.client.renderer.GameRenderer", List.of("nls$preparePlaceholderCamera")
		));
		result.put("badoptimizations", Map.of(
			"net.minecraft.client.particle.ParticleEngine", List.of("nls$captureLocalDebris"),
			"net.minecraft.client.renderer.extract.LevelExtractor", List.of("nls$extractLocalDebris"),
			"net.minecraft.client.renderer.entity.EntityRenderDispatcher", List.of("nls$splitEntityClock")
		));
		result.put("particle_core", Map.of(
			"net.minecraft.client.particle.ParticleEngine", List.of("nls$captureLocalDebris", "nls$clearLocalDebris")
		));
		result.put("c2me", Map.of(
			"net.minecraft.client.Options", List.of("nls$smallSyntheticView"),
			"net.minecraft.client.Minecraft", List.of("nls$dontFreezeWhileServerBoots")
		));
		result.put("modernfix", Map.of(
			"net.minecraft.client.Minecraft", List.of("nls$detachRetainedScene", "nls$disconnect"),
			"net.minecraft.client.multiplayer.ClientPacketListener", List.of("nls$configurationStarted")
		));
		result.put("rrls", Map.of(
			"net.minecraft.client.Minecraft", List.of("nls$bootWaitFrame"),
			"net.minecraft.client.gui.Gui", List.of("nls$returnToPlaceholder")
		));
		result.put("viafabricplus", Map.of(
			"net.minecraft.client.MouseHandler", List.of("nls$dispatchWaitingMouse"),
			"net.minecraft.client.KeyboardHandler", List.of("nls$dispatchWaitingKey"),
			"net.minecraft.client.multiplayer.ClientPacketListener", List.of("nls$configurationStarted"),
			"net.minecraft.client.gui.Gui", List.of("nls$returnToPlaceholder")
		));
		result.put("bobby", Map.of(
			"net.minecraft.client.Minecraft", List.of("nls$renderFrame"),
			"net.minecraft.client.multiplayer.ClientPacketListener", List.of("nls$configurationStarted"),
			"net.minecraft.client.renderer.GameRenderer", List.of("nls$freezePlaceholderScene"),
			"net.minecraft.client.renderer.LevelRenderer", List.of("nls$compileLoadingTerrainAsync")
		));
		result.put("distanthorizons", Map.of(
			"net.minecraft.client.Minecraft", List.of("nls$renderFrame"),
			"net.minecraft.client.multiplayer.ClientPacketListener", List.of("nls$configurationStarted"),
			"net.minecraft.client.renderer.GameRenderer", List.of("nls$freezePlaceholderScene"),
			"net.minecraft.client.renderer.LevelRenderer", List.of("nls$compileLoadingTerrainAsync")
		));
		result.put("fastquit", Map.of(
			"net.minecraft.client.Minecraft", List.of("nls$renderFrame"),
			"net.minecraft.client.gui.screens.worldselection.WorldOpenFlows", List.of("nls$preparingResources")
		));
		result.put("reeses-sodium-options", Map.of(
			"net.minecraft.client.gui.Gui", List.of("nls$returnToPlaceholder")
		));
		result.put("nvidium", Map.of(
			"net.minecraft.client.renderer.LevelRenderer", List.of("nls$compileLoadingTerrainAsync"),
			"net.minecraft.client.renderer.GameRenderer", List.of("nls$preparePlaceholderCamera")
		));
		return result;
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) throw new AssertionError(message);
	}
}
