package io.github.bingkkni.noloadingscreen;

import io.github.bingkkni.noloadingscreen.platform.ClientUi;
import io.github.bingkkni.noloadingscreen.platform.LevelAccess;
import io.github.bingkkni.noloadingscreen.gui.LoadingHud;
import io.github.bingkkni.noloadingscreen.gui.LoadingPauseScreen;
import io.github.bingkkni.noloadingscreen.mixin.ServerReconfigScreenAccessor;
import io.github.bingkkni.noloadingscreen.mixin.ConnectScreenAccessor;
import io.github.bingkkni.noloadingscreen.gui.LoadingInventoryScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.Gui;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import io.github.bingkkni.noloadingscreen.platform.ClientRuntime;
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
	 *       order. Loading packet handling is time-sliced without creating, dropping or reordering
	 *       packets; responses still originate in vanilla handlers.</li>
 *   <li><b>The gap.</b> Everything before the world exists at all — an integrated server booting, a
 *       proxy reconfiguring you onto another server — is time vanilla can only paint a menu over,
 *       because it has no level to render. {@link PlaceholderWorld} gives it one to render.</li>
 * </ol>
 */
public final class NoLoadingScreen {
	public static final String MOD_ID = "noloadingscreen";
	public static final Logger LOGGER = LoggerFactory.getLogger("NoLoadingScreen");

	/** When the current join started, or -1 when no join is in flight. */
	private static long loadStartMs = -1L;
	private static boolean gateReleased;

	private static boolean holdActive;
	private static @Nullable LocalPlayer heldPlayer;
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

	// The configuration connection has no pendingConnection owner. Keep it alive independently
	// of the visible screen until login or disconnect, including while a menu or pack prompt is up.
	private static @Nullable ServerReconfigScreen suppressedConfigScreen;
	/** Initial remote login has a different owner from proxy reconfiguration. */
	private static @Nullable ConnectScreen suppressedConnectScreen;
	private static boolean connectPlaceholderAttempted;
	private static @Nullable Screen resourceScreen;
	private static boolean resourcePlaceholderAttempted;

	// The world we are about to leave, captured while it still exists and handed to
	// PlaceholderWorld once vanilla has finished pulling it out of Minecraft's fields.
	private static @Nullable OutgoingWorld outgoing;

	public static void initialize() {
		NoLoadingScreenConfig config = NoLoadingScreenConfig.get();
		LOGGER.info("NoLoadingScreen ready (enabled={})", config.enabled);
	}

	// --- Phases -------------------------------------------------------------------------------

	public static JoinPhase phase() {
		return phase;
	}

	public static long phaseElapsedMs() {
		return phaseStartMs < 0L ? 0L : ClientRuntime.millis() - phaseStartMs;
	}

