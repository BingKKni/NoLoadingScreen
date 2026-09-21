package io.github.bingkkni.noloadingscreen.verification;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.bingkkni.noloadingscreen.LocalSkinPreloader;
import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.PlaceholderControls;
import io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig;
import io.github.bingkkni.noloadingscreen.PlaceholderMovement;
import io.github.bingkkni.noloadingscreen.PlaceholderInteraction;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import io.github.bingkkni.noloadingscreen.InventoryClick;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import io.github.bingkkni.noloadingscreen.PlaceholderVisuals;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import io.github.bingkkni.noloadingscreen.SavingWorldView;
import io.github.bingkkni.noloadingscreen.gui.LoadingInventoryScreen;
import io.github.bingkkni.noloadingscreen.mixin.AvatarAccessor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.client.Camera;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.ChatAbilities;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.stats.StatsCounter;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import sun.misc.Unsafe;

/** Real LocalPlayer/animation methods, with an empty query-only world and no window/network. */
final class LoadingVisualVerification {
	private static int assertions;

	static void run() throws ReflectiveOperationException {
		Minecraft minecraft = Minecraft.getInstance();
		Options options = minecraft.options;
		set(Options.class, options, "modelParts", EnumSet.allOf(PlayerModelPart.class));
		options.setModelPart(PlayerModelPart.RIGHT_PANTS_LEG, false);
		options.setModelPart(PlayerModelPart.CAPE, false);
		set(Options.class, options, "mainHand", option(HumanoidArm.LEFT));
		KeyMapping none = new KeyMapping("nls.test.movement", -1, KeyMapping.Category.MISC);
		for (String name : List.of("keyUp", "keyDown", "keyLeft", "keyRight", "keyJump", "keyShift", "keySprint")) {
			set(Options.class, options, name, none);
		}
		set(Options.class, options, "sprintWindow", option(7));
		set(Options.class, options, "fovEffectScale", option(1.0));
		set(Options.class, options, "fov", option(70));
		for (String name : List.of("keyAttack", "keyUse", "keySwapOffhand", "keyDrop", "keyPickItem")) set(Options.class, options, name, none);
		KeyMapping[] hotbar = new KeyMapping[9];
		for (int i = 0; i < 9; i++) hotbar[i] = new KeyMapping("nls.test.slot" + i, 49 + i, KeyMapping.Category.MISC);
		set(Options.class, options, "keyHotbarSlots", hotbar);
		set(Minecraft.class, minecraft, "tutorial", allocate(net.minecraft.client.tutorial.Tutorial.class));
		ItemInHandRenderer hands = allocate(ItemInHandRenderer.class);
		set(ItemInHandRenderer.class, hands, "minecraft", minecraft);
		set(ItemInHandRenderer.class, hands, "mainHandItem", ItemStack.EMPTY);
		set(ItemInHandRenderer.class, hands, "offHandItem", ItemStack.EMPTY);
		set(GameRenderer.class, minecraft.gameRenderer, "itemInHandRenderer", hands);
		NoLoadingScreenConfig.get().placeholderFreeMove = false; // no native window/focus query
		EmptyLevel level = allocate(EmptyLevel.class);
		level.blocks = new java.util.HashMap<>();
		set(net.minecraft.world.level.Level.class, level, "isClientSide", true);
		set(net.minecraft.world.level.Level.class, level, "soundSeedGenerator", net.minecraft.util.RandomSource.create());
		ClientPacketListener listener = allocate(ClientPacketListener.class);
		set(ClientPacketListener.class, listener, "localGameProfile", new GameProfile(UUID.randomUUID(), "RegressionPlayer"));
		set(ClientPacketListener.class, listener, "playerInfoMap", Map.of()); // deliberately no cached PlayerInfo
		PlayerSkin startupSkin = SkinPreloadVerification.skin("startup", PlayerModelType.WIDE);
		SkinPreloadVerification.preload(listener.getLocalGameProfile().id(), CompletableFuture.completedFuture(Optional.of(startupSkin)));
		LocalPlayer player = new LocalPlayer(minecraft, level, listener, new StatsCounter(), new ClientRecipeBook(),
			Input.EMPTY, false, ChatAbilities.NO_RESTRICTIONS);
		check(!player.isModelPartShown(PlayerModelPart.HAT) && player.getMainArm() == HumanoidArm.RIGHT,
			"Reproduce vanilla's hidden layers and right-handed default before placeholder initialization");
		player.setId(1);
		player.setPos(0.5, 80, 0.5);
		player.setOnGround(true);
		set(PlaceholderWorld.class, null, "level", level);
		set(PlaceholderWorld.class, null, "player", player);
		set(PlaceholderWorld.class, null, "gameMode", new MultiPlayerGameMode(minecraft, listener));
		set(PlaceholderWorld.class, null, "installed", true);
		set(PlaceholderWorld.class, null, "synthetic", true);
		minecraft.level = null;
		minecraft.player = null;
		minecraft.gameMode = null;
		set(Gui.class, minecraft.gui, "screen", null);

		// Same vanilla chain as the supplied log: isSwimming -> isSpectator -> getPlayerInfo.
		try {
			player.updateSwimming();
			throw new AssertionError("The unbound fixture must reproduce the supplied null-connection failure");
		} catch (NullPointerException expected) {
			check(expected.getMessage().contains("getConnection"), "Reproduced the exact null connection, not an unrelated fixture error");
		}
		set(LocalPlayer.class, player, "startedUsingItem", true);
		invoke(PlaceholderWorld.class, null, "initializeMovement");
		verifyAppearance(player, options);
		check(player.getMainArm() == HumanoidArm.LEFT, "Main hand is synchronized before the first placeholder frame");
		check(PlaceholderWorld.bind(), "Bind first skin lookup");
		try {
			check(player.getSkin() == startupSkin, "Skin loaded before player construction is available on the very first lookup");
		} finally {
			PlaceholderWorld.unbind();
		}
		check(!player.isUsingItem(), "Captured use pose clears without firing release-use callbacks");
		check(minecraft.getConnection() == null && minecraft.level == null && minecraft.gameMode == null,
			"Initialization binds all fields only for its own scope");
		check(player.getPose() == Pose.STANDING, "Real pose initialization completes without cached PlayerInfo");
		PlaceholderWorld.tick();
		check(minecraft.player == null && minecraft.level == null && minecraft.gameMode == null,
			"Local tick restores the vanilla null session");
		level.failQueries = true;
		try {
			PlaceholderWorld.tick();
			throw new AssertionError("Injected world query failure must propagate to the caller's fallback boundary");
		} catch (IllegalStateException expected) {
			check(expected.getMessage().equals("query failure"), "Test failed inside the world query");
		}
		check(minecraft.player == null && minecraft.level == null && minecraft.gameMode == null,
			"Even failed local updates cannot leak a binding into vanilla ticks");
		level.failQueries = false;
		verifySkinAndSettings(minecraft, player);
		verifyFlyingHandoff(minecraft, player);
		verifyLoadingLookAndHold(minecraft, player, level);

		check(PlaceholderWorld.bind(), "Bind test player");
		try {
			verifyClocksAndCamera(minecraft, player);
			verifyAnimations(player);
			verifyHeadBeforeLoaded(player);
		} finally {
			PlaceholderWorld.unbind();
		}
		check(PlaceholderWorld.renderDelta(DeltaTracker.ZERO) == DeltaTracker.ZERO, "Unbound renderer is not modified");
		check(PlaceholderWorld.entityPartialTick(player, .3F) == .3F, "Unbound entity interpolation is not modified");
		verifyMenuPhysics(minecraft, player);
		BlockFeedbackVerification.prepare(minecraft, level);
		verifyInteractions(minecraft, player, level);
		BlockFeedbackVerification.run(minecraft, player, level);
		verifyInventory(minecraft, player);
		BlockFeedbackVerification.assertCleared();
		verifySavingView(minecraft, player, level);
		LoadingTransitionsVerification.run(minecraft, player, level);
		set(PlaceholderWorld.class, null, "installed", false);
		set(PlaceholderWorld.class, null, "synthetic", false);
		set(PlaceholderWorld.class, null, "level", null);
		set(PlaceholderWorld.class, null, "player", null);
		set(PlaceholderWorld.class, null, "gameMode", null);
		System.out.println("LoadingVisualVerification: " + assertions + " assertions passed (skin/layers/main hand, null-connection regression, split interpolation, animation, menu physics, local blocks/hotbar/offhand/inventory).");
	}

