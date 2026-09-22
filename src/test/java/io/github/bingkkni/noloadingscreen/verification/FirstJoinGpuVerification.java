package io.github.bingkkni.noloadingscreen.verification;

import io.github.bingkkni.noloadingscreen.DisconnectedWorldView;
import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Opt-in real first join and save round trip, confined to a generated world in build/first-join-gpu. */
public final class FirstJoinGpuVerification {
	private enum Stage { BOOT, CREATE, JOIN, MEASURE, SYNC, KICK, OFFLINE, REOPEN, VERIFY_SAVE, DONE }
	private static Stage stage = Stage.BOOT;
	private static long start;
	private static long lastFrame;
	private static boolean nested;
	private static String folder;
	private static final List<Double> frames = new ArrayList<>();
	private static final AtomicBoolean markerApplied = new AtomicBoolean();
	private FirstJoinGpuVerification() {}

	public static void afterFrame(Minecraft client) throws Exception {
		if (nested || stage == Stage.DONE || !client.isGameLoadFinished() || client.gui.overlay() != null) return;
		long now = System.nanoTime();
		if (stage == Stage.MEASURE && lastFrame != 0) frames.add((now - lastFrame) / 1_000_000.0);
		lastFrame = now;
		nested = true;
		try {
			switch (stage) {
				case BOOT -> {
					if (start == 0) { start = now; return; }
					if (now - start < 2_000_000_000L) return;
					if (client.level != null) throw new AssertionError("Test may not start in a user world");
					stage = Stage.CREATE;
					CreateWorldScreen.openFresh(client, () -> { throw new AssertionError("World creation cancelled"); });
				}
				case CREATE -> {
					if (!(client.gui.screen() instanceof CreateWorldScreen screen)) return;
					screen.getUiState().setName("NLS isolated first join " + System.currentTimeMillis());
					screen.getUiState().setSeed("314159265");
					screen.getUiState().setGenerateStructures(false);
					screen.getUiState().setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
					folder = screen.getUiState().getTargetFolder();
					stage = Stage.JOIN;
					var create = CreateWorldScreen.class.getDeclaredMethod("onCreate");
					create.setAccessible(true);
					create.invoke(screen);
				}
				case JOIN -> {
					if (client.level == null || client.player == null || PlaceholderWorld.active()) return;
					stage = Stage.MEASURE;
					start = now;
					NoLoadingScreen.LOGGER.info("FirstJoinGpu: measuring first real world for 15 seconds");
				}
				case MEASURE -> {
					if (now - start < 15_000_000_000L) return;
					frames.sort(Double::compare);
					NoLoadingScreen.LOGGER.info("FirstJoinGpu: frames={} p50={}ms p95={}ms p99={}ms max={}ms over50ms={}",
						frames.size(), percentile(.5), percentile(.95), percentile(.99), percentile(1), frames.stream().filter(f -> f > 50).count());
					if (Boolean.getBoolean("nls.verify.profileOnly")) { finish(client); return; }
					var server = client.getSingleplayerServer();
					var id = client.player.getUUID();
					server.execute(() -> {
						var player = server.getPlayerList().getPlayer(id);
						player.getInventory().setSelectedSlot(0);
						player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
						player.setHealth(13);
						markerApplied.set(true);
					});
					stage = Stage.SYNC;
				}
				case SYNC -> {
					if (!markerApplied.get() || client.player.getHealth() != 13 || client.player.getInventory().getItem(0).getCount() != 7) return;
					NoLoadingScreenConfig.get().retainWorldOnKick = false; // explicit debug must not rewrite this setting
					client.player.connection.sendCommand("nlsdebug kick");
					stage = Stage.KICK;
				}
				case KICK -> {
					if (!DisconnectedWorldView.visible() || client.getSingleplayerServer() != null || client.level != null)
						throw new AssertionError("Debug kick did not save and retain the singleplayer scene");
					if (!Files.isRegularFile(Path.of("saves", folder, "level.dat"))) throw new AssertionError("World was not saved");
					if (NoLoadingScreenConfig.get().retainWorldOnKick) throw new AssertionError("Debug changed user configuration");
					if (!PlaceholderWorld.bind()) throw new AssertionError("Cannot bind debug scene");
					try {
						if (client.player.getHealth() != 13) throw new AssertionError("Final server health not retained");
						client.player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 64));
					} finally { PlaceholderWorld.unbind(); }
					start = now;
					stage = Stage.OFFLINE;
				}
				case OFFLINE -> {
					if (!DisconnectedWorldView.visible()) throw new AssertionError("Debug scene exited without user action");
					if (now - start < 3_000_000_000L) return;
					DisconnectedWorldView.leave();
					stage = Stage.REOPEN;
				}
				case REOPEN -> {
					stage = Stage.VERIFY_SAVE;
					client.createWorldOpenFlows().openWorld(folder, () -> { throw new AssertionError("Reopen cancelled"); });
				}
				case VERIFY_SAVE -> {
					if (client.player == null || client.level == null || PlaceholderWorld.active()
						|| !client.player.connection.hasClientLoaded() || client.player.tickCount < 40) return;
					ItemStack saved = client.player.getInventory().getItem(0);
					if (!saved.is(Items.DIAMOND) || saved.getCount() != 7) throw new AssertionError("Local inventory edits leaked into the saved world: " + saved);
					NoLoadingScreen.LOGGER.info("FirstJoinGpu: real debug save/reopen PASSED; saved 7 diamonds, discarded local 64, health snapshot 13");
					finish(client);
				}
				default -> throw new AssertionError(stage);
			}
		} finally { nested = false; }
	}

	private static double percentile(double value) { return frames.get(Math.min(frames.size() - 1, (int) (frames.size() * value))); }
	private static void finish(Minecraft client) {
		stage = Stage.DONE;
		client.level.disconnect(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
		client.disconnect(new TitleScreen(), false);
		NoLoadingScreen.LOGGER.info("FirstJoinGpuVerification PASSED (isolated generated world only)");
		client.stop();
	}
}