	private static void enterPhase(final JoinPhase next) {
		if (phase == next || !NoLoadingScreenConfig.get().enabled) {
			// With the mod off nothing is tracked and nothing is logged, so "disabled" really does
			// mean vanilla — including in the log file.
			return;
		}

		long now = ClientRuntime.millis();
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

		long now = ClientRuntime.millis();
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

	// --- Before a WorldStem or remote registry synchronization exists --------------------------

	public static void onPreparingResources() {
		Minecraft minecraft = Minecraft.getInstance();
		if (!NoLoadingScreenConfig.get().enabled || minecraft.level != null) return;
		resourceScreen = ClientUi.screen(minecraft);
		resourcePlaceholderAttempted = false;
		enterPhase(JoinPhase.PREPARING_RESOURCES);
		tryResourcePlaceholder();
	}

	public static boolean preparingResources() {
		return resourceScreen != null && NoLoadingScreenConfig.get().enabled;
	}

	public static void tryResourcePlaceholder() {
		if (preparingResources() && !resourcePlaceholderAttempted && PlaceholderRegistries.ready() != null) {
			resourcePlaceholderAttempted = true;
			installEarlyPlaceholder(true);
		}
	}

	private static void installEarlyPlaceholder(final boolean dismissScreen) {
		RegistryAccess.Frozen registries = PlaceholderRegistries.ready();
		if (registries != null) PlaceholderWorld.synthesise(registries, null, null, null,
			new Vec3(0.5, 80, 0.5), 0, 0, dismissScreen);
	}

	/** Called only from ConnectScreen.tick, never from its off-thread status callback. */
	public static void onConnecting(final ConnectScreen screen) {
		Minecraft minecraft = Minecraft.getInstance();
		ConnectScreenAccessor access = (ConnectScreenAccessor) screen;
		Connection connection = access.nls$connection();
		if (!NoLoadingScreenConfig.get().enabled || minecraft.level != null || access.nls$aborted()
			|| connection == null || !connection.isConnected()
			|| (ClientUi.screen(minecraft) != screen && suppressedConnectScreen != screen)) return;
		if (suppressedConnectScreen != screen) {
			suppressedConnectScreen = screen;
			connectPlaceholderAttempted = false;
		}
		if (phase == JoinPhase.NONE) enterPhase(JoinPhase.CONNECTING);
		// Never construct a second play listener after configuration has already made the real one.
		if (!connectPlaceholderAttempted && !(connection.getPacketListener() instanceof ClientPacketListener)
			&& PlaceholderRegistries.ready() != null) {
			connectPlaceholderAttempted = true;
			// A pack prompt can arrive before the first encryption tick. Build behind it, never
			// dismiss it: only the actual waiting screen (or no screen) can be removed.
			installEarlyPlaceholder(ClientUi.screen(minecraft) == screen || ClientUi.screen(minecraft) == null);
		}
	}

	// --- Singleplayer: the integrated server is booting ----------------------------------------

	/**
	 * Called before doWorldLoad mounts its tracker screen and spins up the server.
	 * Keep an existing early scene; only a missing scene needs the now-available WorldStem.
	 */
	public static void onSingleplayerLoadStart(final RegistryAccess.Frozen registries, final LevelLoadingScreen screen) {
		if (!NoLoadingScreenConfig.get().enabled) return;
		resourceScreen = null;
		resourcePlaceholderAttempted = false;
		enterPhase(JoinPhase.SERVER_BOOT);
		// Keep the actual tracker even when GuiMixin never mounts its screen. An early private
		// world needs no registry swap: it has no gameplay and is discarded at the real login.
		overlayTracker = LoadingHud.trackerOf(screen);
		overlayStartMs = ClientRuntime.millis();
		levelReadySeen = false;
		LoadingHud.resetSmoothing();
		if (PlaceholderWorld.active()) {
			markTimeline("沿用准备资源阶段的占位世界，保留镜头与本地操作");
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
		beginTimeline("配置阶段开始 (重载配置中...)");
		enterPhase(JoinPhase.CONFIGURING);

		NoLoadingScreenConfig config = NoLoadingScreenConfig.get();
		if (!config.enabled) {
			return;
		}

		// This world is about to be thrown away, and it is complete: every chunk is loaded and
		// already meshed. Keeping it is both the best thing to look at and the cheapest, since it
		// means constructing nothing at all.
		outgoing = OutgoingWorld.capture();
	}

	/**
	 * Asked from inside {@code Minecraft#clearClientLevel}: should the render engines stay pointed
	 * at the level being torn down? Detaching them releases every built section, and re-attaching
	 * would re-mesh the entire world — the exact cost this mod exists to avoid.
	 */
	public static boolean shouldKeepEnginesForAdoption() {
		return outgoing != null;
	}

	/**
	 * Called at the end of the same method, once vanilla has finished tearing the level down and
	 * has swapped the connection over to the configuration protocol.
	 */
	public static void onConfigurationStarted() {
		OutgoingWorld snapshot = outgoing;
		outgoing = null;
		if (snapshot == null) return;

		Minecraft minecraft = Minecraft.getInstance();
		Screen screen = ClientUi.screen(minecraft);

		if (!snapshot.install(true)) {
			// The engines are still pointing at a level nobody owns any more; finish the teardown
			// that was suppressed on the way in.
			io.github.bingkkni.noloadingscreen.platform.SceneRenderer.setLevel(minecraft, null);
			minecraft.particleEngine.setLevel(null);
			minecraft.gameRenderer.setLevel(null);
			minecraft.setCameraEntity(null);
			return;
		}

		markTimeline("NoLoadingScreen 接管即将拆除的旧世界，撤下「重载配置中」界面");
		// adopt() drops the mounted screen while the placeholder is bound. Keep the original
		// instance captured above so its configuration connection continues to be ticked after the
		// screen is gone; reading ClientUi.screen(minecraft) here would always see null.
		if (screen instanceof ServerReconfigScreen reconfigScreen) {
			suppressedConfigScreen = reconfigScreen;
		}
	}

	/**
	 * Called when a play-phase {@code ClientPacketListener} is constructed, which is the last thing
	 * the configuration phase does. Registries and tags are in; the server is now building the
	 * player. Those two waits look identical from the outside and fail for completely different
	 * reasons, so they get separate names.
	 */
	public static void onConfigurationFinished() {
		if (phase == JoinPhase.CONFIGURING || phase == JoinPhase.SERVER_BOOT || phase == JoinPhase.CONNECTING) {
			enterPhase(JoinPhase.WAITING_WORLD);
		}
	}

	/** Called from {@code ClientPacketListener#handleLogin}: the real world is about to arrive. */
	public static void onLoginStart() {
		suppressedConnectScreen = null; // success: release ownership, do not close the connection
		connectPlaceholderAttempted = false;
		resourceScreen = null;
		releaseSuppressedConfigScreen(false);
		// A snapshot that survived this far was never consumed, which can only mean the switch did
		// not go the way it usually does. Dropping it keeps a stale level out of the next teardown.
		outgoing = null;
		DisconnectedWorldView.clear();
		PlaceholderWorld.uninstall();
	}

	/**
	 * Called whenever the client throws a level away, including on the way to a new one. Note this
	 * fires from {@code clearClientLevel}'s head — i.e. immediately after
	 * {@link #onConfigurationStarting()} has taken its snapshot — so the snapshot is deliberately
	 * left alone here. {@link #onConfigurationStarted} consumes it a few statements later.
	 */
	public static void onLevelTornDown() {
		releaseWaitingOwners();
		DisconnectedWorldView.clear();
		PlaceholderWorld.uninstall();
	}

	private static void releaseWaitingOwners() {
		resourceScreen = null;
		resourcePlaceholderAttempted = false;
		suppressedConnectScreen = null;
		connectPlaceholderAttempted = false;
		clearHold();
		releaseSuppressedConfigScreen(false);
	}

	/**
	 * Called when the connection is dropped outright. Distinct from {@link #onLevelTornDown()},
	 * which also fires on the way <em>into</em> a world: an abandoned join has to forget its phases,
	 * or the next join reports a total that includes the one that never finished.
	 */
	public static void onDisconnected() {
		onDisconnected(false);
	}

	/** A real disconnection still cleans up all join state; only KickWarn keeps the local scene. */
	public static void onDisconnected(final boolean keepPlaceholder) {
		ConnectScreen connecting = suppressedConnectScreen;
		if (connecting != null) abortConnecting(connecting);
		ServerReconfigScreen screen = suppressedConfigScreen;
		if (screen != null) {
			Connection connection = ((ServerReconfigScreenAccessor) screen).nls$connection();
			if (connection.isConnected()) {
				connection.disconnect(ConnectScreen.ABORT_CONNECTION);
			}
		}
		if (keepPlaceholder) releaseWaitingOwners();
		else onLevelTornDown();
		outgoing = null;
		phase = JoinPhase.NONE;
		phaseStartMs = -1L;
		joinStartMs = -1L;
		phaseLog.clear();
		lastJoinPhases.clear();
		loadStartMs = -1L;
		gateReleased = false;
		forcingLoadingPackets = false;
		levelReadySeen = false;
		timelineStart = -1L;
		timelineLast = -1L;
		clearOverlay();
		LoadingWork.reset();
	}

	/** Same lock and ordering as vanilla Cancel, including an in-flight connector. */
	private static void abortConnecting(final ConnectScreen screen) {
		ConnectScreenAccessor access = (ConnectScreenAccessor) screen;
		synchronized (screen) {
			access.nls$setAborted(true);
			var future = access.nls$channelFuture();
			if (future != null) {
				future.cancel(true);
				access.nls$setChannelFuture(null);
			}
			Connection connection = access.nls$connection();
			if (connection != null && connection.isConnected()) connection.disconnect(ConnectScreen.ABORT_CONNECTION);
		}
	}

	private static void releaseSuppressedConfigScreen(final boolean remount) {
		ServerReconfigScreen screen = suppressedConfigScreen;
		suppressedConfigScreen = null;
		if (!remount || screen == null) {
			return;
		}

		ServerReconfigScreen replacement = new ServerReconfigScreen(
			screen.getTitle(), ((ServerReconfigScreenAccessor) screen).nls$connection());
		ClientUi.setScreen(Minecraft.getInstance(), replacement);
		((ServerReconfigScreenAccessor) replacement).nls$disconnectButton().active = true;
		suppressedConfigScreen = replacement;
	}

	/**
	 * Called when a placeholder frame threw and the placeholder had to be dropped mid-join. Its
	 * screen was taken down when it went up, so without this the player would be left facing a black
	 * window with nothing on it — strictly worse than the screen this mod set out to remove.
	 */
	public static void onPlaceholderFailed() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null
			|| (ClientUi.screen(minecraft) != null && !(ClientUi.screen(minecraft) instanceof LoadingPauseScreen))) {
			return;
		}

		if (DisconnectedWorldView.active()) {
			Screen disconnected = DisconnectedWorldView.fallbackScreen();
			onDisconnected();
			ClientUi.setScreen(minecraft, disconnected);
			return;
		}
		if (suppressedConfigScreen != null) {
			releaseSuppressedConfigScreen(true);
			return;
		}
		Screen fallback = waitingScreen();
		if (fallback != null) ClientUi.setScreen(minecraft, fallback);
	}

