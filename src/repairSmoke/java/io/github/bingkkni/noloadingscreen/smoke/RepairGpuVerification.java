package io.github.bingkkni.noloadingscreen.smoke;

import io.github.bingkkni.noloadingscreen.DisconnectedWorldView;
import io.github.bingkkni.noloadingscreen.InventoryClick;
import io.github.bingkkni.noloadingscreen.LoadingWaitLoop;
import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.PlaceholderInteraction;
import io.github.bingkkni.noloadingscreen.PlaceholderRegistries;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import io.github.bingkkni.noloadingscreen.SavingWorldView;
import io.github.bingkkni.noloadingscreen.gui.LoadingInventoryScreen;
import io.github.bingkkni.noloadingscreen.mixin.LivingEntityAccessor;
import io.github.bingkkni.noloadingscreen.platform.ClientUi;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import sun.misc.Unsafe;

/**
 * Real client, native loader, actual GPU frames and real ticks. Input arrives through the game's own
 * window event path ({@link RepairInput}), so the transformed dispatch chain is exercised. No user
 * save, server or socket:
 * the integrated-server wait polls a {@link SaveWaitProbe} that ends by itself.
 */
public final class RepairGpuVerification {
	private static final Unsafe UNSAFE = unsafe();
	private static final long STAGE_TIMEOUT_NS = 30_000_000_000L;
	private static final double MOUSE_STEP = 8.0;
	/** Vanilla: default sensitivity 0.5 gives factor 1.0, then Entity.turn scales by 0.15 degrees. */
	private static final float DEGREES_PER_STEP = (float) (MOUSE_STEP * 0.15);
	private static final int SETTLE_FRAMES = 8;

	private enum Stage { BOOT, SYNTHETIC, KICK, PICK_PRESS, PICK_HOLD, PICK_RELEASE, PICK_AGAIN, CREATIVE_INVENTORY, INVENTORY, OPTIONS, DONE }

	private static Stage stage = Stage.BOOT;
	private static int framesInStage;
	private static long stageStart;
	private static int assertions;
	private static boolean nested;
	private static long lastFrame;
	private static final List<Double> frameTimes = new ArrayList<>();
	private static LocalPlayer player;
	private static ClientLevel level;

	// Save wait observation, filled by duringSave from within the real synchronous save loop.
	private static int saveFrames;
	private static int injectedMoves;
	private static int cameraRegressions;
	private static int focusRepairs;
	/** Frames whose camera motion reversed the previous frame's direction: a twitch, not a walk. */
	private static int cameraReversals;
	private static double largestCameraStep;
	private static int largestStepFrame;
	private static Vec3 lastCameraPos;
	private static Vec3 lastCameraStep;
	private static float lastCameraYaw = Float.NaN;

	private RepairGpuVerification() {}

