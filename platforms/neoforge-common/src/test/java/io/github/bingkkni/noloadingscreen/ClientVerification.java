package io.github.bingkkni.noloadingscreen;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

/** Executed inside the actual FML CLIENT game classloader, never directly on a plain classpath. */
public final class ClientVerification {
	public static void run() throws Exception {
		if (Boolean.getBoolean("nls.verify.vanillaRegistryFixture")) {
			io.github.bingkkni.noloadingscreen.verification.NeoForgeBehaviorVerification.runRegistryFixture();
			return;
		}
		Map<String, String[]> targets = Map.ofEntries(
			Map.entry("net.minecraft.client.Minecraft", new String[]{"nls$pauseLoading", "nls$tick", "nls$disconnect", "nls$handlePlaceholderKeybinds", "nls$preloadSkin", "nls$showSavingWorld", "nls$interactiveSaveFrame", "nls$savingFinished", "nls$bootWaitFrame", "nls$singleplayerLoadStarted", "nls$keepPreparedScene", "nls$keepDisconnectedCamera", "nls$keepDisconnectedEngines"}),
			Map.entry("net.minecraft.client.player.AbstractClientPlayer", new String[]{"nls$placeholderSkin", "nls$bridgeLocalSkin", "nls$offlineSurvivalMode"}),
			Map.entry("net.minecraft.world.entity.Avatar", new String[]{"nls$modelCustomisation"}),
			Map.entry("net.minecraft.client.multiplayer.ClientPacketListener", new String[]{"nls$loginStarting", "nls$configurationStarted", "nls$blockChatPacket", "nls$blockCommandPacket", "nls$followServerPosition"}),
			Map.entry("net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl", new String[]{"nls$retainFailedLogin"}),
			Map.entry("net.minecraft.client.gui.screens.DisconnectedScreen", new String[]{"nls$parent", "nls$details"}),
			Map.entry("net.minecraft.client.gui.Gui", new String[]{"nls$drawLoadingOverlay"}),
			Map.entry("net.minecraft.client.MouseHandler", new String[]{"nls$scrollPlaceholder", "nls$dispatchWaitingMouse"}),
			Map.entry("net.minecraft.client.KeyboardHandler", new String[]{"nls$dispatchWaitingKey"}),
			Map.entry("net.minecraft.client.particle.ParticleEngine", new String[]{"nls$captureLocalDebris", "nls$extractLocalDebris", "nls$clearLocalDebris"}),
			Map.entry("net.minecraft.client.gui.screens.ConnectScreen", new String[]{"nls$observeEncryption", "nls$earlyConnectWorld", "nls$connection", "nls$setAborted"}),
			Map.entry("net.minecraft.client.gui.screens.worldselection.WorldOpenFlows", new String[]{"nls$preparingResources", "nls$interactiveResourceWait"}),
			Map.entry("net.minecraft.client.gui.screens.ChatScreen", new String[]{"nls$blockLoadingChat", "nls$initLoadingChat", "nls$loadingChatClick"}),
			Map.entry("net.minecraft.client.gui.components.CommandSuggestions", new String[]{"nls$skipLoadingSuggestions"}),
			Map.entry("net.minecraft.world.entity.Entity", new String[]{"nls$collide", "nls$updateFluidOnEyes"}),
			Map.entry("net.minecraft.client.renderer.entity.EntityRenderDispatcher", new String[]{"nls$splitEntityClock"}),
			Map.entry("net.minecraft.network.PacketProcessor$ListenerAndPacket", new String[]{"nls$measureIndividualPacket"}),
			Map.entry("net.minecraft.network.PacketProcessor", new String[]{"nls$loadingPacketBudget", "nls$yieldBetweenPackets"}),
			Map.entry("net.minecraft.client.resources.SkinManager", new String[]{"nls$observeSkinFuture", "nls$trackLocalLookup"}),
			Map.entry("net.minecraft.client.multiplayer.PlayerInfo", new String[]{"nls$skinLookup"}),
			Map.entry("net.minecraft.client.Options", new String[]{"nls$smallSyntheticView"}),
			Map.entry("net.minecraft.client.renderer.LevelRenderer", new String[]{"nls$showLocalPlayerWithoutTerrain", "nls$invalidateFreshRenderer", "nls$compileLoadingTerrainAsync"}),
			Map.entry("net.minecraft.world.entity.player.Player", new String[]{"nls$updatePlayerPose", "nls$backOffFromEdge"}),
			Map.entry("net.minecraft.world.entity.LivingEntity", new String[]{"nls$jumpPower", "nls$updateSwimAmount", "nls$updateInvisibilityStatus"}),
			Map.entry("net.minecraft.client.renderer.GameRenderer", new String[]{"nls$preparePlaceholderCamera", "nls$refreshEnvironment", "nls$hands"}),
			Map.entry("net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl", new String[]{"nls$setConnection"}),
			Map.entry("net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen", new String[]{"nls$connection", "nls$disconnectButton"}),
			Map.entry("net.minecraft.client.gui.screens.LevelLoadingScreen", new String[]{"nls$"}),
			Map.entry("net.minecraft.client.multiplayer.LevelLoadTracker", new String[]{"nls$"}),
			Map.entry("net.minecraft.client.player.LocalPlayer", new String[]{"nls$setCrouching", "nls$holdPosition", "nls$prepareHold"}),
			Map.entry("net.minecraft.client.multiplayer.LevelLoadTracker$WaitingForPlayerChunk", new String[]{"nls$releaseChunkReadiness"})
		);
		ClassLoader loader = ClientVerification.class.getClassLoader();
		for (var entry : targets.entrySet()) {
			Class<?> target = Class.forName(entry.getKey(), false, loader);
			for (String fragment : entry.getValue()) {
				require(Arrays.stream(target.getDeclaredMethods()).map(Method::getName).anyMatch(name -> name.contains(fragment)),
					entry.getKey() + " is missing transformed method " + fragment);
			}
			System.out.println("Verified Mixin target: " + entry.getKey());
		}
		assertHook("net.minecraft.client.Minecraft", "nls$renderFrame");
		assertHook("net.minecraft.client.Minecraft", "nls$returnToPlaceholder");
		assertHook("net.minecraft.client.renderer.GameRenderer", "nls$abortFrame");
		assertHook("net.minecraft.client.gui.render.GuiRenderer", "nls$abortFrame");
		assertHook("net.neoforged.neoforge.client.ClientHooks", "nls$stripMountedScreen");
		verifyOptionalMods();
		if (!Boolean.getBoolean("nls.verify.compatibility")) io.github.bingkkni.noloadingscreen.verification.NeoForgeBehaviorVerification.run();
		System.out.println("NeoForge CLIENT transformation inventory passed");
	}
	private static void verifyOptionalMods() throws Exception {
		var mods = net.neoforged.fml.loading.FMLLoader.getCurrent().getLoadingModList().getMods();
		for (var mod : mods) System.out.println("Discovered mod: " + mod.getModId() + "=" + mod.getVersion());
		boolean sodium = io.github.bingkkni.noloadingscreen.platform.LoaderServices.modVersion("sodium").isPresent();
		boolean via = io.github.bingkkni.noloadingscreen.platform.LoaderServices.modVersion("viaforge").isPresent();
		if (Boolean.getBoolean("nls.verify.compatibility")) require(sodium && via, "Compatibility run requires the exact supplied Sodium and ViaForge artifacts");
		if (sodium) {
			require(io.github.bingkkni.noloadingscreen.compat.SodiumCompatibility.supported(), "Sodium artifact must match the tested whitelist");
			String prefix = "net.caffeinemc.mods.sodium.client.";
			Map<String, String> hooks = Map.of(
				"render.SodiumWorldRenderer", "nls$coalesceFirstReload",
				"render.chunk.RenderSectionManager", "nls$leaveLoadingBuildsOnWorkers",
				"render.chunk.compile.executor.ChunkBuilder", "nls$smallVoidWorkerPool",
				"render.chunk.compile.executor.ChunkJobCollector", "nls$pending",
				"render.chunk.ShaderChunkRenderer", "nls$programs",
				"render.chunk.DefaultChunkRenderer", "nls$adoptWarmPrograms");
			for (var hook : hooks.entrySet()) assertHook(prefix + hook.getKey(), hook.getValue());
			io.github.bingkkni.noloadingscreen.verification.NeoForgeOptionalVerification.run();
		}
		if (via) assertHook("net.minecraft.client.multiplayer.ClientPacketListener", "sendConnectionDetails");
	}
	private static void assertHook(String target, String hook) throws Exception {
		Class<?> type = Class.forName(target, false, ClientVerification.class.getClassLoader());
		require(Arrays.stream(type.getDeclaredMethods()).anyMatch(method -> method.getName().contains(hook)), target + " missing " + hook);
		System.out.println("Verified optional hook: " + target + ":" + hook);
	}
	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
