package io.github.bingkkni.noloadingscreen;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.bingkkni.noloadingscreen.gui.LoadingHud;
import io.github.bingkkni.noloadingscreen.mixin.ServerReconfigScreenAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives the whole mod. Two independent halves:
 *
 * <ol>
	 *   <li><b>The gate.</b> Vanilla holds you behind "Loading terrain" until the chunk section under
	 *       your feet has been <em>meshed</em>. We open its own gate immediately by advancing
	 *       {@code LevelLoadTracker} and invoking the public callback it already exposes. Screen close
	 *       and {@code ServerboundPlayerLoadedPacket} are still emitted by vanilla code, in vanilla
	 *       order — no packet is ever created, delayed or reordered by this mod.</li>
 *   <li><b>The gap.</b> Everything before the world exists at all — an integrated server booting, a
 *       proxy reconfiguring you onto another server — is time vanilla can only paint a menu over,
 *       because it has no level to render. {@link PlaceholderWorld} gives it one to render.</li>
 * </ol>
 */
public final class NoLoadingScreen implements ClientModInitializer {
	public static final String MOD_ID = "noloadingscreen";
	public static final Logger LOGGER = LoggerFactory.getLogger("NoLoadingScreen");

	/** When the current join started, or -1 when no join is in flight. */
	private static long loadStartMs = -1L;
	private static boolean gateReleased;

	private static boolean holdActive;
	private static Vec3 holdPos = Vec3.ZERO;

	/** Set while we call {@code loadingPacketsReceived} ourselves, so the timeline can tell the
	 *  server's real signal apart from our forced one. */
	private static boolean forcingLoadingPackets;

	private static long timelineStart = -1L;
	private static long timelineLast = -1L;

	/** Matches vanilla's own {@code CLIENT_WAIT_TIMEOUT_MS}: after this the overlay goes away
	 *  whatever happened, so a stuck load cannot leave permanent decoration on screen. */
	private static final long OVERLAY_TIMEOUT_MS = 30_000L;
	/** The tracker whose progress the HUD overlay is showing, or null when nothing is loading. */
	private static @Nullable LevelLoadTracker overlayTracker;
	/** Separate from {@link #loadStartMs}, which is cleared the moment the gate opens. */
	private static long overlayStartMs = -1L;
	private static boolean levelReadySeen;

	// --- Phase tracking ------------------------------------------------------------------------
	// One line per join in the log, and a line of text on screen. Both exist because "it hung" is
	// not a report anyone can act on, and neither of vanilla's waiting screens says which side is
	// busy.

	private static JoinPhase phase = JoinPhase.NONE;
	private static long phaseStartMs = -1L;
	private static long joinStartMs = -1L;
	private static final List<PhaseTime> phaseLog = new ArrayList<>();
	/** Kept after {@link #finishPhases()} has cleared the live list, for the chat summary. */
	private static final List<PhaseTime> lastJoinPhases = new ArrayList<>();

	private record PhaseTime(JoinPhase phase, long ms) {
	}

	// --- The screen we took over --------------------------------------------------------------
	// The "Reconfiguring" screen owns Connection#tick for the whole configuration phase, and its
	// Disconnect button is the only way out of a slow one (Esc does nothing there). So it is kept
	// and kept ticking even while unmounted — vanilla's logic, including the 600-tick button
	// delay, runs exactly as written. Only the painting is ours.
	private static @Nullable Screen suppressedConfigScreen;

	// The world we are about to leave, captured while it still exists and handed to
	// PlaceholderWorld once vanilla has finished pulling it out of Minecraft's fields.
	private static @Nullable ClientLevel adoptLevel;
	private static @Nullable LocalPlayer adoptPlayer;
	private static @Nullable MultiPlayerGameMode adoptGameMode;

	@Override
	public void onInitializeClient() {
		NoLoadingScreenConfig config = NoLoadingScreenConfig.get();
		LOGGER.info("NoLoadingScreen ready (enabled={})", config.enabled);
	}

	// --- Phases -------------------------------------------------------------------------------

	public static JoinPhase phase() {
		return phase;
	}