	/** Every frame of the normal loop; also the 1.21 save loop, whose frames are runTick(false). */
	public static void afterFrame(Minecraft client) throws Exception {
		if (nested) {
			duringSave(client);
			return;
		}
		if (stage == Stage.DONE || !client.isGameLoadFinished() || ClientUi.overlay(client) != null || PlaceholderRegistries.ready() == null) return;
		check(client.getSingleplayerServer() == null, "No save/server allowed in this isolated fixture");
		check(client.level == null && client.player == null, "Frame restored placeholder ownership");
		long now = System.nanoTime();
		if (lastFrame != 0) frameTimes.add((now - lastFrame) / 1_000_000.0);
		lastFrame = now;
		if (framesInStage++ == 0) stageStart = now;
		else if (now - stageStart > STAGE_TIMEOUT_NS) throw new AssertionError("Stage " + stage + " did not finish in time");
		switch (stage) {
			case BOOT -> {
				NoLoadingScreen.LOGGER.info("Repair GPU: {}", RepairFixtures.describeGpu());
				focus(client, true);
				RepairFixtures.bindItemComponents(PlaceholderRegistries.ready());
				long installStart = System.nanoTime();
				NoLoadingScreen.onPreparingResources();
				check(PlaceholderWorld.active(), "First synthetic world constructed");
				NoLoadingScreen.LOGGER.info("Repair cold placeholder install: {} ms", (System.nanoTime() - installStart) / 1_000_000.0);
				enter(Stage.SYNTHETIC);
			}
			case SYNTHETIC -> {
				check(PlaceholderWorld.active(), "Scene remains renderable");
				if (now - stageStart < 2_000_000_000L) return;
				promoteToLiveSession(client);
				verifySaveWait(client);
				enter(Stage.KICK);
			}
			case KICK -> {
				io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig.get().retainWorldOnKick = true;
				NoLoadingScreen.onPreparingResources();
				check(PlaceholderWorld.active(), "Second synthetic world constructed");
				promoteToLiveSession(client);
				runInsideTask(client, () -> client.disconnect(new DisconnectedScreen(new TitleScreen(),
					Component.literal("Isolated kick"), Component.literal("Repair regression")), false));
				check(DisconnectedWorldView.visible(), "Real disconnect retains KickWarn scene");
				check(!player.connection.getConnection().isConnected(), "Offline connection has no live channel");
				check(ClientUi.screen(client) == null, "KickWarn hides the disconnect screen");
				SandboxGpuChecks.run(client);
				RepairInput.middleButton(client, true);
				enter(Stage.PICK_PRESS);
			}
			case PICK_PRESS -> {
				if (framesInStage < SETTLE_FRAMES) return;
				check(player.getMainHandItem().is(Items.STONE), "Middle press picks the first aimed block through real ticks");
				RepairInput.mouseMove(client, 0); // vanilla ignores the first motion after a grab; it only records the cursor
				RepairInput.mouseMove(client, -90 / 0.15); // real mouse turn onto the dirt block while the button stays held
				enter(Stage.PICK_HOLD);
			}
			case PICK_HOLD -> {
				if (framesInStage < SETTLE_FRAMES) return;
				check(Math.abs(player.getYRot() + 90) < 2, "Mouse turned the held view onto the dirt block: yaw " + player.getYRot());
				check(player.getMainHandItem().is(Items.STONE), "Holding the middle button does not pick another block");
				RepairInput.middleButton(client, false);
				enter(Stage.PICK_RELEASE);
			}
			case PICK_RELEASE -> {
				if (framesInStage < SETTLE_FRAMES) return;
				check(player.getMainHandItem().is(Items.STONE), "Releasing the middle button does not pick the newly aimed block");
				RepairInput.middleButton(client, true);
				enter(Stage.PICK_AGAIN);
			}
			case PICK_AGAIN -> {
				if (framesInStage < SETTLE_FRAMES) return;
				check(player.getMainHandItem().is(Items.DIRT), "A new press picks the newly aimed block");
				RepairInput.middleButton(client, false);
				NoLoadingScreen.LOGGER.info("Repair pick press/hold/release PASSED");
				verifyHands(client);
				verifyFov(client);
				check(PlaceholderWorld.bind(), "Open local creative inventory");
				try { ClientUi.setScreen(client, new io.github.bingkkni.noloadingscreen.gui.LoadingCreativeInventoryScreen(player)); }
				finally { PlaceholderWorld.unbind(); }
				enter(Stage.CREATIVE_INVENTORY);
			}
			case CREATIVE_INVENTORY -> {
				if (now - stageStart < 1_000_000_000L) return;
				check(ClientUi.screen(client) instanceof io.github.bingkkni.noloadingscreen.gui.LoadingCreativeInventoryScreen, "Creative inventory frames survived");
				ClientUi.setScreen(client, null);
				PlaceholderWorld.setLocalMode(net.minecraft.world.level.GameType.SURVIVAL);
				check(PlaceholderWorld.bind(), "Open local inventory");
				try { ClientUi.setScreen(client, new LoadingInventoryScreen(player)); }
				finally { PlaceholderWorld.unbind(); }
				client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				enter(Stage.INVENTORY);
			}
			case INVENTORY -> {
				if (now - stageStart < 1_500_000_000L) return;
				check(ClientUi.screen(client) instanceof LoadingInventoryScreen, "Actual inventory frames survived");
				ClientUi.setScreen(client, null);
				client.options.setCameraType(CameraType.FIRST_PERSON);
				ClientUi.setScreen(client, new io.github.bingkkni.noloadingscreen.gui.NoLoadingScreenOptionsScreen(null));
				enter(Stage.OPTIONS);
			}
			case OPTIONS -> {
				if (now - stageStart < 1_000_000_000L) return;
				check(ClientUi.screen(client) instanceof io.github.bingkkni.noloadingscreen.gui.NoLoadingScreenOptionsScreen, "New options and timeout slider render in the real client");
				ClientUi.screen(client).onClose();
				frameTimes.sort(Double::compare);
				NoLoadingScreen.LOGGER.info("Repair GPU frames={} p50={}ms p95={}ms max={}ms (scripted transitions included, not gameplay FPS)",
					frameTimes.size(), percentile(.5), percentile(.95), percentile(1));
				NoLoadingScreen.onDisconnected();
				ClientUi.setScreen(client, new TitleScreen());
				check(!PlaceholderWorld.active() && !LoadingWaitLoop.active() && !DisconnectedWorldView.active(), "Cleanup leaves no scene/clock");
				stage = Stage.DONE;
				NoLoadingScreen.LOGGER.info("RepairGpuVerification PASSED: {} assertions; real save wait with live input, kick, pick, hands, FOV and inventory frames; no save/server", assertions);
				client.stop();
			}
			default -> throw new IllegalStateException(stage.name());
		}
	}

