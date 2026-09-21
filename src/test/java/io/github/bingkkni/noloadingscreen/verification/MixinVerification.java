package io.github.bingkkni.noloadingscreen.verification;

import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import io.github.bingkkni.noloadingscreen.gui.LoadingPauseScreen;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import sun.misc.Unsafe;

public final class MixinVerification implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		try {
			if (Boolean.getBoolean("nls.verify.coldStartGpu")) return;
			if (Boolean.getBoolean("nls.verify.inputCompatibility")) {
				InputCompatibilityVerification.run();
				if (Boolean.getBoolean("nls.verify.loadingCompatibility")) LoadingCompatibilityVerification.run();
				System.exit(0);
				return;
			}
			if (Boolean.getBoolean("nls.verify.optimizationCompatibility")) {
				verifyTransformations();
				OptimizationCompatibilityVerification.run();
				System.exit(0);
				return;
			}
			verifyTransformations();
			verifyConnectionOwnership();
			SharedConstants.tryDetectVersion();
			Bootstrap.bootStrap();
			RetainedLightQueueVerification.run();
			verifyPlaceholderRegressions();
			SkinPreloadVerification.run();
			SandboxVerification.run();
			LoadingVisualVerification.run();
			EarlyLoadingVerification.run();
			LoadingWorkVerification.run();
			JoinClassWarmupVerification.run();
			System.out.println("NoLoadingScreen headless verification passed: Mixins, lifecycle, input/chat, initialization, skin preload/appearance, visual clocks, animation and inventory.");
			System.exit(0);
		} catch (Throwable failure) {
			failure.printStackTrace();
			System.exit(1);
		}
	}

	static void verifyTransformations() throws ReflectiveOperationException {
		Map<String, String[]> targets = Map.ofEntries(
			Map.entry("net.minecraft.client.Minecraft", new String[]{"nls$pauseLoading", "nls$tick", "nls$disconnect", "nls$handlePlaceholderKeybinds", "nls$preloadSkin", "nls$showSavingWorld", "nls$interactiveSaveFrame", "nls$savingFinished", "nls$bootWaitFrame", "nls$singleplayerLoadStarted", "nls$keepPreparedScene", "nls$keepDisconnectedCamera", "nls$keepDisconnectedEngines", "nls$detachRetainedScene"}),
			Map.entry("net.minecraft.client.player.AbstractClientPlayer", new String[]{"nls$placeholderSkin", "nls$bridgeLocalSkin", "nls$offlineSurvivalMode"}),
			Map.entry("net.minecraft.world.entity.Avatar", new String[]{"nls$modelCustomisation"}),
			Map.entry("net.minecraft.client.multiplayer.ClientLevel", new String[]{"nls$retireLightQueue", "nls$discardRetiredLightTask"}),
			Map.entry("net.minecraft.client.multiplayer.ClientPacketListener", new String[]{"nls$loginStarting", "nls$configurationStarted", "nls$blockChatPacket", "nls$blockCommandPacket", "nls$followServerPosition", "nls$retireOutgoingLightQueue"}),
			Map.entry("net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl", new String[]{"nls$retainFailedLogin"}),
			Map.entry("net.minecraft.client.gui.screens.DisconnectedScreen", new String[]{"nls$parent", "nls$details"}),
			Map.entry("net.minecraft.client.renderer.extract.LevelExtractor", new String[]{"nls$showLocalPlayerWithoutTerrain", "nls$invalidateFreshRenderer", "nls$extractLocalDebris"}),
			Map.entry("net.minecraft.client.gui.Gui", new String[]{"nls$interceptScreen", "nls$returnToPlaceholder", "nls$keepHeldTransferKeys", "nls$tickPlaceholderHud", "nls$tickLocalInventory", "nls$allowLocalSavingUi"}),
			Map.entry("net.minecraft.client.MouseHandler", new String[]{"nls$scrollPlaceholder", "nls$dispatchWaitingMouse"}),
			Map.entry("net.minecraft.client.KeyboardHandler", new String[]{"nls$dispatchWaitingKey"}),
			Map.entry("net.minecraft.client.particle.ParticleEngine", new String[]{"nls$captureLocalDebris", "nls$clearLocalDebris"}),
			Map.entry("net.minecraft.client.gui.screens.ConnectScreen", new String[]{"nls$observeEncryption", "nls$earlyConnectWorld", "nls$connection", "nls$setAborted"}),
			Map.entry("net.minecraft.client.gui.screens.worldselection.WorldOpenFlows", new String[]{"nls$preparingResources", "nls$interactiveResourceWait"}),
			Map.entry("net.minecraft.client.gui.screens.ChatScreen", new String[]{"nls$blockLoadingChat", "nls$initLoadingChat", "nls$loadingChatClick"}),
			Map.entry("net.minecraft.client.gui.components.CommandSuggestions", new String[]{"nls$localSuggestions"}),
			Map.entry("net.minecraft.world.entity.Entity", new String[]{"nls$collide", "nls$fluidInteraction"}),
			Map.entry("net.minecraft.client.renderer.entity.EntityRenderDispatcher", new String[]{"nls$splitEntityClock"}),
			Map.entry("net.minecraft.network.PacketProcessor$ListenerAndPacket", new String[]{"nls$measureIndividualPacket"}),
			Map.entry("net.minecraft.network.PacketProcessor", new String[]{"nls$loadingPacketBudget", "nls$yieldBetweenPackets"}),
			Map.entry("net.minecraft.client.resources.SkinManager", new String[]{"nls$observeSkinFuture", "nls$trackLocalLookup"}),
			Map.entry("net.minecraft.client.multiplayer.PlayerInfo", new String[]{"nls$skinLookup"}),
			Map.entry("net.minecraft.client.Options", new String[]{"nls$smallSyntheticView"}),
			Map.entry("net.minecraft.client.renderer.LevelRenderer", new String[]{"nls$skyRenderer", "nls$setSkyRenderer", "nls$compileLoadingTerrainAsync"}),
			Map.entry("net.minecraft.world.entity.player.Player", new String[]{"nls$updatePlayerPose", "nls$backOffFromEdge"}),
			Map.entry("net.minecraft.world.entity.LivingEntity", new String[]{"nls$jumpPower", "nls$updateSwimAmount", "nls$updateInvisibilityStatus"}),
			Map.entry("net.minecraft.client.renderer.GameRenderer", new String[]{"nls$preparePlaceholderCamera", "nls$freezePlaceholderScene", "nls$refreshEnvironment", "nls$hands"}),
			Map.entry("net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl", new String[]{"nls$setConnection"}),
			Map.entry("net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen", new String[]{"nls$connection", "nls$disconnectButton"}),
			Map.entry("net.minecraft.client.gui.screens.LevelLoadingScreen", new String[]{"nls$"}),
			Map.entry("net.minecraft.client.multiplayer.LevelLoadTracker", new String[]{"nls$"}),
			Map.entry("net.minecraft.client.player.LocalPlayer", new String[]{"nls$setCrouching", "nls$holdPosition", "nls$prepareHold"}),
			Map.entry("net.minecraft.client.gui.Hud", new String[]{"nls$"})
		);
		ClassLoader loader = MixinVerification.class.getClassLoader();
		for (var entry : targets.entrySet()) {
			Class<?> target = Class.forName(entry.getKey(), false, loader);
			for (String fragment : entry.getValue()) {
				check(Arrays.stream(target.getDeclaredMethods()).map(Method::getName).anyMatch(name -> name.contains(fragment)),
					entry.getKey() + " is missing transformed method " + fragment);
			}
			System.out.println("Verified Mixin target: " + entry.getKey());
		}
	}

	private static void verifyConnectionOwnership() throws ReflectiveOperationException {
		Unsafe unsafe = unsafe();
		Minecraft minecraft = (Minecraft) unsafe.allocateInstance(Minecraft.class);
		Gui gui = (Gui) unsafe.allocateInstance(Gui.class);
		set(Minecraft.class, null, "instance", minecraft);
		set(Minecraft.class, minecraft, "gui", gui);
		set(Gui.class, gui, "minecraft", minecraft);

		FakeConnection connection = new FakeConnection();
		ServerReconfigScreen owner = (ServerReconfigScreen) unsafe.allocateInstance(ServerReconfigScreen.class);
		set(ServerReconfigScreen.class, owner, "connection", connection);
		set(NoLoadingScreen.class, null, "suppressedConfigScreen", owner);

		NoLoadingScreen.tickPlaceholder();
		check(connection.ticks == 1, "Hidden configuration connection must tick");

		Screen unrelatedMenu = (Screen) unsafe.allocateInstance(TitleScreen.class);
		set(Gui.class, gui, "screen", unrelatedMenu);
		NoLoadingScreen.tickPlaceholder();
		check(connection.ticks == 2, "Opening another screen must not stop the connection");
		check(NoLoadingScreen.canRevealConfigScreen(), "Another screen must not release connection ownership");

		Screen pause = (Screen) unsafe.allocateInstance(LoadingPauseScreen.class);
		set(Gui.class, gui, "screen", pause);
		NoLoadingScreen.tickPlaceholder();
		check(connection.ticks == 3, "Loading pause menu must keep the connection alive");

		set(Gui.class, gui, "screen", owner);
		NoLoadingScreen.tickPlaceholder();
		check(connection.ticks == 3, "Mounted vanilla owner must not receive an extra connection tick");

		set(Gui.class, gui, "screen", pause);
		Component originalReason = Component.literal("Original backend kick reason");
		connection.reason = originalReason;
		connection.connected = false;
		NoLoadingScreen.tickPlaceholder();
		check(connection.disconnections == 1, "A failed transfer must deliver vanilla disconnection handling");
		check(connection.reason == originalReason, "The original server reason must not be replaced");

		set(NoLoadingScreen.class, null, "holdActive", true);
		set(NoLoadingScreen.class, null, "gateReleased", true);
		set(NoLoadingScreen.class, null, "timelineStart", 123L);
		NoLoadingScreen.onDisconnected();
		check(!NoLoadingScreen.canRevealConfigScreen(), "Disconnect must release the configuration connection");
		check(!NoLoadingScreen.timelineActive(), "Disconnect must reset the timeline");
		check(!(boolean) get(NoLoadingScreen.class, "holdActive"), "Disconnect must reset player hold state");
		check(!(boolean) get(NoLoadingScreen.class, "gateReleased"), "Disconnect must reset readiness state");

		connection.connected = true;
		set(NoLoadingScreen.class, null, "suppressedConfigScreen", owner);
		NoLoadingScreen.onScreenChanging(unrelatedMenu);
		check(!connection.connected, "Returning to the title screen must close an abandoned connection");
		check(!NoLoadingScreen.canRevealConfigScreen(), "Returning to menus must discard stale connection ownership");
		verifyPauseButtons(connection);
		System.out.println("Verified hidden/menu/mounted connection ticks, kick reason, disconnect reset, title-screen cleanup and immediate pause buttons.");
	}

	private static void verifyPlaceholderRegressions() throws ReflectiveOperationException {
		Unsafe unsafe = unsafe();
		Minecraft minecraft = Minecraft.getInstance();
		CapturingChatListener chat = new CapturingChatListener(minecraft);
		set(Gui.class, minecraft.gui, "chatListener", chat);
		set(NoLoadingScreenConfig.class, null, "instance", new NoLoadingScreenConfig());
		set(PlaceholderWorld.class, null, "installed", true);
		ClientPacketListener listener = (ClientPacketListener) unsafe.allocateInstance(ClientPacketListener.class);
		ChatScreen screen = (ChatScreen) unsafe.allocateInstance(ChatScreen.class);
		// These uninitialized objects would throw or try to send unless the actual transformed
		// methods return BEFORE touching player, signing state, commands, or the connection.
		listener.sendChat("blocked");
		check(chat.count == 1 && chat.last.equals(NoLoadingScreen.blockedMessage(false)), "Packet-level chat guard");
		listener.sendCommand("say blocked");
		check(chat.count == 2 && chat.last.equals(NoLoadingScreen.blockedMessage(true)), "Packet-level command guard");
		screen.handleChatInput(" /say blocked", true);
		check(chat.count == 3 && chat.last.equals(NoLoadingScreen.blockedMessage(true)), "Chat-screen slash guard");
		screen.handleChatInput("blocked", true);
		check(chat.count == 4 && chat.last.equals(NoLoadingScreen.blockedMessage(false)), "Chat-screen message guard");
		check(chat.last.getStyle().getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.RED)), "Blocked message is red");
		CommandSuggestions suggestions = (CommandSuggestions) unsafe.allocateInstance(CommandSuggestions.class);
		var input = (net.minecraft.client.gui.components.EditBox) unsafe.allocateInstance(net.minecraft.client.gui.components.EditBox.class);
		set(net.minecraft.client.gui.components.EditBox.class, input, "value", "/gamemode cr");
		set(net.minecraft.client.gui.components.EditBox.class, input, "cursorPos", 12);
		set(CommandSuggestions.class, suggestions, "input", input);
		set(CommandSuggestions.class, suggestions, "commandUsage", new java.util.ArrayList<>());
		suggestions.updateCommandInfo();
		Field pending = CommandSuggestions.class.getDeclaredField("pendingSuggestions");
		pending.setAccessible(true);
		var future = (java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>) pending.get(suggestions);
		check(future.join().getList().stream().anyMatch(s -> s.getText().equals("creative")), "Local mode completion needs no player or connection");
		check(minecraft.player == null, "Editing guard works with no live player");

		Options options = (Options) unsafe.allocateInstance(Options.class);
		KeyMapping f5 = new KeyMapping("nls.test.perspective", 294, KeyMapping.Category.MISC);
		KeyMapping attack = new KeyMapping("nls.test.attack", 65, KeyMapping.Category.MISC);
		KeyMapping noClick = new KeyMapping("nls.test.unused", -1, KeyMapping.Category.MISC);
		set(Options.class, options, "keyTogglePerspective", f5);
		set(Options.class, options, "keyChat", noClick);
		set(Options.class, options, "keyCommand", noClick);
		set(Options.class, options, "keyInventory", noClick);
		set(Options.class, options, "keyMappings", new KeyMapping[]{f5, attack, noClick});
		set(Minecraft.class, minecraft, "options", options);
		GameRenderer renderer = (GameRenderer) unsafe.allocateInstance(GameRenderer.class);
		set(Minecraft.class, minecraft, "gameRenderer", renderer);
		Camera camera = new Camera();
		set(GameRenderer.class, renderer, "mainCamera", camera);
		options.setCameraType(CameraType.FIRST_PERSON);
		for (CameraType expected : new CameraType[]{CameraType.THIRD_PERSON_BACK, CameraType.THIRD_PERSON_FRONT, CameraType.FIRST_PERSON}) {
			KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(294));
			KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(65));
			PlaceholderWorld.handleSafeKeybinds();
			check(options.getCameraType() == expected, "Safe F5 cycles perspective without a live level");
			check(!attack.consumeClick(), "Gameplay clicks must not carry into new world");
		}
		ClientLevel level = (ClientLevel) unsafe.allocateInstance(ClientLevel.class);
		Method release = Arrays.stream(Gui.class.getDeclaredMethods()).filter(m -> m.getName().contains("nls$keepHeldTransferKeys")).findFirst().orElseThrow();
		release.setAccessible(true);
		Class<?> snapshotType = Class.forName("io.github.bingkkni.noloadingscreen.OutgoingWorld");
		var constructor = snapshotType.getDeclaredConstructors()[0];
		constructor.setAccessible(true);
		set(NoLoadingScreen.class, null, "outgoing", constructor.newInstance(level, null, null));
		attack.setDown(true);
		release.invoke(minecraft.gui);
		check(attack.isDown(), "Transient transfer screen retains held keys");
		set(NoLoadingScreen.class, null, "outgoing", null);
		release.invoke(minecraft.gui);
		check(!attack.isDown(), "Real menus still release keys");

		Biome biome = (Biome) unsafe.allocateInstance(Biome.class);
		set(Biome.class, biome, "attributes", EnvironmentAttributeMap.EMPTY);
		set(Level.class, level, "biomeManager", new BiomeManager((x, y, z) -> Holder.direct(biome), 0));
		set(ClientLevel.class, level, "environmentAttributes", EnvironmentAttributeSystem.builder()
			.addConstantLayer(EnvironmentAttributes.SKY_COLOR, base -> 0x78A7FF)
			.addConstantLayer(EnvironmentAttributes.SUN_ANGLE, base -> 45.0F).build());
		set(PlaceholderWorld.class, null, "level", level);
		minecraft.level = level;
		check(camera.attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, 1) == 0, "Uninitialized probe reproduces black sky");
		PlaceholderWorld.refreshEnvironment();
		check(camera.attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, 1) == 0x78A7FF, "First placeholder frame samples sky color");
		check(camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, 1) == 45.0F, "Sun uses retained environment, not default angle");
		camera.reset();
		minecraft.level = null;
		PlaceholderWorld.refreshEnvironment();
		check(camera.attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, 1) == 0, "Unbound world does not touch probe");

		LocalPlayer player = (LocalPlayer) unsafe.allocateInstance(LocalPlayer.class);
		player.setXRot(30);
		player.setYRot(120);
		Method arms = PlaceholderWorld.class.getDeclaredMethod("tickArmRotation", LocalPlayer.class);
		arms.setAccessible(true);
		arms.invoke(null, player);
		check(player.xBob == 15 && player.yBob == 60, "Arm lag follows vanilla half-distance smoothing");
		for (int i = 0; i < 20; i++) arms.invoke(null, player);
		check(Math.abs(player.xBob - 30) < .001 && Math.abs(player.yBob - 120) < .001, "Turning does not leave hands permanently tilted");
		set(PlaceholderWorld.class, null, "installed", false);
		set(PlaceholderWorld.class, null, "level", null);
		check(!NoLoadingScreen.isLoading(), "Idle gameplay permits chat again");
		System.out.println("Verified actual input/chat guards, transfer key retention, sky probe refresh and vanilla arm smoothing.");
	}

	private static final class CapturingChatListener extends ChatListener {
		private int count;
		private Component last;
		private CapturingChatListener(final Minecraft minecraft) { super(minecraft); }
		@Override public void handleSystemMessage(final Component message, final boolean remote) { this.last = message; this.count++; }
	}

	private static void verifyPauseButtons(final FakeConnection connection) throws ReflectiveOperationException {
		connection.connected = true;
		LoadingPauseScreen pause = new LoadingPauseScreen(Component.literal("Switching server"), connection);
		pause.width = 800;
		pause.height = 600;
		Method init = LoadingPauseScreen.class.getDeclaredMethod("init");
		init.setAccessible(true);
		init.invoke(pause);
		check(pause.children().size() == 2, "Loading menu must show return and disconnect buttons immediately");
		Button resume = (Button) pause.children().get(0);
		Button disconnect = (Button) pause.children().get(1);
		check(resume.active && disconnect.active, "Both loading buttons must be immediately usable");
		check(!pause.isPauseScreen(), "Loading menu must not pause connection progress");
		int handledBefore = connection.disconnections;
		disconnect.onPress(null);
		check(!connection.connected, "Loading menu Disconnect button must close the connection");
		check(connection.disconnections == handledBefore + 1, "Disconnect button must deliver disconnection immediately");
		check(!disconnect.active, "Disconnect button must disable itself after use");
	}

	private static Unsafe unsafe() throws ReflectiveOperationException {
		Field field = Unsafe.class.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		return (Unsafe) field.get(null);
	}

	private static void set(final Class<?> type, final Object instance, final String name, final Object value)
		throws ReflectiveOperationException {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		field.set(instance, value);
	}

	private static Object get(final Class<?> type, final String name) throws ReflectiveOperationException {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(null);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static final class FakeConnection extends Connection {
		private boolean connected = true;
		private int ticks;
		private int disconnections;
		private Component reason;

		private FakeConnection() {
			super(PacketFlow.CLIENTBOUND);
		}

		@Override
		public boolean isConnected() {
			return this.connected;
		}

		@Override
		public void tick() {
			this.ticks++;
		}

		@Override
		public void handleDisconnection() {
			this.disconnections++;
		}

		@Override
		public void disconnect(final Component reason) {
			this.reason = reason;
			this.connected = false;
		}
	}
}