	private static void verifyFlyingHandoff(final Minecraft minecraft, final LocalPlayer player) throws ReflectiveOperationException {
		set(PlaceholderWorld.class, null, "synthetic", false);
		player.getAbilities().mayfly = true;
		player.getAbilities().flying = true;
		player.setOnGround(false);
		player.setDeltaMovement(Vec3.ZERO);
		minecraft.options.keyJump.setDown(true);
		invoke(PlaceholderWorld.class, null, "initializeMovement");
		check(player.getAbilities().flying && !player.noPhysics, "Adopted flight retains collision without the override");
		set(Gui.class, minecraft.gui, "screen", allocate(net.minecraft.client.gui.screens.ChatScreen.class));
		NoLoadingScreenConfig.get().placeholderFreeMove = true;
		double height = player.getY();
		for (int i = 0; i < 20; i++) PlaceholderWorld.tick();
		check(player.getY() == height && player.getAbilities().flying, "Flying handoff remains airborne even with chat open");
		set(Gui.class, minecraft.gui, "screen", null);
		NoLoadingScreenConfig.get().placeholderFreeMove = false;
		minecraft.options.keyJump.setDown(false);
		player.getAbilities().flying = false;
		set(PlaceholderWorld.class, null, "synthetic", true);
		invoke(PlaceholderWorld.class, null, "initializeMovement");
		check(!((PlaceholderControls) get(PlaceholderWorld.class, null, "controls")).flying(), "New walking player resets inherited flight");
	}

	private static void verifyLoadingLookAndHold(final Minecraft minecraft, final LocalPlayer player, final EmptyLevel level)
		throws ReflectiveOperationException {
		set(Options.class, minecraft.options, "sensitivity", option(.5));
		set(Options.class, minecraft.options, "invertXMouse", option(false));
		set(Options.class, minecraft.options, "invertYMouse", option(false));
		var window = allocate(com.mojang.blaze3d.platform.Window.class);
		set(com.mojang.blaze3d.platform.Window.class, window, "focused", true);
		set(Minecraft.class, minecraft, "window", window);
		set(Minecraft.class, minecraft, "framerateLimitTracker", allocate(com.mojang.blaze3d.platform.FramerateLimitTracker.class));
		var mouse = new net.minecraft.client.MouseHandler(minecraft);
		set(net.minecraft.client.MouseHandler.class, mouse, "mouseGrabbed", true);
		set(net.minecraft.client.MouseHandler.class, mouse, "accumulatedDX", 40.0);
		set(net.minecraft.client.MouseHandler.class, mouse, "accumulatedDY", 20.0);
		player.setYRot(0);
		player.setXRot(0);
		Method look = java.util.Arrays.stream(Minecraft.class.getDeclaredMethods())
			.filter(m -> m.getName().contains("nls$mouseLook")).findFirst().orElseThrow();
		look.setAccessible(true);
		level.chunksMissing = true;
		look.invoke(minecraft, mouse);
		check(player.getYRot() != 0 && player.getXRot() != 0, "Actual placeholder mouse routing works with no chunks");
		check(minecraft.level == null && minecraft.player == null, "Mouse routing releases its placeholder binding");

		// Reproduce the window after login but before terrain: the gate can open BEFORE a
		// position packet. That packet must replace the hold anchor, including relative teleports.
		set(PlaceholderWorld.class, null, "installed", false);
		minecraft.level = level;
		minecraft.player = player;
		set(NoLoadingScreen.class, null, "holdActive", true);
		set(NoLoadingScreen.class, null, "heldPlayer", player);
		set(NoLoadingScreen.class, null, "holdPos", new Vec3(0, 80, 0));
		set(Minecraft.class, minecraft, "packetProcessor", new net.minecraft.network.PacketProcessor(Thread.currentThread()));
		set(net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl.class, player.connection, "minecraft", minecraft);
		set(ClientLevel.class, level, "blockStatePredictionHandler", new net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler());
		CapturingConnection connection = new CapturingConnection();
		((io.github.bingkkni.noloadingscreen.mixin.ClientCommonPacketListenerImplAccessor) player.connection).nls$setConnection(connection);
		player.connection.handleMovePlayer(new net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket(1,
			new net.minecraft.world.entity.PositionMoveRotation(new Vec3(100, 120, -100), Vec3.ZERO, 45, 15), java.util.Set.of()));
		check(connection.sent.size() == 2
			&& connection.sent.get(0) instanceof net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket
			&& connection.sent.get(1) instanceof net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot,
			"Actual teleport handler still emits exactly vanilla's two acknowledgements, in order");
		player.getAbilities().flying = true;
		player.setOnGround(true); // stale grounded flag must not cancel flight in vanilla aiStep
		NoLoadingScreen.beforePlayerAiStep(player);
		check(!player.onGround() && player.getAbilities().flying, "Missing terrain is not a landing");
		set(net.minecraft.client.MouseHandler.class, mouse, "accumulatedDX", 60.0);
		float beforeYaw = player.getYRot();
		look.invoke(minecraft, mouse);
		float yaw = player.getYRot();
		player.setPos(101, 119, -101);
		NoLoadingScreen.onPlayerAiStep(player);
		check(player.position().equals(new Vec3(100, 120, -100)), "Hold follows the server teleport, not the pre-login anchor");
		check(yaw != beforeYaw && player.getYRot() == yaw, "Safety hold does not freeze/revert mouse look");
		Camera camera = minecraft.gameRenderer.mainCamera();
		camera.setEntity(player);
		invoke(Camera.class, camera, "alignWithEntity", float.class, .5F);
		check(camera.yRot() == yaw, "Rendered camera follows held player's live rotation without terrain");
		player.connection.handleMovePlayer(new net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket(2,
			new net.minecraft.world.entity.PositionMoveRotation(new Vec3(3, -2, 4), Vec3.ZERO, 10, -5),
			java.util.Set.of(net.minecraft.world.entity.Relative.X, net.minecraft.world.entity.Relative.Y,
				net.minecraft.world.entity.Relative.Z, net.minecraft.world.entity.Relative.Y_ROT, net.minecraft.world.entity.Relative.X_ROT)));
		player.setPos(999, 1, 999);
		NoLoadingScreen.onPlayerAiStep(player);
		check(player.position().equals(new Vec3(103, 118, -96)) && player.getYRot() == yaw + 10,
			"Relative teleport updates anchor only after vanilla resolves its coordinates and rotation");
		player.connection.handleMovePlayer(new net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket(3,
			new net.minecraft.world.entity.PositionMoveRotation(new Vec3(1, 85, 1), Vec3.ZERO, yaw, 0), java.util.Set.of()));
		check(connection.sent.size() == 6, "Local hold and mouse look add no teleport/ability packets");
		((io.github.bingkkni.noloadingscreen.mixin.ClientCommonPacketListenerImplAccessor) player.connection).nls$setConnection(null);
		level.chunksMissing = false;
		NoLoadingScreen.beforePlayerAiStep(player);
		player.setPos(2, 84, 2);
		NoLoadingScreen.onPlayerAiStep(player);
		check(player.position().equals(new Vec3(2, 84, 2)), "Loaded destination restores normal motion");
		check(!(boolean) get(NoLoadingScreen.class, null, "holdActive"), "Destination chunk clears the hold without needing another aiStep tail");
		minecraft.level = null;
		minecraft.player = null;
		set(PlaceholderWorld.class, null, "installed", true);
		player.getAbilities().flying = false;
		invoke(PlaceholderWorld.class, null, "initializeMovement");
	}