	/** A prompt returning to a null parent after cosmetic failure must not land on the title screen. */
	public static @Nullable Screen waitingScreen() {
		if (DisconnectedWorldView.active()) return DisconnectedWorldView.fallbackScreen();
		if (SavingWorldView.saving()) return new net.minecraft.client.gui.screens.GenericMessageScreen(ClientUi.savingLevel());
		if (suppressedConnectScreen != null) return suppressedConnectScreen;
		if (suppressedConfigScreen != null) return suppressedConfigScreen;
		if (resourceScreen != null) return resourceScreen;
		return overlayTracker == null ? null : new LevelLoadingScreen(overlayTracker, LevelLoadingScreen.Reason.OTHER);
	}

	/** The connection remains ours until login or disconnect, not merely until a screen opens. */
	public static void tickPlaceholder() {
		Minecraft minecraft = Minecraft.getInstance();
		tryResourcePlaceholder();
		ConnectScreen connecting = suppressedConnectScreen;
		if (connecting != null && minecraft.level == null && ClientUi.screen(minecraft) != connecting) {
			connecting.tick(); // vanilla retains all login/configuration protocol and kick handling
		}
		ServerReconfigScreen screen = suppressedConfigScreen;
		if (screen != null) {
			if (minecraft.level != null) {
				releaseSuppressedConfigScreen(false);
			} else if (ClientUi.screen(minecraft) != screen) {
				// Reuse vanilla's owner, including immediate disconnection delivery. Its 600-tick
				// delay only enables the fallback button; it must never gate delivery of a kick.
				screen.tick();
			}
		}

		try {
			PlaceholderWorld.tick();
		} catch (Throwable failure) {
			// Collision/visual queries can be modified by other mods too. Keep their failures
			// inside the same cosmetic fallback boundary as placeholder rendering; connection
			// ticking above is deliberately outside this catch.
			PlaceholderWorld.onRenderFailed(failure);
		}
	}