	/** Frames the 26.x save loop draws with renderFrame(false); they never return through runTick. */
	public static void afterWaitFrame(Minecraft client) throws Exception {
		if (nested) duringSave(client);
	}

	// --- Save wait: the real transformed disconnect with an integrated-server wait ----------------

	private static void verifySaveWait(Minecraft client) throws Exception {
		SaveWaitProbe probe = allocate(SaveWaitProbe.class);
		probe.arm(2_500_000_000L);
		set(Minecraft.class, client, "singleplayerServer", probe);
		set(Minecraft.class, client, "isLocalServer", true);
		float startYaw = player.getYRot();
		Vec3 startPos = player.position();
		int startTicks = player.tickCount;
		saveFrames = 0; injectedMoves = 0; cameraRegressions = 0; cameraReversals = 0; largestCameraStep = 0; largestStepFrame = 0; focusRepairs = 0;
		lastCameraPos = null; lastCameraStep = null; lastCameraYaw = Float.NaN;
		try {
			runInsideTask(client, () -> client.disconnect(new TitleScreen(), false));
		} finally {
			RepairInput.forwardKey(client, false);
			set(Minecraft.class, client, "singleplayerServer", null);
			set(Minecraft.class, client, "isLocalServer", false);
		}
		check(!SavingWorldView.saving() && !LoadingWaitLoop.active() && !PlaceholderWorld.active(), "Save wait releases scene and clock");
		check(client.level == null && client.player == null && client.gameMode == null, "Vanilla finished its field teardown");
		check(saveFrames >= 20, "The save wait drew interactive frames: " + saveFrames);
		check(player.tickCount - startTicks >= 20, "Local ticks advanced during the save wait: " + (player.tickCount - startTicks));
		// The first callback after a grab only records the cursor; the final one has no frame left.
		float expected = Math.max(0, injectedMoves - 2) * DEGREES_PER_STEP;
		float turned = player.getYRot() - startYaw;
		NoLoadingScreen.LOGGER.info("Repair save wait: frames={} injected moves={} yaw +{} (expected about {}), yaw regressions={}, camera reversals={}, largest camera step={} at frame {}, player moved {}, host focus repaired {} time(s)",
			saveFrames, injectedMoves, turned, expected, cameraRegressions, cameraReversals, largestCameraStep, largestStepFrame, player.position().distanceTo(startPos), focusRepairs);
		check(turned >= expected * 0.9F - 0.01F, "Mouse input polled by the frame path turns the view during the save wait");
		check(turned <= injectedMoves * DEGREES_PER_STEP + 0.01F, "No duplicated mouse input");
		check(player.position().distanceTo(startPos) > 0.5, "A key pressed during the save wait moves the player");
		check(cameraRegressions == 0, "Camera yaw never steps backwards while the mouse only turns one way");
		check(cameraReversals == 0, "Camera motion never reverses between save frames while one key is held");
		check(largestCameraStep < 0.3, "Camera position never teleports between save frames");
		check(player.getY() > 77.9, "The player stayed on the prepared floor");
		NoLoadingScreen.LOGGER.info("Repair save wait input PASSED");
	}