	private static void verifyAppearance(final LocalPlayer player, final Options options) {
		for (PlayerModelPart part : PlayerModelPart.values()) {
			check(player.isModelPartShown(part) == options.isModelPartEnabled(part), "Skin layer follows options: " + part);
		}
	}

	private static void verifySkinAndSettings(final Minecraft minecraft, final LocalPlayer player) throws ReflectiveOperationException {
		Options options = minecraft.options;
		options.setModelPart(PlayerModelPart.JACKET, false);
		options.setModelPart(PlayerModelPart.LEFT_SLEEVE, false);
		options.setModelPart(PlayerModelPart.RIGHT_PANTS_LEG, true);
		set(OptionInstance.class, options.mainHand(), "value", HumanoidArm.RIGHT);
		PlaceholderWorld.tick();
		verifyAppearance(player, options);
		check(player.getMainArm() == HumanoidArm.RIGHT, "Appearance changes during loading apply without a server echo");

		CompletableFuture<Optional<PlayerSkin>> texture = new CompletableFuture<>();
		SkinPreloadVerification.preload(player.getUUID(), texture);
		PlayerSkin readySkin = SkinPreloadVerification.skin("placeholder", PlayerModelType.SLIM);
		PlayerSkin serverSkin = SkinPreloadVerification.skin("server", PlayerModelType.WIDE);
		check(PlaceholderWorld.bind(), "Bind skin test player");
		try {
			check(player.getSkin() == DefaultPlayerSkin.get(player.getUUID()), "Pending texture still uses vanilla default skin");
			texture.complete(Optional.of(readySkin));
			check(player.getSkin() == readySkin && player.getSkin().model() == PlayerModelType.SLIM,
				"Completed skin, slim model, cape and elytra are returned intact without PlayerInfo");
			LocalPlayer other = new LocalPlayer(minecraft, minecraft.level, player.connection, new StatsCounter(), new ClientRecipeBook(),
				Input.EMPTY, false, ChatAbilities.NO_RESTRICTIONS);
			check(other.getUUID().equals(player.getUUID()) && other.getSkin() == DefaultPlayerSkin.get(other.getUUID()),
				"Same UUID is insufficient: another player must still use vanilla's lookup");
			PlayerInfo info = new PlayerInfo(player.getGameProfile(), false);
			set(PlayerInfo.class, info, "skinLookup", (Supplier<PlayerSkin>) () -> serverSkin);
			set(AbstractClientPlayer.class, player, "playerInfo", info);
			set(PlaceholderWorld.class, null, "synthetic", false);
			check(player.getSkin() == serverSkin, "An adopted player's server skin must not be replaced with the account skin");
			player.getEntityData().set(AvatarAccessor.nls$modelCustomisation(), (byte)PlayerModelPart.HAT.getMask());
			player.setMainArm(HumanoidArm.LEFT);
			PlaceholderWorld.tick();
			check(player.isModelPartShown(PlayerModelPart.HAT) && !player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE)
				&& player.getMainArm() == HumanoidArm.LEFT, "Adopted players retain the server's layers and main hand");
			set(PlaceholderWorld.class, null, "synthetic", true);
			set(PlaceholderWorld.class, null, "installed", false);
			check(player.getSkin() == serverSkin, "Inactive placeholders cannot override normal skin lookup");
			verifyRealSkinBridge(minecraft, player, readySkin);
		} finally {
			set(PlaceholderWorld.class, null, "installed", true);
			set(PlaceholderWorld.class, null, "synthetic", true);
			set(AbstractClientPlayer.class, player, "playerInfo", null);
			PlaceholderWorld.unbind();
		}
		PlaceholderWorld.tick();
		verifyAppearance(player, options);
	}

	private static void verifyRealSkinBridge(final Minecraft minecraft, final LocalPlayer player, final PlayerSkin cached)
		throws ReflectiveOperationException {
		Object previousManager = get(Minecraft.class, minecraft, "skinManager");
		try {
			set(AbstractClientPlayer.class, player, "playerInfo", null);
			check(player.getSkin() == cached, "Real local player retains the account skin before PlayerInfo arrives");
			NoLoadingScreenConfig.get().enabled = false;
			check(player.getSkin() == DefaultPlayerSkin.get(player.getUUID()), "Disabled mod restores the missing-PlayerInfo default");
			NoLoadingScreenConfig.get().enabled = true;
			LocalPlayer other = new LocalPlayer(minecraft, minecraft.level, player.connection, new StatsCounter(), new ClientRecipeBook(),
				Input.EMPTY, false, ChatAbilities.NO_RESTRICTIONS);
			check(other.getSkin() == DefaultPlayerSkin.get(other.getUUID()), "Real-player bridge excludes other instances with the same UUID");

			CompletableFuture<Optional<PlayerSkin>> loading = new CompletableFuture<>();
			var manager = new SkinPreloadVerification.TestSkinManager(loading);
			set(Minecraft.class, minecraft, "skinManager", manager);
			set(AbstractClientPlayer.class, player, "playerInfo", new PlayerInfo(player.getGameProfile(), false));
			// PlayerInfo.createSkinLookup checks the session account to determine signature policy.
			// Supply its actual vanilla supplier directly; no authenticated User is needed headlessly.
			PlayerInfo info = (PlayerInfo) get(AbstractClientPlayer.class, player, "playerInfo");
			set(PlayerInfo.class, info, "skinLookup", manager.createLookup(player.getGameProfile(), false));
			for (int i = 0; i < 20; i++) check(player.getSkin() == cached, "Pending server lookup must not flash a default skin");
			check(manager.requests == 1, "Only vanilla requests the skin; rendering observes that exact Future");
			PlayerSkin server = SkinPreloadVerification.skin("new_server", PlayerModelType.WIDE);
			loading.complete(Optional.of(server));
			check(player.getSkin() == server, "Completed custom server skin immediately replaces the temporary account skin");

			var empty = new CompletableFuture<Optional<PlayerSkin>>();
			manager = new SkinPreloadVerification.TestSkinManager(empty);
			set(Minecraft.class, minecraft, "skinManager", manager);
			info = new PlayerInfo(player.getGameProfile(), false);
			set(PlayerInfo.class, info, "skinLookup", manager.createLookup(player.getGameProfile(), false));
			set(AbstractClientPlayer.class, player, "playerInfo", info);
			check(player.getSkin() == cached, "Replacement PlayerInfo starts a new readiness check");
			empty.complete(Optional.empty());
			check(player.getSkin() == DefaultPlayerSkin.get(player.getUUID()), "Authoritative empty/failed server texture result is not overwritten forever");

			CompletableFuture<Optional<PlayerSkin>> lateAccount = new CompletableFuture<>();
			SkinPreloadVerification.preload(player.getUUID(), lateAccount);
			manager = new SkinPreloadVerification.TestSkinManager(CompletableFuture.completedFuture(Optional.empty()));
			set(Minecraft.class, minecraft, "skinManager", manager);
			info = new PlayerInfo(player.getGameProfile(), false);
			set(PlayerInfo.class, info, "skinLookup", manager.createLookup(player.getGameProfile(), false));
			set(AbstractClientPlayer.class, player, "playerInfo", info);
			check(player.getSkin() == DefaultPlayerSkin.get(player.getUUID()), "Server lookup can finish before the account preload");
			// Simulate expiration: a fresh get(profile) would return a DIFFERENT, pending Future.
			manager.texture = new CompletableFuture<>();
			lateAccount.complete(Optional.of(cached));
			check(player.getSkin() == DefaultPlayerSkin.get(player.getUUID()) && manager.requests == 1,
				"Late account readiness plus cache expiry cannot override vanilla's completed empty lookup");
		} finally {
			NoLoadingScreenConfig.get().enabled = true;
			set(Minecraft.class, minecraft, "skinManager", previousManager);
		}
	}

	private static void verifyClocksAndCamera(final Minecraft minecraft, final LocalPlayer player) throws ReflectiveOperationException {
		PlaceholderMovement movement = (PlaceholderMovement) get(PlaceholderWorld.class, null, "movement");
		movement.reset(0, 80, 0, 0, 0, 0, true);
		movement.tick(1, 0, 0, 0, new PlaceholderMovement.Physics(.1, .05, .42, .08, .6, .91, .98),
			false, false, false, true, 80, 320, motion -> motion);
		TestDelta source = new TestDelta();
		for (float alpha : new float[]{0, .25F, .5F, .75F, 1}) {
			source.alpha = alpha;
			DeltaTracker clock = PlaceholderWorld.prepareRender(source);
			check(clock.getGameTimeDeltaPartialTick(true) == alpha, "Camera and hand alpha remains live");
			check(clock.getGameTimeDeltaPartialTick(false) == 1, "World environment stays frozen");
			check(clock.getGameTimeDeltaTicks() == 0 && clock.getRealtimeDeltaTicks() == .2F, "No gameplay time; UI real time preserved");
			check(Math.abs(player.getZ() - movement.z(alpha)) < 1E-9 && player.zo == player.getZ(), "Position is interpolated exactly once");
			check(PlaceholderWorld.entityPartialTick(player, 1) == alpha, "Controlled avatar has live animation alpha");
			check(PlaceholderWorld.entityPartialTick(allocate(LocalPlayer.class), alpha) == 1, "Other entities stay frozen, not replaying their last tick");
		}
		Camera camera = minecraft.gameRenderer.mainCamera();
		camera.setEntity(player);
		set(Camera.class, camera, "fovModifier", 1.0F);
		set(Camera.class, camera, "oldFovModifier", 1.0F);
		player.setSprinting(true);
		player.getAbilities().flying = true;
		camera.tick();
		float start = (float) invoke(Camera.class, camera, "calculateFov", float.class, 0.0F);
		float middle = (float) invoke(Camera.class, camera, "calculateFov", float.class, .5F);
		float end = (float) invoke(Camera.class, camera, "calculateFov", float.class, 1.0F);
		check(start < middle && middle < end, "Vanilla sprint/flight FOV has intermediate rendered values");
		player.setXRot(30);
		player.setYRot(120);
		invoke(PlaceholderWorld.class, null, "tickArmRotation", LocalPlayer.class, player);
		check(Mth.lerp(.25F, player.xBobO, player.xBob) < Mth.lerp(.75F, player.xBobO, player.xBob), "Arm lag interpolates within one tick");
	}

	private static void verifyAnimations(final LocalPlayer player) {
		player.getAbilities().flying = false;
		player.setOnGround(true);
		player.walkAnimation.stop();
		float oldWalk = player.walkAnimation.position();
		int oldAge = player.tickCount;
		player.swinging = true;
		player.swingTime = 1;
		for (int i = 0; i < 8; i++) PlaceholderVisuals.tick(player, 0, 0, .2);
		check(player.walkAnimation.position() > oldWalk && player.walkAnimation.speed() > 0, "Moving third-person limbs advance");
		check(player.tickCount == oldAge + 8 && !player.swinging, "Idle animation age advances and captured swing finishes");
		check(player.yHeadRot == player.getYRot(), "Head follows current look");
		check(Math.abs(Mth.wrapDegrees(player.yHeadRot - player.yBodyRot)) <= 50.01F, "Body follows head with vanilla angle limit");
		check(player.avatarState().getInterpolatedWalkDistance(1) > 0 && player.avatarState().getInterpolatedBob(1) > 0,
			"Walking updates vanilla view bob and cloak distance state");
		float movingSpeed = player.walkAnimation.speed();
		for (int i = 0; i < 12; i++) PlaceholderVisuals.tick(player, 0, 0, 0);
		check(player.walkAnimation.speed() < movingSpeed * .01, "Stopping decays limb animation instead of freezing it mid-stride");
		player.getAbilities().flying = true;
		player.setOnGround(false);
		float oldBob = player.avatarState().getInterpolatedBob(1);
		PlaceholderVisuals.tick(player, 0, .1, .2);
		check(player.avatarState().getInterpolatedBob(1) < oldBob, "Flight stops walking camera bob");
	}

	/**
	 * The window between login and ServerboundPlayerLoadedPacket: vanilla's LocalPlayer.tick returns
	 * at once, so nothing copies the view into the head yaw. The constructor's random yaw against a
	 * zero yHeadRotO then renders as a per-tick sawtooth, and the body ignores the camera.
	 */
	private static void verifyHeadBeforeLoaded(final LocalPlayer player) throws ReflectiveOperationException {
		Field loaded = ClientPacketListener.class.getDeclaredField("clientLoaded");
		loaded.setAccessible(true);
		loaded.setBoolean(player.connection, false);
		player.yHeadRot = 3.0F;
		player.yHeadRotO = 0.0F;
		player.yBodyRot = 0.0F;
		player.yBodyRotO = 0.0F;
		player.setYRot(120.0F);
		float swing = player.attackAnim;
		int age = player.tickCount;
		for (int i = 0; i < 20; i++) NoLoadingScreen.beforePlayerTick(player);
		check(player.yHeadRot == 120.0F && player.yHeadRotO == 120.0F, "Before the loaded packet the head follows the view without a stale interpolation source");
		check(Math.abs(Mth.wrapDegrees(player.yHeadRot - player.yBodyRot)) <= 50.01F, "The body turns after the head with vanilla's limit");
		check(player.attackAnim == swing && player.tickCount == age, "Only rotation bookkeeping runs: no swing, age, movement or packet side effects");
		player.setYRot(-60.0F);
		NoLoadingScreen.beforePlayerTick(player);
		check(player.yHeadRotO == 120.0F && player.yHeadRot == -60.0F, "Each tick keeps one previous head yaw for the renderer's interpolation");
		loaded.setBoolean(player.connection, true);
		player.setYRot(0.0F);
		NoLoadingScreen.beforePlayerTick(player);
		check(player.yHeadRot == -60.0F, "Once vanilla ticks the player again the hook steps aside");
	}

	private static void verifyMenuPhysics(final Minecraft minecraft, final LocalPlayer player) throws ReflectiveOperationException {
		PlaceholderMovement movement = (PlaceholderMovement) get(PlaceholderWorld.class, null, "movement");
		NoLoadingScreenConfig.get().placeholderFreeMove = true;
		for (Screen menu : List.of(new LoadingInventoryScreen(player), allocate(net.minecraft.client.gui.screens.ChatScreen.class))) {
			set(Gui.class, minecraft.gui, "screen", menu);
			movement.reset(0, 90, 0, .2, -.2, 0, false);
			int age = player.tickCount;
			minecraft.options.keyJump.setDown(true); // typing a space must not toggle flight
			PlaceholderWorld.tick();
			check(movement.y(1) < 90 && movement.x(1) > 0, "Open inventory/chat does not freeze gravity or horizontal momentum");
			check(player.tickCount > age && !player.input.keyPresses.jump(), "Animation continues but menu input is not movement");
			check(minecraft.level == null && minecraft.player == null, "Menu physics does not leak a binding");
		}
		minecraft.options.keyJump.setDown(false);
		set(Gui.class, minecraft.gui, "screen", null);
		NoLoadingScreenConfig.get().placeholderFreeMove = false;
	}

	private static void verifyInteractions(final Minecraft minecraft, final LocalPlayer player, final EmptyLevel level) throws ReflectiveOperationException {
		DataComponentMap components = DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build();
		for (var item : List.of(Items.DIAMOND, Items.STONE, Items.COBBLESTONE, Items.OAK_STAIRS)) item.builtInRegistryHolder().bindComponents(components);
		check(PlaceholderWorld.bind(), "Bind for local interactions");
		try {
			BlockPos target = new BlockPos(3, 80, 3);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target).add(0, .5, 0), Direction.UP, target, false);
			level.blocks.put(target, Blocks.BEDROCK.defaultBlockState());
			check(!PlaceholderInteraction.breakBlock(player, hit), "Survival cannot break bedrock");
			PlaceholderWorld.setLocalMode(net.minecraft.world.level.GameType.CREATIVE);
			check(PlaceholderInteraction.breakBlock(player, hit) && level.getBlockState(target).isAir(), "Creative breaks bedrock immediately");
			PlaceholderWorld.setLocalMode(net.minecraft.world.level.GameType.SURVIVAL);
			check(!PlaceholderInteraction.breakBlock(player, hit), "Breaking air is a no-op");
			level.blocks.put(target, Blocks.STONE.defaultBlockState());
			player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 8));
			PlaceholderInteraction.selectSlot(player, 0);
			player.getAbilities().instabuild = true; // mode, not a stale ability flag, owns stack consumption
			check(PlaceholderInteraction.placeBlock(player, InteractionHand.MAIN_HAND, hit), "Right action places a held block");
			check(level.getBlockState(target.above()).is(Blocks.COBBLESTONE) && player.getMainHandItem().getCount() == 7, "Survival placement consumes one despite stale abilities");
			check(!PlaceholderInteraction.placeBlock(player, InteractionHand.MAIN_HAND, hit) && player.getMainHandItem().getCount() == 7, "Occupied placement does not consume items");
			level.blocks.remove(target.above());
			level.obstructed = true;
			check(!PlaceholderInteraction.placeBlock(player, InteractionHand.MAIN_HAND, hit), "Placement respects collision validation");
			level.obstructed = false;
			player.getInventory().setItem(0, new ItemStack(Items.OAK_STAIRS, 3));
			player.setYRot(90);
			check(PlaceholderInteraction.placeBlock(player, InteractionHand.MAIN_HAND, hit), "Directional block places through vanilla context");
			check(level.getBlockState(target.above()).getValue(net.minecraft.world.level.block.StairBlock.FACING) == player.getDirection(), "Placement preserves facing, not default state");
			player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
			level.blocks.remove(target.above());
			check(!PlaceholderInteraction.placeBlock(player, InteractionHand.MAIN_HAND, hit) && player.getMainHandItem().getCount() == 5, "Non-block use does not execute unrelated item actions");
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE, 4));
			check(PlaceholderInteraction.placeBlock(player, InteractionHand.OFF_HAND, hit) && player.getOffhandItem().getCount() == 3, "Offhand block placement works locally");
			PlaceholderInteraction.swapOffhand(player);
			check(player.getMainHandItem().is(Items.STONE) && player.getOffhandItem().is(Items.DIAMOND), "F swaps the actual disposable inventory stacks");
			PlaceholderInteraction.selectSlot(player, 2);
			check(player.getInventory().getSelectedSlot() == 2, "Hotbar selection changes locally");
			PlaceholderInteraction.selectSlot(player, 99);
			check(player.getInventory().getSelectedSlot() == 2, "Invalid hotbar selection is ignored");
			var missing = new BlockHitResult(new Vec3(1000, 80, 1000), Direction.UP, new BlockPos(1000, 80, 1000), false);
			check(!PlaceholderInteraction.breakBlock(player, missing), "Unloaded chunks cannot be edited");
			player.getAbilities().instabuild = false;
		} finally {
			PlaceholderWorld.unbind();
		}
		PlaceholderInteraction.selectSlot(player, 0);
		check(player.getInventory().getSelectedSlot() == 2, "Unbound interactions are rejected");
		// A real packet-backed controller is never called: listener.connection is intentionally
		// absent, so a swing/slot/placement send would already have failed these tests.
		KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(50));
		PlaceholderWorld.handleSafeKeybinds();
		check(player.getInventory().getSelectedSlot() == 1 && minecraft.player == null, "Actual hotbar key routing binds and restores the player");
		verifyActionKeys(minecraft, player, level);
		verifyWheel(minecraft, player);
		verifyHandSwap(minecraft, player);
		PlaceholderWorld.setLocalMode(net.minecraft.world.level.GameType.CREATIVE);
		PickBlockVerification.run(minecraft, player, level);
		PlaceholderWorld.setLocalMode(net.minecraft.world.level.GameType.SURVIVAL);
	}

	private static void verifyActionKeys(final Minecraft minecraft, final LocalPlayer player, final EmptyLevel level) throws ReflectiveOperationException {
		KeyMapping attack = new KeyMapping("nls.test.localAttack", 74, KeyMapping.Category.MISC);
		KeyMapping use = new KeyMapping("nls.test.localUse", 75, KeyMapping.Category.MISC);
		KeyMapping swap = new KeyMapping("nls.test.localSwap", 70, KeyMapping.Category.MISC);
		set(Options.class, minecraft.options, "keyAttack", attack);
		set(Options.class, minecraft.options, "keyUse", use);
		set(Options.class, minecraft.options, "keySwapOffhand", swap);
		level.blocks.clear();
		PlaceholderWorld.setLocalMode(net.minecraft.world.level.GameType.CREATIVE);
		player.setPos(3.5, 80, .5);
		player.setXRot(0);
		player.setYRot(0);
		player.setOldPosAndRot();
		BlockPos target = new BlockPos(3, 81, 3);
		level.blocks.put(target, Blocks.STONE.defaultBlockState());
		KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(74));
		PlaceholderWorld.handleSafeKeybinds();
		check(level.getBlockState(target).isAir() && player.swinging, "Actual attack key performs a fresh raycast and local swing without a packet");
		attack.setDown(true);
		BlockPos turnedTarget = new BlockPos(1, 81, 0);
		level.blocks.put(turnedTarget, Blocks.STONE.defaultBlockState());
		player.setYRot(90);
		player.setOldPosAndRot();
		for (int frame = 0; frame < 90; frame++) PlaceholderWorld.handleSafeKeybinds();
		check(!level.getBlockState(turnedTarget).isAir(), "Holding attack while turning cannot mine another block, including repeated save frames");
		attack.setDown(false);
		KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(74));
		PlaceholderWorld.handleSafeKeybinds();
		check(level.getBlockState(turnedTarget).isAir(), "A fresh vanilla click can break the newly targeted block");
		PlaceholderWorld.setLocalMode(net.minecraft.world.level.GameType.SURVIVAL);
		player.setYRot(0);
		player.setOldPosAndRot();
		level.blocks.put(target, Blocks.STONE.defaultBlockState());
		player.getInventory().setSelectedSlot(0);
		player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 8));
		KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(75));
		PlaceholderWorld.handleSafeKeybinds();
		check(level.getBlockState(target.north()).is(Blocks.COBBLESTONE) && player.getMainHandItem().getCount() == 7, "Actual use key places against the pointed face");
		use.setDown(true);
		PlaceholderWorld.handleSafeKeybinds();
		check(player.getMainHandItem().getCount() == 7, "Held use cannot place twice inside the four-tick delay");
		PlaceholderInteraction interaction = (PlaceholderInteraction) get(PlaceholderWorld.class, null, "interaction");
		for (int i = 0; i < 4; i++) interaction.tick();
		PlaceholderWorld.handleSafeKeybinds();
		check(player.getMainHandItem().getCount() == 6, "Held use repeats after four ticks");
		use.setDown(false);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND, 5));
		KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(70));
		PlaceholderWorld.handleSafeKeybinds();
		check(player.getMainHandItem().is(Items.DIAMOND) && player.getOffhandItem().is(Items.COBBLESTONE), "Actual F key swaps without ServerboundPlayerActionPacket");
		check(minecraft.level == null && minecraft.player == null, "Action keys release the local binding");
	}

	private static void verifyWheel(final Minecraft minecraft, final LocalPlayer player) throws ReflectiveOperationException {
		set(Options.class, minecraft.options, "discreteMouseScroll", option(false));
		set(Options.class, minecraft.options, "mouseWheelSensitivity", option(1.0));
		set(Minecraft.class, minecraft, "window", allocate(com.mojang.blaze3d.platform.Window.class));
		set(Minecraft.class, minecraft, "framerateLimitTracker", allocate(com.mojang.blaze3d.platform.FramerateLimitTracker.class));
		net.minecraft.client.MouseHandler mouse = new net.minecraft.client.MouseHandler(minecraft);
		Method scroll = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onScroll", long.class, double.class, double.class);
		scroll.setAccessible(true);
		player.getInventory().setSelectedSlot(0);
		scroll.invoke(mouse, 0L, 0.0, .4);
		check(player.getInventory().getSelectedSlot() == 0, "Fractional wheel input accumulates normally");
		scroll.invoke(mouse, 0L, 0.0, .6);
		check(player.getInventory().getSelectedSlot() == 8, "Actual wheel callback cycles hotbar while the live player is absent");
		check(minecraft.level == null && minecraft.player == null, "Wheel callback restores absent session fields");
	}

	private static void verifyHandSwap(final Minecraft minecraft, final LocalPlayer player) throws ReflectiveOperationException {
		// With no item-model resource loader, reuse the same stacks so tick takes vanilla's instant
		// equality branch. It must still advance the hand heights rather than leave them frozen.
		ItemInHandRenderer hands = (ItemInHandRenderer) get(GameRenderer.class, minecraft.gameRenderer, "itemInHandRenderer");
		set(ItemInHandRenderer.class, hands, "mainHandItem", player.getMainHandItem());
		set(ItemInHandRenderer.class, hands, "offHandItem", player.getOffhandItem());
		PlaceholderWorld.tick();
		check((float) get(ItemInHandRenderer.class, hands, "offHandHeight") > 0, "Hand renderer equip animation advances during placeholder ticks");

		// Player.tick's main-hand snapshot: the renderer only raises a new stack gradually when the
		// swap ticker restarted, and vanilla restarts it from the copied previous stack. The fixture
		// has no item-model resolver, so keep the renderer's visible stacks equal to the player's;
		// the real lowering/raising heights are covered by the GPU regression.
		var inventory = player.getInventory();
		int selected = inventory.getSelectedSlot();
		ItemStack held = new ItemStack(Items.STONE, 4);
		inventory.setItem(selected, held);
		tickWithVisibleHands(hands, player, 12);
		check(player.getItemSwapScale(1.0F) == 1.0F, "A settled hand has a complete swap scale");
		held.setCount(3);
		tickWithVisibleHands(hands, player, 1);
		check(player.getItemSwapScale(1.0F) == 1.0F, "In-place count edits of the same item never restart the equip animation");
		inventory.setItem(selected, ItemStack.EMPTY); // hotbar to backpack: the hand is now empty
		tickWithVisibleHands(hands, player, 1);
		check(player.getItemSwapScale(1.0F) < .3F, "Emptying the hand restarts the swap ticker exactly as Player.tick does");
		tickWithVisibleHands(hands, player, 12);
		check(player.getItemSwapScale(1.0F) == 1.0F, "The restarted ticker advances again on later local ticks");
		inventory.setItem(selected, new ItemStack(Items.DIAMOND, 1));
		tickWithVisibleHands(hands, player, 1);
		check(player.getItemSwapScale(1.0F) < .3F, "Backpack to hand restarts the ticker too");
		check(minecraft.player == null && minecraft.level == null, "Hand tracking never leaks the binding");
		inventory.setItem(selected, ItemStack.EMPTY);
		tickWithVisibleHands(hands, player, 12);
	}

	private static void tickWithVisibleHands(final ItemInHandRenderer hands, final LocalPlayer player, final int ticks) throws ReflectiveOperationException {
		for (int i = 0; i < ticks; i++) {
			set(ItemInHandRenderer.class, hands, "mainHandItem", player.getMainHandItem());
			set(ItemInHandRenderer.class, hands, "offHandItem", player.getOffhandItem());
			PlaceholderWorld.tick();
		}
	}

	private static void verifyInventory(final Minecraft minecraft, final LocalPlayer player) throws ReflectiveOperationException {
		Gui originalGui = minecraft.gui;
		CapturingGui gui = allocate(CapturingGui.class);
		set(Minecraft.class, minecraft, "gui", gui);
		KeyMapping inventoryKey = new KeyMapping("nls.test.inventory", 69, KeyMapping.Category.MISC);
		set(Options.class, minecraft.options, "keyInventory", inventoryKey);
		player.getInventory().setSelectedSlot(0);
		player.inventoryMenu.setCarried(ItemStack.EMPTY);
		ItemStack original = new ItemStack(Items.DIAMOND, 7);
		player.getInventory().setItem(0, original);
		player.getInventory().setItem(1, ItemStack.EMPTY);
		try {
			KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(69));
			PlaceholderWorld.handleSafeKeybinds();
			check(gui.current instanceof LoadingInventoryScreen, "E opens a loading inventory even with no live player");
			LoadingInventoryScreen screen = (LoadingInventoryScreen) gui.current;
			check(AbstractContainerScreen.class.isAssignableFrom(screen.getClass()) && !screen.isPauseScreen(), "Local inventory uses vanilla presentation without pausing");
			check(screen.getMenu() == player.inventoryMenu && screen.getMenu().slots.size() == 46, "Inventory uses current local slots rather than stale display copies");
			set(Screen.class, screen, "minecraft", minecraft);
			screen.width = 176;
			screen.height = 166;
			invoke(AbstractContainerScreen.class, screen, "init");
			Method tickScreen = java.util.Arrays.stream(Gui.class.getDeclaredMethods())
				.filter(method -> method.getName().contains("nls$tickLocalInventory")).findFirst().orElseThrow();
			tickScreen.setAccessible(true);
			com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> originalTick = args -> {
				check(minecraft.player == player, "Vanilla final inventory tick sees the bound local player");
				((Screen) args[0]).tick();
				return null;
			};
			tickScreen.invoke(originalGui, screen, originalTick);
			check(minecraft.player == null, "Inventory tick binding does not extend into other GUI ticks");
			screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(9, 143,
				new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
			check(player.inventoryMenu.getCarried().getCount() == 7 && player.getInventory().getItem(0).isEmpty(), "Actual left slot click picks up the local stack without sending");
			check(PlaceholderWorld.bind(), "Bind local slot manipulation");
			try {
				PlaceholderInteraction.clickSlot(player, 37, 1, InventoryClick.PICKUP);
				check(player.getInventory().getItem(1).getCount() == 1 && player.inventoryMenu.getCarried().getCount() == 6, "Right slot click places one item");
				PlaceholderInteraction.clickSlot(player, 37, 0, InventoryClick.PICKUP);
				PlaceholderInteraction.clickSlot(player, 37, 40, InventoryClick.SWAP);
				check(player.getOffhandItem().getCount() == 7, "Inventory F swaps hovered slot with offhand locally");
				PlaceholderInteraction.clickSlot(player, 37, 0, InventoryClick.THROW);
				PlaceholderInteraction.clickSlot(player, -999, 0, InventoryClick.PICKUP);
			} finally {
				PlaceholderWorld.unbind();
			}
			screen.removed(); // must not send close/drop, even with an absent connection
			check(minecraft.player == null, "Inventory event binding is released");
			check(screen.keyPressed(new KeyEvent(69, 0, 0)) && gui.current == null, "E closes without a live connection");
			gui.current = screen;
			check(screen.keyPressed(new KeyEvent(256, 0, 0)) && gui.current == null, "Esc closes without a live connection");
			gui.current = screen;
			// Late teardown must close only the old snapshot, not overwrite a new session. Using an
			// incoming level also avoids GL engine detachment, which is outside this headless fixture.
			ClientLevel incoming = allocate(EmptyLevel.class);
			LocalPlayer incomingPlayer = allocate(LocalPlayer.class);
			minecraft.level = incoming;
			minecraft.player = incomingPlayer;
			minecraft.options.keyAttack.setDown(true);
			minecraft.options.keyUse.setDown(true);
				io.github.bingkkni.noloadingscreen.NoLoadingScreen.onLoginStart();
				check(LocalSkinPreloader.get(player.getUUID()) != null, "World teardown retains the preloaded skin for the next join");
			check(!minecraft.options.keyAttack.isDown() && !minecraft.options.keyUse.isDown(), "Held local clicks cannot continue as live-server actions");
			check(!PlaceholderInteraction.breakBlock(player, new BlockHitResult(Vec3.ZERO, Direction.UP, BlockPos.ZERO, false)), "Discarded player's local edits are rejected after login");
			check(gui.current == null && !PlaceholderWorld.active(), "Login closes the snapshot automatically");
			check(minecraft.level == incoming && minecraft.player == incomingPlayer, "Snapshot cleanup does not overwrite an incoming session");
			minecraft.level = null;
			minecraft.player = null;
		} finally {
			set(Minecraft.class, minecraft, "gui", originalGui);
		}
	}

	private static void verifySavingView(final Minecraft minecraft, final LocalPlayer player, final EmptyLevel level)
		throws ReflectiveOperationException {
		var saving = new net.minecraft.client.gui.screens.GenericMessageScreen(Gui.SAVING_LEVEL);
		check(SavingWorldView.isSavingScreen(saving), "Only the vanilla saving message is eligible");
		check(!SavingWorldView.isSavingScreen(new net.minecraft.client.gui.screens.GenericMessageScreen(net.minecraft.network.chat.Component.literal("Error")))
			&& !SavingWorldView.isSavingScreen(null), "Other message/error screens are not stripped");
		var mode = new MultiPlayerGameMode(minecraft, player.connection);
		minecraft.level = level;
		minecraft.player = player;
		minecraft.gameMode = mode;
		SavingWorldView.capture();
		check(get(SavingWorldView.class, null, "outgoing") == null, "Remote disconnect does not create a save view");
		set(Minecraft.class, minecraft, "singleplayerServer", allocate(net.minecraft.client.server.IntegratedServer.class));
		NoLoadingScreenConfig.get().enabled = false;
		SavingWorldView.capture();
		check(get(SavingWorldView.class, null, "outgoing") == null, "Disabled mod leaves local saving vanilla");
		NoLoadingScreenConfig.get().enabled = true;
		Method disconnect = java.util.Arrays.stream(Minecraft.class.getDeclaredMethods())
			.filter(m -> m.getName().contains("nls$disconnect") && m.getParameterCount() == 4).findFirst().orElseThrow();
		Method render = java.util.Arrays.stream(Minecraft.class.getDeclaredMethods())
			.filter(m -> m.getName().contains("nls$renderFrame") && m.getParameterCount() == 2).findFirst().orElseThrow();
		disconnect.setAccessible(true);
		render.setAccessible(true);
		for (boolean fail : new boolean[]{false, true}) {
			minecraft.level = level;
			minecraft.player = player;
			minecraft.gameMode = mode;
			set(Gui.class, minecraft.gui, "screen", saving);
			com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> originalDisconnect = args -> {
				try {
					check(args[0] == saving && args[1].equals(false) && args[2].equals(true), "Disconnect arguments are unchanged");
					Object snapshot = get(SavingWorldView.class, null, "outgoing");
					check(snapshot != null && get(snapshot.getClass(), snapshot, "level") == level, "Capture runs before vanilla teardown");
					minecraft.level = null;
					minecraft.gameMode = null;
					// Reuse the initialized scene fixture instead of attaching GPU engines headlessly.
					set(PlaceholderWorld.class, null, "level", level);
					set(PlaceholderWorld.class, null, "player", player);
					set(PlaceholderWorld.class, null, "gameMode", mode);
					set(PlaceholderWorld.class, null, "installed", true);
					SavingWorldView.install();
					check(SavingWorldView.visible() && io.github.bingkkni.noloadingscreen.LoadingWaitLoop.active(), "Saving owns an independent local input/animation clock");
					Method guiWrapper = java.util.Arrays.stream(Gui.class.getDeclaredMethods())
						.filter(m -> m.getName().contains("nls$returnToPlaceholder")).findFirst().orElseThrow();
					Method teardownGuard = java.util.Arrays.stream(Gui.class.getDeclaredMethods())
						.filter(m -> m.getName().contains("nls$allowLocalSavingUi")).findFirst().orElseThrow();
					guiWrapper.setAccessible(true);
					teardownGuard.setAccessible(true);
					guiWrapper.invoke(minecraft.gui, saving, (com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void>) screenArgs -> {
						check(screenArgs[0] == null && minecraft.level == level && minecraft.player == player,
							"Vanilla saving message is dismissed with a complete disconnected player binding");
						try {
							check(!(boolean) teardownGuard.invoke(minecraft.gui, true), "Bound saving scene alone may return to local gameplay UI");
							set(Gui.class, minecraft.gui, "screen", null);
						} catch (ReflectiveOperationException e) { throw new AssertionError(e); }
						return null;
					});
					check((boolean) teardownGuard.invoke(minecraft.gui, true), "Unbound vanilla teardown protection remains enabled");
					check(SavingWorldView.visible() && minecraft.gui.screen() == null, "Saving stays visible with input available, not a blocking screen");
					com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> originalRender = renderArgs -> {
						check(renderArgs[0].equals(true), "Save-loop renderFrame(false) draws the world without running a game tick");
						check(minecraft.level == level && minecraft.player == player && minecraft.gameMode == mode, "Save render binds all scene fields");
						BlockFeedbackVerification.seedSavingFeedback(player, level);
						return null;
					};
					render.invoke(minecraft, false, originalRender);
					check(minecraft.level == null && minecraft.gameMode == null, "Saving render restores the vanilla teardown state");
					// As in inventory teardown tests, an incoming sentinel avoids GPU detachment and
					// also proves cleanup cannot overwrite a level installed by another lifecycle hook.
					minecraft.level = allocate(EmptyLevel.class);
					if (fail) throw new IllegalStateException("save failure");
					return null;
				} catch (ReflectiveOperationException e) {
					throw new AssertionError(e);
				}
			};
			try {
				disconnect.invoke(minecraft, saving, false, true, originalDisconnect);
				check(!fail, "Failed save must not be swallowed");
			} catch (java.lang.reflect.InvocationTargetException e) {
				check(fail && e.getCause() instanceof IllegalStateException && "save failure".equals(e.getCause().getMessage()), "Save exceptions still reach vanilla's crash handling: " + e.getCause());
			}
			check(!SavingWorldView.visible() && !PlaceholderWorld.active() && !io.github.bingkkni.noloadingscreen.LoadingWaitLoop.active(),
				"Disconnect finally releases the saving scene AND local clock, including exceptions");
			check(get(SavingWorldView.class, null, "outgoing") == null,
				"No outgoing world/player is retained after saving");
			BlockFeedbackVerification.assertCleared();
		}
		set(Minecraft.class, minecraft, "singleplayerServer", null);
		minecraft.level = null;
		minecraft.player = null;
		minecraft.gameMode = null;
		set(Gui.class, minecraft.gui, "screen", null);
	}

	// Constructors for these two test doubles are intentionally never called: GL and a full client
	// world are outside this headless test. The LocalPlayer itself DOES run its real constructor.
	static final class EmptyLevel extends ClientLevel {
		boolean failQueries;
		boolean chunksMissing;
		boolean obstructed;
		boolean rejectEdits;
		Map<BlockPos, BlockState> blocks;
		Map<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> blockEntities;
		List<Entity> localEntities;
		@Override public <T extends Entity> List<T> getEntitiesOfClass(Class<T> type, AABB box, java.util.function.Predicate<? super T> filter) {
			return localEntities == null ? List.of() : localEntities.stream().filter(type::isInstance).map(type::cast)
				.filter(e -> e.getBoundingBox().intersects(box)).filter(filter).toList();
		}
		@Override public Entity getEntity(int id) { return localEntities == null ? null : localEntities.stream().filter(e -> e.getId() == id).findFirst().orElse(null); }
		@Override public void addEntity(Entity entity) { if (localEntities == null) localEntities = new java.util.ArrayList<>(); localEntities.add(entity); }
		@Override public void destroyBlockProgress(int id, BlockPos pos, int stage) {}
		private EmptyLevel() { super(null, null, null, null, 2, 2, null, false, 0, 63); }
		@Override public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean create) { return null; }
		@Override public boolean noCollision(Entity entity, AABB box) { return true; }
		@Override public net.minecraft.world.level.border.WorldBorder getWorldBorder() { return new net.minecraft.world.level.border.WorldBorder(); }
		@Override public List<net.minecraft.world.phys.shapes.VoxelShape> getEntityCollisions(Entity entity, AABB box) { return List.of(); }
		@Override public Iterable<net.minecraft.world.phys.shapes.VoxelShape> getBlockCollisions(Entity entity, AABB box) { return List.of(); }
		@Override public Optional<BlockPos> findSupportingBlock(Entity entity, AABB box) { return Optional.empty(); }
		@Override public BlockState getBlockState(BlockPos pos) {
			if (this.failQueries) throw new IllegalStateException("query failure");
			return this.blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
		}
		@Override public boolean hasChunk(int x, int z) { return !this.chunksMissing && Math.abs(x) <= 2 && Math.abs(z) <= 2; }
		// Level.isLoaded consults the chunk source this fixture never builds; answer with the same chunk set.
		@Override public boolean isLoaded(BlockPos pos) { return this.hasChunk(pos.getX() >> 4, pos.getZ() >> 4); }
		@Override public boolean setBlock(BlockPos pos, BlockState state, int flags, int limit) {
			if (this.rejectEdits) return false;
			this.blocks.put(pos.immutable(), state);
			return true;
		}
		@Override public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos) { return this.getBlockState(pos).getFluidState(); }
		@Override public boolean isUnobstructed(BlockState state, BlockPos pos, CollisionContext context) { return !this.obstructed; }
		@Override public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() { return net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS; }
		@Override public void playSeededSound(Entity player, double x, double y, double z,
			 net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound, net.minecraft.sounds.SoundSource source, float volume, float pitch, long seed) {}
		@Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
			return this.blockEntities == null ? null : this.blockEntities.get(pos);
		}
		@Override public int getMinY() { return -64; }
		@Override public int getHeight() { return 384; }
	}

	private static final class CapturingConnection extends net.minecraft.network.Connection {
		final List<net.minecraft.network.protocol.Packet<?>> sent = new java.util.ArrayList<>();
		CapturingConnection() { super(net.minecraft.network.protocol.PacketFlow.CLIENTBOUND); }
		@Override public void send(net.minecraft.network.protocol.Packet<?> packet) { this.sent.add(packet); }
	}

	private static final class CapturingGui extends Gui {
		Screen current;
		private CapturingGui() { super(null, null, null); }
		@Override public Screen screen() { return this.current; }
		@Override public void setScreen(Screen screen) { this.current = screen; }
	}

	private static final class TestDelta implements DeltaTracker {
		float alpha;
		@Override public float getGameTimeDeltaTicks() { return .2F; }
		@Override public float getGameTimeDeltaPartialTick(boolean ignoreFrozenGame) { return this.alpha; }
		@Override public float getRealtimeDeltaTicks() { return .2F; }
	}

	private static Object option(Object value) throws ReflectiveOperationException {
		Object option = allocate(OptionInstance.class);
		set(OptionInstance.class, option, "value", value);
		return option;
	}

	private static <T> T allocate(Class<T> type) throws ReflectiveOperationException {
		Field field = Unsafe.class.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
	}

	private static Object get(Class<?> type, Object instance, String name) throws ReflectiveOperationException {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(instance);
	}

	private static void set(Class<?> type, Object instance, String name, Object value) throws ReflectiveOperationException {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		field.set(instance, value);
	}

	private static Object invoke(Class<?> type, Object instance, String name) throws ReflectiveOperationException {
		Method method = type.getDeclaredMethod(name);
		method.setAccessible(true);
		return method.invoke(instance);
	}

	private static Object invoke(Class<?> type, Object instance, String name, Class<?> parameter, Object value) throws ReflectiveOperationException {
		Method method = type.getDeclaredMethod(name, parameter);
		method.setAccessible(true);
		return method.invoke(instance, value);
	}

	private static void check(boolean condition, String message) {
		assertions++;
		if (!condition) throw new AssertionError(message);
	}
}