	public static long phaseElapsedMs() {
		return phaseStartMs < 0L ? 0L : Util.getMillis() - phaseStartMs;
	}

	private static void enterPhase(final JoinPhase next) {
		if (phase == next || !NoLoadingScreenConfig.get().enabled) {
			// With the mod off nothing is tracked and nothing is logged, so "disabled" really does
			// mean vanilla — including in the log file.
			return;
		}

		long now = Util.getMillis();
		if (phase != JoinPhase.NONE && phaseStartMs >= 0L) {
			phaseLog.add(new PhaseTime(phase, now - phaseStartMs));
		}
		if (joinStartMs < 0L) {
			joinStartMs = now;
			phaseLog.clear();
		}

		phase = next;
		phaseStartMs = now;
		markTimeline("阶段 -> " + next.name());
	}

	/** @return how long the whole join took, or -1 when no join was being tracked. */
	private static long finishPhases() {
		if (joinStartMs < 0L) {
			// Nothing was tracked, so whatever is still in there belongs to an earlier join and must
			// not be reported against this one.
			lastJoinPhases.clear();
			return -1L;
		}

		long now = Util.getMillis();
		if (phase != JoinPhase.NONE && phaseStartMs >= 0L) {
			phaseLog.add(new PhaseTime(phase, now - phaseStartMs));
		}
		long total = now - joinStartMs;
		LOGGER.info(
			"Join finished in {} ms [{}]",
			total,
			phaseLog.stream().map(p -> p.phase().name().toLowerCase() + " " + p.ms() + "ms").collect(Collectors.joining(", "))
		);

		lastJoinPhases.clear();
		lastJoinPhases.addAll(phaseLog);

		phase = JoinPhase.NONE;
		phaseStartMs = -1L;
		joinStartMs = -1L;
		phaseLog.clear();
		return total;
	}

	// --- Singleplayer: the integrated server is booting ----------------------------------------

	/**
	 * Called at the end of {@code Minecraft#doWorldLoad}, i.e. once the integrated server has been
	 * spun up and the local connection has been opened. Vanilla would have sat in a hard
	 * {@code while (!server.isReady())} loop before this point with the whole client frozen —
	 * {@code MinecraftMixin} removes that wait, so by the time we get here the client is running
	 * normally again and simply has no world yet.
	 */
	public static void onSingleplayerLoadStart(final RegistryAccess.Frozen registries) {
		enterPhase(JoinPhase.SERVER_BOOT);

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.screen() instanceof LevelLoadingScreen loadingScreen) {
			// The tracker Minecraft#doWorldLoad built is held by nothing else; the overlay needs it
			// to draw the same progress bar and chunk map the screen would have drawn.
			overlayTracker = LoadingHud.trackerOf(loadingScreen);
			overlayStartMs = Util.getMillis();
			levelReadySeen = false;
			LoadingHud.resetSmoothing();
		}

		NoLoadingScreenConfig config = NoLoadingScreenConfig.get();
		if (!config.enabled) {
			return;
		}

