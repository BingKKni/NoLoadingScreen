package io.github.bingkkni.noloadingscreen.smoke;

import io.github.bingkkni.noloadingscreen.*;
import io.github.bingkkni.noloadingscreen.compat.SodiumCompatibility;
import io.github.bingkkni.noloadingscreen.compat.SodiumProgramOwner;
import io.github.bingkkni.noloadingscreen.compat.SodiumShaderWarmup;
import io.github.bingkkni.noloadingscreen.gui.LoadingInventoryScreen;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/** Full client/GL verification in a dedicated build directory; never opens a save or server. */
public final class GpuSmokeVerification {
	public static int worldFrames;
	public static boolean failNext;
	public static boolean failureInjected;
	private static int stage;
	private static int nextStage;
	private static int recoveredFrames;
	private static int failuresBefore;
	private static List<Object> warmed = List.of();

	public static void afterFrame(Minecraft client) throws Exception {
		if (stage == 5 || !client.isGameLoadFinished() || client.getOverlay() != null || PlaceholderRegistries.ready() == null) return;
		check(client.getSingleplayerServer() == null, "Smoke test must never start an integrated server");
		check(client.level == null && client.player == null && client.gameMode == null, "Frame leaked placeholder ownership");
		if (stage == 0) {
			check(net.neoforged.fml.ModList.get().isLoaded("noloadingscreen"), "Normal FML mod construction did not finish");
			check(net.neoforged.fml.ModList.get().getModContainerById("noloadingscreen").orElseThrow()
				.getCustomExtension(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class).isPresent(),
				"NeoForge options-screen integration was not registered");
			if (SodiumCompatibility.supported()) {
				Object pending = field(SodiumShaderWarmup.class, null, "pending");
				check(pending != null, "Actual GL shader warmup did not retain programs");
				warmed = new ArrayList<>(((Map<?, ?>) field(pending.getClass(), pending, "owned")).values());
				check(warmed.size() == 3, "Actual SOLID/CUTOUT/TRANSLUCENT programs must survive shell deletion");
			}
			client.setScreen(new TitleScreen());
			NoLoadingScreen.onPreparingResources();
			check(PlaceholderWorld.active(), "Real synthetic scene construction failed");
			check(PlaceholderWorld.bind(), "Fluid transition fixture requires the local scene");
			try {
				var environment = (io.github.bingkkni.noloadingscreen.mixin.EntityAccessor) client.player;
				environment.nls$fluidTypeHeights().put(net.neoforged.neoforge.common.NeoForgeMod.WATER_TYPE.value(), 1.0);
				environment.nls$setTouchingWater(true);
				var velocity = client.player.getDeltaMovement();
				io.github.bingkkni.noloadingscreen.platform.PlayerEnvironment.sampleFluids(client.player);
				check(!client.player.isInWater() && environment.nls$fluidTypeHeights().isEmpty(), "Leaving water must clear adopted fluid state");
				check(velocity.equals(client.player.getDeltaMovement()), "Visual-only fluid sampling must not add currents");
			} finally { PlaceholderWorld.unbind(); }
			failuresBefore = (int) field(PlaceholderWorld.class, null, "renderFailures");
			if (SodiumCompatibility.supported()) {
				check(field(SodiumShaderWarmup.class, null, "pending") == null, "Live renderer did not consume shader ownership");
				Class<?> sodium = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
				Object world = sodium.getMethod("instance").invoke(null);
				Object manager = field(sodium, world, "renderSectionManager");
				Object renderer = field(manager.getClass(), manager, "chunkRenderer");
				check(((SodiumProgramOwner) renderer).nls$programs().values().containsAll(warmed), "Live backend must own the exact warmed program objects");
			}
			stage = 1; nextStage = worldFrames + 30;
			return;
		}
		if (stage <= 3) check(PlaceholderWorld.active(), "Synthetic scene failed before intentional failure");
		if (stage == 1 && worldFrames >= nextStage) {
			check(PlaceholderWorld.bind(), "Block feedback requires a scoped local world");
			try {
				PlaceholderBlockEffects.destroy(client.level, new net.minecraft.core.BlockPos(0, 80, 2),
					net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
				check(field(PlaceholderBlockEffects.class, null, "debris") != null, "Actual vanilla terrain particles were not captured locally");
			} finally { PlaceholderWorld.unbind(); }
			client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			stage = 2; nextStage = worldFrames + 30;
		} else if (stage == 2 && worldFrames >= nextStage) {
			check(PlaceholderWorld.bind(), "Inventory requires bound disposable player");
			try { client.setScreen(new LoadingInventoryScreen(client.player)); }
			finally { PlaceholderWorld.unbind(); }
			stage = 3; nextStage = worldFrames + 30;
		} else if (stage == 3 && worldFrames >= nextStage) {
			check(client.screen instanceof LoadingInventoryScreen, "Inventory did not remain local/visible");
			failNext = true;
			stage = 4;
		} else if (stage == 4) {
			check(failureInjected && !failNext, "Expected renderer exception hook never ran");
			check(!PlaceholderWorld.active(), "Recoverable frame did not release the scene");
			check((int) field(PlaceholderWorld.class, null, "renderFailures") == failuresBefore + 1, "Exactly one failure must reach the finite circuit breaker");
			if (++recoveredFrames < 10) return;
			check(client.screen != null, "Failed scene did not restore usable vanilla UI");
			check(field(PlaceholderBlockEffects.class, null, "debris") == null, "Frame recovery must clear local debris");
			if (!warmed.isEmpty()) {
				var valid = Class.forName("net.caffeinemc.mods.sodium.client.gl.GlObject").getDeclaredMethod("isHandleValid");
				valid.setAccessible(true);
				for (Object program : warmed) check(!(boolean) valid.invoke(program), "Native renderer must delete adopted GL programs on teardown");
			}
			NoLoadingScreen.onDisconnected();
			client.setScreen(new TitleScreen());
			stage = 5; // Disconnect fixtures render nested save/wait frames; do not restart this test.
			DisconnectHandoffVerification.run(client);
			NoLoadingScreen.LOGGER.info("NeoForgeGpuSmoke PASSED: synthetic world/inventory/debris/shaders/recovery plus transformed live-level disconnect handoffs; no save/server thread.");
			client.stop();
		}
	}

	private static Object field(Class<?> type, Object target, String name) throws Exception {
		Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target);
	}
	private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