	private static void duringSave(Minecraft client) throws Exception {
		if (!SavingWorldView.visible()) return;
		saveFrames++;
		Camera camera = (Camera) get(GameRenderer.class, client.gameRenderer, "mainCamera");
		Vec3 position = camera.position();
		if (lastCameraPos != null) {
			Vec3 step = position.subtract(lastCameraPos);
			if (step.length() > largestCameraStep) { largestCameraStep = step.length(); largestStepFrame = saveFrames; }
			if (lastCameraStep != null && step.length() > 0.02 && lastCameraStep.length() > 0.02 && step.dot(lastCameraStep) < 0) cameraReversals++;
			lastCameraStep = step;
		}
		lastCameraPos = position;
		float yaw = player.getYRot();
		if (!Float.isNaN(lastCameraYaw) && yaw < lastCameraYaw - 1e-4F) cameraRegressions++;
		lastCameraYaw = yaw;
		if (saveFrames == 1) {
			RepairInput.forwardKey(client, true);
			return;
		}
		// Vanilla discards mouse deltas while the window is inactive; a host focus change would
		// otherwise read as lost input. Pin the fixture's focus and record how often it was needed.
		if (!client.isWindowActive()) { focusRepairs++; focus(client, true); }
		// Delivered exactly like an OS event polled while the frame path presents: outside our own poll.
		RepairInput.mouseMove(client, MOUSE_STEP);
		injectedMoves++;
	}

	// --- Kick scene checks -------------------------------------------------------------------------

	private static void verifyHands(Minecraft client) throws Exception {
		check(PlaceholderWorld.bind(), "Hand animation scope");
		try {
			Inventory inventory = player.getInventory();
			inventory.clearContent();
			inventory.setSelectedSlot(0);
			inventory.setItem(0, new ItemStack(Items.STONE, 4));
			LivingEntityAccessor tickers = (LivingEntityAccessor) player;
			for (int i = 0; i < 20; i++) PlaceholderWorld.tick();
			check(height(client) > .9F, "Held item starts equipped");
			PlaceholderInteraction.clickSlot(player, 36, 0, InventoryClick.QUICK_MOVE);
			PlaceholderWorld.tick();
			check(player.getMainHandItem().isEmpty(), "Hotbar item moved to backpack");
			check(tickers.nls$itemSwapTicker() == 0, "Emptying the hand restarts the vanilla swap ticker");
			check(height(client) < .9F, "Hotbar to backpack re-equips the empty hand");
			for (int i = 0; i < 10; i++) PlaceholderWorld.tick();
			check(height(client) > .9F, "The empty hand is raised again");
			PlaceholderInteraction.clickSlot(player, 9, 0, InventoryClick.QUICK_MOVE);
			PlaceholderWorld.tick();
			check(!player.getMainHandItem().isEmpty(), "Backpack item returns to hotbar");
			check(tickers.nls$itemSwapTicker() == 0 && height(client) < .9F, "Backpack to hand animates");
		} finally { PlaceholderWorld.unbind(); }
		NoLoadingScreen.LOGGER.info("Repair hand equip directions PASSED");
	}