	public static boolean openLoadingPauseScreen() {
		Minecraft minecraft = Minecraft.getInstance();
		if (!PlaceholderWorld.active()) return false;
		if (ClientUi.screen(minecraft) != null) return true;
		if (DisconnectedWorldView.active()) {
			ClientUi.setScreen(minecraft, new LoadingPauseScreen(Component.translatable("disconnect.lost"), DisconnectedWorldView::leave));
			return true;
		}
		ConnectScreen connecting = suppressedConnectScreen;
		if (connecting != null) {
			ConnectScreenAccessor access = (ConnectScreenAccessor) connecting;
			ClientUi.setScreen(minecraft, new LoadingPauseScreen(access.nls$status(), () -> {
				onDisconnected();
				ClientUi.setScreen(minecraft, access.nls$parent());
			}));
		} else if (suppressedConfigScreen != null) {
			ClientUi.setScreen(minecraft, new LoadingPauseScreen(suppressedConfigScreen.getTitle(),
				((ServerReconfigScreenAccessor) suppressedConfigScreen).nls$connection()));
		} else {
			// A synchronous save/resource wait cannot safely be cancelled by abandoning its stack.
			ClientUi.setScreen(minecraft, new LoadingPauseScreen(SavingWorldView.visible() ? ClientUi.savingLevel()
				: Component.translatable(phase.translationKey()), (Runnable) null));
		}
		return true;
	}