		if (PlaceholderWorld.synthesise(registries, null, null, null, new Vec3(0.5, 80.0, 0.5), 0.0F, 0.0F)) {
			markTimeline("NoLoadingScreen 装上占位世界，玩家可以自由转视角");
		}
	}

	// --- Multiplayer: the server put us back into the configuration phase ----------------------

	/**
	 * Called from {@code ClientPacketListener#handleConfigurationStart}, immediately before
	 * {@code Minecraft#clearClientLevel} throws the world away. This is the last moment the real
	 * level and player still exist, so it is where the camera state is taken.
	 */
	public static void onConfigurationStarting() {
		Minecraft minecraft = Minecraft.getInstance();
		beginTimeline("配置阶段开始 (重载配置中...)");
		enterPhase(JoinPhase.CONFIGURING);

		NoLoadingScreenConfig config = NoLoadingScreenConfig.get();
		if (!config.enabled) {
			return;
		}

		// This world is about to be thrown away, and it is complete: every chunk is loaded and
		// already meshed. Keeping it is both the best thing to look at and the cheapest, since it
		// means constructing nothing at all.
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level != null && player != null && !player.isDeadOrDying()) {
			adoptLevel = level;
			adoptPlayer = player;
			adoptGameMode = minecraft.gameMode;
		}
	}

	/**
	 * Asked from inside {@code Minecraft#clearClientLevel}: should the render engines stay pointed
	 * at the level being torn down? Detaching them releases every built section, and re-attaching
	 * would re-mesh the entire world — the exact cost this mod exists to avoid.
	 */
	public static boolean shouldKeepEnginesForAdoption() {
		return adoptLevel != null;
	}

	/**
	 * Called at the end of the same method, once vanilla has finished tearing the level down and
	 * has swapped the connection over to the configuration protocol.
	 */
	public static void onConfigurationStarted(final ClientPacketListener listener) {
		ClientLevel level = adoptLevel;
		LocalPlayer player = adoptPlayer;
		MultiPlayerGameMode gameMode = adoptGameMode;
		adoptLevel = null;
		adoptPlayer = null;
		adoptGameMode = null;

		if (level == null || player == null) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		Screen screen = minecraft.gui.screen();

		if (!PlaceholderWorld.adopt(level, player, gameMode)) {
			// The engines are still pointing at a level nobody owns any more; finish the teardown
			// that was suppressed on the way in.
			minecraft.levelExtractor.setLevel(null);
			minecraft.particleEngine.setLevel(null);
			minecraft.gameRenderer.setLevel(null);
			minecraft.setCameraEntity(null);
			return;
		}

		markTimeline("NoLoadingScreen 接管即将拆除的旧世界，撤下「重载配置中」界面");
		if (screen instanceof ServerReconfigScreen && minecraft.gui.screen() != screen) {
			// PlaceholderWorld unmounted it. We now owe it a tick every client tick: its own tick()
			// is what drives Connection#tick for the whole configuration phase.
			suppressedConfigScreen = screen;
		}
	}

	/**
	 * Called when a play-phase {@code ClientPacketListener} is constructed, which is the last thing
	 * the configuration phase does. Registries and tags are in; the server is now building the
	 * player. Those two waits look identical from the outside and fail for completely different
	 * reasons, so they get separate names.
	 */
	public static void onConfigurationFinished() {
		if (phase == JoinPhase.CONFIGURING || phase == JoinPhase.SERVER_BOOT) {
			enterPhase(JoinPhase.WAITING_WORLD);
		}
	}

	/** Called from {@code ClientPacketListener#handleLogin}: the real world is about to arrive. */
	public static void onLoginStart() {
		releaseSuppressedConfigScreen(false);
		// A snapshot that survived this far was never consumed, which can only mean the switch did
		// not go the way it usually does. Dropping it keeps a stale level out of the next teardown.
		adoptLevel = null;
		adoptPlayer = null;
		adoptGameMode = null;
		PlaceholderWorld.uninstall();
	}

	/**
	 * Called whenever the client throws a level away, including on the way to a new one. Note this
	 * fires from {@code clearClientLevel}'s head — i.e. immediately after
	 * {@link #onConfigurationStarting()} has taken its snapshot — so the snapshot is deliberately
	 * left alone here. {@link #onConfigurationStarted} consumes it a few statements later.
	 */
	public static void onLevelTornDown() {
		releaseSuppressedConfigScreen(false);
		PlaceholderWorld.uninstall();
	}

	/**
	 * Called when the connection is dropped outright. Distinct from {@link #onLevelTornDown()},
	 * which also fires on the way <em>into</em> a world: an abandoned join has to forget its phases,
	 * or the next join reports a total that includes the one that never finished.
	 */
	public static void onDisconnected() {
		onLevelTornDown();
		adoptLevel = null;
		adoptPlayer = null;
		adoptGameMode = null;
		phase = JoinPhase.NONE;
		phaseStartMs = -1L;
		joinStartMs = -1L;
		phaseLog.clear();
		loadStartMs = -1L;
		clearOverlay();
	}

	/**
	 * Vanilla enables the Disconnect button on {@code delayTicker == 600} — an exact equality, so a
	 * counter that is already past it never trips again.
	 */
	private static final int CONFIG_DISCONNECT_DELAY_TICKS = 600;

	private static void releaseSuppressedConfigScreen(final boolean remount) {
		Screen screen = suppressedConfigScreen;
		suppressedConfigScreen = null;
		if (!remount || !(screen instanceof ServerReconfigScreen old)) {
			return;
		}

		// A fresh instance rather than the same object: see ServerReconfigScreenAccessor.
		ServerReconfigScreenAccessor source = (ServerReconfigScreenAccessor) old;
		ServerReconfigScreen replacement = new ServerReconfigScreen(old.getTitle(), source.nls$connection());
		Minecraft.getInstance().gui.setScreen(replacement);
		// Carry the wait across, so the button unlocks on the schedule the player has already been
		// waiting through rather than starting its 30 seconds over.
		((ServerReconfigScreenAccessor) replacement)
			.nls$setDelayTicker(Math.min(source.nls$delayTicker(), CONFIG_DISCONNECT_DELAY_TICKS - 1));
	}

	/**
	 * Called when a placeholder frame threw and the placeholder had to be dropped mid-join. Its
	 * screen was taken down when it went up, so without this the player would be left facing a black
	 * window with nothing on it — strictly worse than the screen this mod set out to remove.
	 */
	public static void onPlaceholderFailed() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.screen() != null || minecraft.level != null) {
			return;
		}

		if (suppressedConfigScreen != null) {
			releaseSuppressedConfigScreen(true);
			return;
		}

		LevelLoadTracker tracker = overlayTracker;
		if (tracker != null) {
			minecraft.gui.setScreen(new LevelLoadingScreen(tracker, LevelLoadingScreen.Reason.OTHER));
		}
	}

	/**
	 * Called from the head of {@code Minecraft#tick}. Two jobs: keep the unmounted "Reconfiguring"
	 * screen ticking (it owns {@code Connection#tick} for this phase), and hand it back if the
	 * player asks for it — its Disconnect button is the only way out of a configuration phase that
	 * never ends, and a void you cannot leave is a worse trap than a screen you cannot skip.
	 */
	public static void tickPlaceholder() {
		Minecraft minecraft = Minecraft.getInstance();

		Screen screen = suppressedConfigScreen;
		if (screen != null) {
			if (minecraft.gui.screen() != null || minecraft.level != null) {
				// Something else took the screen, or the world arrived: not ours to drive any more.
				releaseSuppressedConfigScreen(false);
			} else if (InputConstants.isKeyDown(minecraft.getWindow(), InputConstants.KEY_ESCAPE)) {
				markTimeline("玩家按下 Esc，交还「重载配置中」界面");
				releaseSuppressedConfigScreen(true);
			} else {
				// Vanilla's own tick(), unchanged: it advances the 600-tick disconnect-button
				// delay and calls Connection#tick / handleDisconnection.
				screen.tick();
			}
		}

		PlaceholderWorld.tick();
	}

	/** True while the placeholder is standing in for a world that has not arrived yet. */
	public static boolean placeholderActive() {
		return PlaceholderWorld.active();
	}

	/** True while Esc would hand the "Reconfiguring" screen — and its Disconnect button — back. */
	public static boolean canRevealConfigScreen() {
		return suppressedConfigScreen != null;
	}

	// --- The join itself ----------------------------------------------------------------------

	/** Called from {@code LevelLoadTracker#startClientLoad}: a new world is about to stream in. */
	public static void onLoadStart(final LevelLoadTracker tracker) {
		loadStartMs = Util.getMillis();
		gateReleased = false;
		holdActive = false;
		overlayTracker = tracker;
		overlayStartMs = loadStartMs;
		levelReadySeen = false;
		LoadingHud.resetSmoothing();
		enterPhase(JoinPhase.RECEIVING_CHUNKS);
		markTimeline("等待服务端发送世界数据 (LevelLoadTracker.startClientLoad)");
	}

	/**
	 * Called at the end of {@code ClientPacketListener#startWaitingForNewLevel}. Singleplayer never
	 * goes through {@code Gui#setScreen} here: the screen {@code Minecraft#doWorldLoad} put up is
	 * still mounted, so vanilla just calls {@code update()} on it and the interception in
	 * {@code GuiMixin} never fires. By this point {@code setLevel} has run, so there is a world to
	 * look at and the screen can go.
	 */
	public static void onWaitingForNewLevel() {
		NoLoadingScreenConfig config = NoLoadingScreenConfig.get();
		if (!config.enabled) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null && minecraft.gui.screen() instanceof LevelLoadingScreen) {
			markTimeline("NoLoadingScreen 撤下沿用中的地形加载界面，画面交还给玩家");
			minecraft.gui.setScreen(null);
		}
	}

	/** Called from {@code LevelLoadTracker#loadingPacketsReceived}. */
	public static void onLoadingPacketsReceived() {
		if (!forcingLoadingPackets) {
			markTimeline("服务端已开始发送区块 (LEVEL_CHUNKS_LOAD_START)");
		}
	}

	/**
	 * Called from the head of {@code LevelLoadTracker#tickClientLoad}, i.e. immediately before
	 * vanilla evaluates its own state machine, so a release we make here takes effect in the very
	 * same tick.
	 */
	public static void onTrackerTick(final LevelLoadTracker tracker) {
		NoLoadingScreenConfig config = NoLoadingScreenConfig.get();
		if (!config.enabled) {
			return;
		}

		// The tracker starts in WaitingForServer and only exposes the release callback once the
		// server's LEVEL_CHUNKS_LOAD_START game event has moved it to WaitingForPlayerChunk. On a
		// busy server that first wait is dead time, so we drive the transition ourselves. Both calls
		// are public no-ops in every other state, which is what makes this safe.
		forcingLoadingPackets = true;
		try {
			tracker.loadingPacketsReceived();
		} finally {
			forcingLoadingPackets = false;
		}

		Runnable release = tracker.getPlayerCompiledSectionCallback();
		if (release == null) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;

		boolean chunkPresent = player != null && level != null && hasPlayerChunk(level, player);
		release.run();

		if (!gateReleased) {
			gateReleased = true;
			markTimeline("放行闸门 (instant, 玩家区块已到达=" + chunkPresent + ")");
			if (!chunkPresent && player != null) {
				holdActive = true;
				holdPos = player.position();
			}
		}
	}

	/** Called from {@code LevelLoadTracker#isLevelReady} the first time it answers {@code true}. */
	public static void onLevelReady() {
		levelReadySeen = true;

		if (loadStartMs < 0L) {
			return;
		}

		long terrainMs = Util.getMillis() - loadStartMs;
		loadStartMs = -1L;

		NoLoadingScreenConfig config = NoLoadingScreenConfig.get();
		endTimeline("进入世界 (发送 ServerboundPlayerLoadedPacket)");
		long joinMs = finishPhases();

		if (config.showJoinTime) {
			reportJoinTime(joinMs >= 0L ? joinMs : terrainMs);
		}
	}

	/**
	 * The same numbers the log always gets, put in chat as well.
	 *
	 * <p>The total is the whole join, matching the "Join finished in ... ms" line. Reporting the last
	 * {@code startClientLoad} instead — which is what this used to do — turns a 1.2 second server
	 * switch into "joined in 7 ms", because a proxy hands out several of them in a row and only the
	 * last one was still running when the gate opened. The terrain leg is the fallback for joins that
	 * never entered the phase tracker at all.
	 */
	private static void reportJoinTime(final long totalMs) {
		Minecraft.getInstance()
			.gui
			.chatListener()
			.handleSystemMessage(Component.translatable("noloadingscreen.message.joinTime", totalMs), false);

		if (lastJoinPhases.isEmpty()) {
			return;
		}

		MutableComponent breakdown = Component.empty();
		for (int i = 0; i < lastJoinPhases.size(); i++) {
			PhaseTime entry = lastJoinPhases.get(i);
			if (i > 0) {
				breakdown.append(" · ");
			}
			breakdown.append(
				Component.translatable(
					"noloadingscreen.message.joinPhase",
					Component.translatable(entry.phase().shortTranslationKey()),
					entry.ms()
				)
			);
		}

		Minecraft.getInstance()
			.gui
			.chatListener()
			.handleSystemMessage(Component.translatable("noloadingscreen.message.joinPhases", breakdown), false);
	}

	/**
	 * The tracker the HUD overlay should be showing, or null. Deliberately not tied to
	 * {@code isLevelReady()}: this mod opens that gate early on purpose, and an indicator that
	 * disappears before the ground shows up is worse than no indicator. It survives until the
	 * player's own chunk is actually there.
	 */
	public static @Nullable LevelLoadTracker overlayTracker() {
		LevelLoadTracker tracker = overlayTracker;
		if (tracker == null) {
			return null;
		}

		NoLoadingScreenConfig config = NoLoadingScreenConfig.get();
		if (!config.enabled || !config.loadingOverlay) {
			return null;
		}

		if (overlayStartMs >= 0L && Util.getMillis() - overlayStartMs > OVERLAY_TIMEOUT_MS) {
			return clearOverlay();
		}

		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null) {
			return null;
		}

		if (!PlaceholderWorld.active() && levelReadySeen && hasPlayerChunk(level, player)) {
			return clearOverlay();
		}

		return tracker;
	}

	/**
	 * Whether the overlay should draw at all this frame. The tracker only carries a progress bar and
	 * a chunk map on singleplayer — no packet carries either — so on a server the status line is the
	 * only thing there is, and it is the part that was missing.
	 */
	public static boolean shouldDrawOverlay() {
		NoLoadingScreenConfig config = NoLoadingScreenConfig.get();
		if (!config.enabled || !config.loadingOverlay) {
			return false;
		}
		return PlaceholderWorld.active() || overlayTracker() != null;
	}

	private static @Nullable LevelLoadTracker clearOverlay() {
		overlayTracker = null;
		overlayStartMs = -1L;
		return null;
	}

	/**
	 * Called from the tail of {@code LocalPlayer#aiStep}. While the player is standing in a chunk
	 * that has not arrived yet, every block around them is air, so vanilla physics would drop them
	 * through the world and the server would rubber-band them straight back.
	 */
	public static void onPlayerAiStep(final LocalPlayer player) {
		if (!holdActive) {
			return;
		}

		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || hasPlayerChunk(level, player)) {
			holdActive = false;
			markTimeline("玩家区块到达，恢复正常物理");
			return;
		}

		player.setDeltaMovement(Vec3.ZERO);
		player.setPos(holdPos.x, holdPos.y, holdPos.z);

		player.setOnGround(true);
		player.resetFallDistance();
	}

	// --- Diagnostics -----------------------------------------------------------------------
	// The join sequence spans several screens and two protocol phases, and which part is slow
	// depends entirely on the server. Rather than guess, every join writes a timeline to
	// latest.log; the chat summary is the part that is optional. Nothing is logged with the mod
	// off, so "disabled" really does mean vanilla, including in the log file.

	public static boolean timelineActive() {
		return timelineStart >= 0L;
	}

	public static void beginTimeline(final String label) {
		if (!NoLoadingScreenConfig.get().enabled) {
			return;
		}
		long now = Util.getMillis();
		timelineStart = now;
		timelineLast = now;
		LOGGER.info("[timeline] ===== {} =====", label);
	}

	public static void markTimeline(final String label) {
		if (!NoLoadingScreenConfig.get().enabled) {
			return;
		}
		if (timelineStart < 0L) {
			beginTimeline(label);
			return;
		}
		long now = Util.getMillis();
		LOGGER.info("[timeline] 累计 {} ms  (本段 +{} ms)  {}", now - timelineStart, now - timelineLast, label);
		timelineLast = now;
	}

	public static void endTimeline(final String label) {
		markTimeline(label);
		timelineStart = -1L;
		timelineLast = -1L;
	}

	private static boolean hasPlayerChunk(final ClientLevel level, final LocalPlayer player) {
		ChunkPos chunkPos = player.chunkPosition();
		return level.hasChunk(chunkPos.x(), chunkPos.z());
	}
}