	private static void verifyFov(Minecraft client) throws Exception {
		check(PlaceholderWorld.bind(), "FOV scope");
		try {
			Object owner = client.gameRenderer;
			try { owner.getClass().getDeclaredField("fovModifier"); }
			catch (NoSuchFieldException cameraOwnsFov) { owner = get(GameRenderer.class, owner, "mainCamera"); }
			client.options.keyUp.setDown(false);
			client.options.keySprint.setDown(false);
			for (int i = 0; i < 10; i++) PlaceholderWorld.tick();
			float standing = (float) get(owner.getClass(), owner, "fovModifier");
			focus(client, true);
			client.options.keyUp.setDown(true);
			client.options.keySprint.setDown(true);
			for (int i = 0; i < 5; i++) PlaceholderWorld.tick();
			check(player.isSprinting(), "Offline sprint enabled");
			check((float) get(owner.getClass(), owner, "fovModifier") > standing + .01F, "Offline sprint widens the interpolated FOV");
		} finally {
			client.options.keyUp.setDown(false);
			client.options.keySprint.setDown(false);
			PlaceholderWorld.unbind();
		}
		NoLoadingScreen.LOGGER.info("Repair offline sprint FOV PASSED");
	}

	// --- Scene preparation ------------------------------------------------------------------------

	/** Gives the synthetic void one real chunk with a floor and two target blocks, then makes it vanilla's live session. */
	private static void promoteToLiveSession(Minecraft client) throws Exception {
		check(PlaceholderWorld.bind(), "Prepare real client chunk");
		MultiPlayerGameMode mode;
		try {
			level = client.level;
			player = client.player;
			mode = client.gameMode;
			RepairFixtures.installChunk(level, new LevelChunk(level, new ChunkPos(0, 0)));
			for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) level.setBlock(new BlockPos(x, 77, z), Blocks.STONE.defaultBlockState(), 3);
			level.setBlock(new BlockPos(8, 79, 10), Blocks.STONE.defaultBlockState(), 3); // straight ahead at yaw 0
			level.setBlock(new BlockPos(10, 79, 8), Blocks.DIRT.defaultBlockState(), 3); // straight ahead at yaw -90
			player.setPos(8.5, 78, 8.5); // chunk centre: a turning walk stays on the floor
			player.setOnGround(true);
			aim(0);
		} finally { PlaceholderWorld.unbind(); }
		// The same handoff a real login performs: the disposable scene becomes vanilla's live world.
		set(PlaceholderWorld.class, null, "installed", false);
		set(PlaceholderWorld.class, null, "synthetic", false);
		set(PlaceholderWorld.class, null, "level", null);
		set(PlaceholderWorld.class, null, "player", null);
		set(PlaceholderWorld.class, null, "gameMode", null);
		client.level = level;
		client.player = player;
		client.gameMode = mode;
		NoLoadingScreen.onLoginStart();
		focus(client, true);
	}

	private static void aim(float yaw) {
		player.setYRot(yaw);
		player.setXRot(0);
		player.setOldPosAndRot();
	}

	/** A menu click runs inside a client task; the save wait then inherits that reentrancy. */
	private static void runInsideTask(Minecraft client, Runnable action) {
		nested = true;
		try { client.execute(action); }
		finally { nested = false; }
	}

	/** Movement input requires an active window; the isolated window is normally unfocused. */
	private static void focus(Minecraft client, boolean active) throws Exception {
		try { set(Minecraft.class, client, "windowActive", active); }
		catch (NoSuchFieldException focusOnWindow) { set(client.getWindow().getClass(), client.getWindow(), "focused", active); }
	}

	// --- Helpers ----------------------------------------------------------------------------------

	private static void enter(Stage next) {
		stage = next;
		framesInStage = 0;
	}

	private static float height(Minecraft client) throws Exception {
		return RepairFixtures.mainHandHeight(client, player);
	}

	private static double percentile(double p) {
		return frameTimes.isEmpty() ? 0 : frameTimes.get(Math.min(frameTimes.size() - 1, (int) (frameTimes.size() * p)));
	}

	private static void check(boolean value, String message) {
		assertions++;
		if (!value) throw new AssertionError(message);
	}

	private static Object get(Class<?> type, Object owner, String name) throws Exception {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(owner);
	}

	private static void set(Class<?> type, Object owner, String name, Object value) throws Exception {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		field.set(owner, value);
	}

	private static <T> T allocate(Class<T> type) throws Exception {
		return type.cast(UNSAFE.allocateInstance(type));
	}

	private static Unsafe unsafe() {
		try {
			Field field = Unsafe.class.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			return (Unsafe) field.get(null);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException(failure);
		}
	}
}