	public static void onScreenChanging(final @Nullable Screen screen) {
		if (screen instanceof ConnectScreen connecting) {
			if (connecting != suppressedConnectScreen) {
				onDisconnected();
				// Capture the owner when mounted, not at its first encryption tick: a pack
				// prompt can replace it before that tick ever runs. No placeholder starts yet.
				if (NoLoadingScreenConfig.get().enabled && !((ConnectScreenAccessor) connecting).nls$aborted()) {
					suppressedConnectScreen = connecting;
				}
			}
			return;
		}
		// Datapack failures, backup/low-disk warnings and cancellations retain vanilla ownership.
		if (resourceScreen != null && screen != null && screen != resourceScreen
			&& !(screen instanceof LevelLoadingScreen || screen instanceof LoadingInventoryScreen || screen instanceof LoadingPauseScreen || screen instanceof ChatScreen)) {
			onDisconnected();
			return;
		}
		if ((PlaceholderWorld.active() || suppressedConfigScreen != null || suppressedConnectScreen != null)
			&& (screen instanceof DisconnectedScreen || screen instanceof TitleScreen
				|| screen instanceof JoinMultiplayerScreen)) {
			onDisconnected();
		}
	}

	/** True while the placeholder is standing in for a world that has not arrived yet. */
	public static boolean placeholderActive() {
		return PlaceholderWorld.active();
	}

	/** Independent of HUD visibility: includes the real player's missing-chunk safety hold. */
	public static boolean isLoading() {
		return NoLoadingScreenConfig.get().enabled && (PlaceholderWorld.active() || phase != JoinPhase.NONE || holdActive);
	}

	public static Component blockedMessage(final boolean command) {
		return Component.translatable(command ? "noloadingscreen.message.commandBlocked" : "noloadingscreen.message.chatBlocked")
			.withStyle(ChatFormatting.RED);
	}

	public static boolean blockOutgoingMessage(final boolean command) {
		if (!isLoading()) return false;
		ClientUi.systemMessage(Minecraft.getInstance(), blockedMessage(command));
		return true;
	}

	/** True while Esc can open a loading menu with an immediately usable Disconnect button. */
	public static boolean canRevealConfigScreen() {
		return suppressedConfigScreen != null || suppressedConnectScreen != null;
	}

	// --- The join itself ----------------------------------------------------------------------

	/** Called from {@code LevelLoadTracker#startClientLoad}: a new world is about to stream in. */
	public static void onLoadStart(final LevelLoadTracker tracker) {
		LoadingWork.worldArriving();
		loadStartMs = ClientRuntime.millis();
		gateReleased = false;
		clearHold();
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
		if (minecraft.level != null && ClientUi.screen(minecraft) instanceof LevelLoadingScreen) {
			markTimeline("NoLoadingScreen 撤下沿用中的地形加载界面，画面交还给玩家");
			ClientUi.setScreen(minecraft, null);
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

		LevelAccess.release(tracker);
	}

	/** Called by the API-family hook at vanilla's readiness decision, before notification. */
	public static void onGateReleased() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;

		boolean chunkPresent = player != null && level != null && hasPlayerChunk(level, player);

		if (!gateReleased) {
			gateReleased = true;
			markTimeline("放行闸门 (instant, 玩家区块已到达=" + chunkPresent + ")");
			if (!chunkPresent && player != null) {
				holdActive = true;
				heldPlayer = player;
				holdPos = player.position();
				player.setOnGround(false);
			}
		}
	}

	/** Called from {@code LevelLoadTracker#isLevelReady} the first time it answers {@code true}. */
	public static void onLevelReady() {
		levelReadySeen = true;

		if (loadStartMs < 0L) {
			return;
		}

		long terrainMs = ClientRuntime.millis() - loadStartMs;
		loadStartMs = -1L;
		LoadingWork.worldArriving();

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
		ClientUi.systemMessage(Minecraft.getInstance(), Component.translatable("noloadingscreen.message.joinTime", totalMs));

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

		ClientUi.systemMessage(Minecraft.getInstance(), Component.translatable("noloadingscreen.message.joinPhases", breakdown));
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

		if (overlayStartMs >= 0L && ClientRuntime.millis() - overlayStartMs > OVERLAY_TIMEOUT_MS) {
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
		return !DisconnectedWorldView.active() && (PlaceholderWorld.active() || overlayTracker() != null);
	}

	private static @Nullable LevelLoadTracker clearOverlay() {
		overlayTracker = null;
		overlayStartMs = -1L;
		return null;
	}

	private static void clearHold() {
		holdActive = false;
		heldPlayer = null;
		holdPos = Vec3.ZERO;
	}

	/** The anchor belongs to one real player and follows every authoritative teleport. */
	public static void onPlayerPositionReceived() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (holdActive && player != null && player == heldPlayer) {
			holdPos = player.position();
		}
	}

	private static boolean shouldHold(final LocalPlayer player) {
		if (!holdActive) return false;
		ClientLevel level = Minecraft.getInstance().level;
		if (!NoLoadingScreenConfig.get().enabled || player != heldPlayer || level == null
			|| player.level() != level || player.isPassenger()) {
			clearHold();
			return false;
		}
		// Test the server's anchor, not a position physics may already have moved across a border.
		if (LevelAccess.hasChunk(level, holdPos)) {
			clearHold();
			markTimeline("玩家区块到达，恢复正常物理");
			return false;
		}
		return true;
	}

	/** An absent floor is not a landing: vanilla cancels ability flight when onGround is true. */
	public static void beforePlayerAiStep(final LocalPlayer player) {
		if (shouldHold(player)) player.setOnGround(false);
	}

	/** Undo only local movement, never yaw/pitch or the server's latest teleport. */
	public static void onPlayerAiStep(final LocalPlayer player) {
		if (!shouldHold(player)) return;
		player.setDeltaMovement(Vec3.ZERO);
		player.setPos(holdPos.x, holdPos.y, holdPos.z);
		player.setOnGround(false);
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
		long now = ClientRuntime.millis();
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
		long now = ClientRuntime.millis();
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
		return LevelAccess.hasChunk(level, chunkPos);
	}
}
