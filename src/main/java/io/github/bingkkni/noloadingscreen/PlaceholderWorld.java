package io.github.bingkkni.noloadingscreen;

import com.mojang.authlib.GameProfile;
import io.github.bingkkni.noloadingscreen.mixin.ClientCommonPacketListenerImplAccessor;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.ServerLinks;
import net.minecraft.stats.StatsCounter;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.clock.ClockNetworkState;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A world to look at while the real one does not exist yet.
 *
 * <p>Between "you asked to join" and "the server sent {@code ClientboundLoginPacket}" the client has
 * no {@code ClientLevel} and no {@code LocalPlayer} at all. {@code GameRenderer#render} skips the
 * entire level pass when {@code minecraft.level == null}, and {@code renderLevel} dereferences
 * {@code minecraft.player} without a null check — so during that window there is, by construction,
 * nothing on screen but a full-screen menu. That is the black frame and the "Loading terrain" /
 * "Reconfiguring" screens: not a stylistic choice, a consequence of those two fields being null.
 *
 * <p>So this class fills them in, in one of two ways.
 *
 * <h2>Adopting (server switch)</h2>
 *
 * <p>A proxy sending you to another server tears down a world that is <em>right there</em>, fully
 * built, with every chunk already meshed. {@link #adopt} keeps it instead: vanilla's teardown is
 * allowed to null {@code minecraft.level} and {@code player} as usual — which is what keeps every
 * tick, packet handler and other mod on the vanilla code path — but the render engines are left
 * pointing at the old level, and the old objects are handed back for the duration of each frame. You
 * see the place you just left, frozen, and can look around it. Nothing is constructed at all.
 *
 * <h2>Synthesising (singleplayer world load)</h2>
 *
 * <p>Starting a singleplayer world has no previous world to keep, so {@link #synthesise} builds an
 * ordinary, complete {@code ClientLevel} — empty chunks, plains biome, noon, clear sky — and a
 * {@code LocalPlayer} standing in it. This is deliberately the <em>only</em> path that constructs a
 * {@code ClientPacketListener}, because that constructor is hooked by other mods and has global side
 * effects; see {@link PlayAddonGuard}. It is safe here precisely because it runs when no play session
 * exists, which is the same reason there is nothing to adopt.
 *
 * <h2>Why this is safe to point a server at</h2>
 *
 * <ul>
 *   <li>Whichever listener the placeholder ends up holding, its {@link Connection} has <b>no
 *       channel</b>. {@code Connection#send} parks packets in a pending queue when not connected, so
 *       anything sent through it is discarded with the object and never written to a socket. For an
 *       adopted listener that means swapping its connection out (see
 *       {@link ClientCommonPacketListenerImplAccessor}); a synthesised one is born that way.</li>
 *   <li>The placeholder player is <b>never ticked</b>. It is not in anything that ticks entities, so
 *       {@code aiStep} and the position-reporting code never run once. Its movement is this class
 *       writing coordinates directly.</li>
 *   <li>It is thrown away whole when {@code handleLogin} arrives, and vanilla then builds the real
 *       player at the vanilla moment with the server's own position and rotation. Nothing the camera
 *       did can survive into the session, so the server has nothing to disagree with and no
 *       correction to send.</li>
 *   <li>Binding is per-frame: {@code minecraft.level} / {@code player} / {@code gameMode} are only
 *       assigned around a render frame and the two input calls that need a camera, then set back.</li>
 * </ul>
 */
public final class PlaceholderWorld {
	/** Small on purpose: {@code ClientChunkCache} allocates {@code (2r+1)^2} slots up front. */
	private static final int CHUNK_RADIUS = 2;
	private static final int SEA_LEVEL = 63;
	/** Noon, so the synthesised sky is bright rather than whatever tick 0 happens to mean. */
	private static final long NOON = 6000L;
	/** Anything but 0, which {@code Entity#getId} treats as "not assigned yet" and throws on. */
	private static final int PLACEHOLDER_ENTITY_ID = 1;
	/**
	 * After this many failed frames the placeholder stays out of the way for the rest of the
	 * session. One failure can be a fluke; three is the setup saying it does not agree with a
	 * stand-in world, and each further attempt costs a visibly broken frame on the way to the same
	 * fallback screen.
	 */
	private static final int MAX_RENDER_FAILURES = 3;

	private static @Nullable ClientLevel level;
	private static @Nullable LocalPlayer player;
	private static @Nullable MultiPlayerGameMode gameMode;
	private static boolean installed;
	/** True when we built the level ourselves and therefore own the engines pointing at it. */
	private static boolean synthetic;
	/** Set while {@link #build} runs, so hooks on the classes it constructs can ignore ours. */
	private static boolean building;
	/** Non-zero while the fields are handed to vanilla; a counter because renders can nest. */
	private static int bindDepth;
	/** Frames that threw with the placeholder up, counted for the life of the process. */
	private static int renderFailures;

	// What was in Minecraft's three fields before the outermost bind, put back by the matching
	// unbind. Saved rather than assumed null: vanilla's teardown clears them one at a time —
	// gameMode, then level, then player — so a bind landing in the middle of it must not swallow
	// whatever has not been cleared yet.
	private static @Nullable ClientLevel savedLevel;
	private static @Nullable LocalPlayer savedPlayer;
	private static @Nullable MultiPlayerGameMode savedGameMode;

	private PlaceholderWorld() {
	}

	public static boolean active() {
		return installed;
	}

	/** False once the placeholder has failed enough times to have earned being left switched off. */
	private static boolean allowed() {
		return renderFailures < MAX_RENDER_FAILURES;
	}

	/** True while the synthesised {@code ClientPacketListener} is being constructed. */
	public static boolean isBuilding() {
		return building;
	}

	// --- Adopting -----------------------------------------------------------------------------

	/**
	 * Takes over the world vanilla is in the middle of throwing away. Must be called after
	 * {@code clearClientLevel} has finished, with the render engines still pointing at {@code level}
	 * — {@code MinecraftMixin} arranges that by suppressing the one call that would have detached
	 * them.
	 */
	public static boolean adopt(
		final ClientLevel oldLevel,
		final LocalPlayer oldPlayer,
		final @Nullable MultiPlayerGameMode oldGameMode
	) {
		if (installed) {
			return true;
		}
		if (!allowed()) {
			return false;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			// The teardown did not happen after all; there is nothing to stand in for.
			return false;
		}
		if (oldPlayer.isDeadOrDying()) {
			// Gui#setScreen(null) answers a dying player with the death screen, which is not what
			// anyone wants to stare at during a server switch.
			return false;
		}

		level = oldLevel;
		player = oldPlayer;
		// clearClientLevel nulls Minecraft#gameMode a few statements before Minecraft#level, so the
		// captured value can be null depending on exactly where the snapshot was taken. It is not
		// optional: GameRenderer#renderItemInHand dereferences it on every first-person frame with
		// no null check, so a null here is a crash a second later, not a missing feature.
		gameMode = oldGameMode != null ? oldGameMode : new MultiPlayerGameMode(minecraft, oldPlayer.connection);
		synthetic = false;
		installed = true;

		try {
			// The listener is kept, but its line to the server is cut first: see the accessor.
			((ClientCommonPacketListenerImplAccessor) oldPlayer.connection).nls$setConnection(deadConnection());

			// clearClientLevel calls gameRenderer.resetData(), which resets the camera; these two put
			// it back. Deliberately *not* levelExtractor.setLevel — that would discard every built
			// section and re-mesh the whole world, which is the exact cost this mod exists to avoid.
			minecraft.gameRenderer.setLevel(oldLevel);
			minecraft.setCameraEntity(oldPlayer);
			dropScreen(minecraft);
			// One line per server switch. The last crash was a null gameMode reaching the renderer,
			// and this says outright whether the snapshot carried one — worth more than guessing
			// from a stack trace on a machine that cannot run the game.
			NoLoadingScreen.LOGGER.info("Adopted the outgoing world for the placeholder (captured gameMode={})", oldGameMode != null);
		} catch (Throwable t) {
			NoLoadingScreen.LOGGER.warn("Could not adopt the outgoing world, falling back to vanilla", t);
			uninstall();
			return false;
		}

		return true;
	}

	// --- Synthesising -------------------------------------------------------------------------

	/**
	 * Builds a world from nothing. Returns false — leaving vanilla's own screen up — if anything at
	 * all goes wrong, because a loading screen is a much better outcome than a crash on the way into
	 * a world.
	 *
	 * @param registries where blocks, biomes and dimension types come from. Singleplayer takes these
	 *                   from the {@code WorldStem}, the only registry set that exists that early.
	 */
	public static boolean synthesise(
		final RegistryAccess.Frozen registries,
		final @Nullable FeatureFlagSet features,
		final @Nullable Holder<DimensionType> dimensionType,
		final @Nullable ResourceKey<Level> dimension,
		final Vec3 pos,
		final float yRot,
		final float xRot
	) {
		if (installed) {
			return true;
		}
		if (!allowed()) {
			return false;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			return false;
		}

		PlayAddonGuard guard = PlayAddonGuard.acquire();
		if (guard == null) {
			// Refused: building a listener would leave state behind that cannot be put back.
			return false;
		}

		building = true;
		try (guard) {
			build(minecraft, registries, features, dimensionType, dimension, pos, yRot, xRot);
		} catch (Throwable t) {
			NoLoadingScreen.LOGGER.warn("Could not build the placeholder world, keeping the vanilla loading screen", t);
			level = null;
			player = null;
			gameMode = null;
			return false;
		} finally {
			building = false;
		}

		synthetic = true;
		installed = true;

		try {
			// Ours to attach, and ours to keep attached until uninstall(): re-pointing these per
			// frame would rebuild the section graph sixty times a second.
			minecraft.levelExtractor.setLevel(level);
			minecraft.particleEngine.setLevel(level);
			minecraft.gameRenderer.setLevel(level);
			minecraft.setCameraEntity(player);
			dropScreen(minecraft);
		} catch (Throwable t) {
			NoLoadingScreen.LOGGER.warn("Could not install the placeholder world, falling back to vanilla", t);
			uninstall();
			return false;
		}

		return true;
	}

	private static void build(
		final Minecraft minecraft,
		final RegistryAccess.Frozen registries,
		final @Nullable FeatureFlagSet features,
		final @Nullable Holder<DimensionType> dimensionType,
		final @Nullable ResourceKey<Level> dimension,
		final Vec3 pos,
		final float yRot,
		final float xRot
	) {
		FeatureFlagSet enabledFeatures = features != null ? features : FeatureFlags.DEFAULT_FLAGS;
		Holder<DimensionType> type = dimensionType != null
			? dimensionType
			: registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD);

		// Built from the local session rather than Minecraft#getGameProfile(), which joins on the
		// profile future — a blocking network call is the last thing a join needs.
		GameProfile profile = new GameProfile(minecraft.getUser().getProfileId(), minecraft.getUser().getName());
		CommonListenerCookie cookie = new CommonListenerCookie(
			new LevelLoadTracker(),
			profile,
			minecraft.getTelemetryManager().createWorldSessionManager(false, null, null, UUID.randomUUID()),
			registries,
			enabledFeatures,
			"noloadingscreen:placeholder",
			null,
			null,
			Map.of(),
			null,
			Map.of(),
			ServerLinks.EMPTY,
			Map.of(),
			true
		);
		ClientPacketListener listener = new ClientPacketListener(minecraft, deadConnection(), cookie);

		ClientLevel.ClientLevelData levelData = new ClientLevel.ClientLevelData(Difficulty.NORMAL, false, false);
		levelData.setGameTime(NOON);
		level = new ClientLevel(
			listener,
			levelData,
			dimension != null ? dimension : Level.OVERWORLD,
			type,
			CHUNK_RADIUS,
			CHUNK_RADIUS,
			minecraft.levelExtractor,
			false,
			0L,
			SEA_LEVEL
		);
		setNoon(listener, registries);

		// setLocalMode() is deliberately not called: it reaches straight through to
		// minecraft.player.getAbilities(), which is null at this point, and the field it would set
		// already defaults to GameType.DEFAULT_MODE — survival, which is what we want anyway.
		gameMode = new MultiPlayerGameMode(minecraft, listener);

		LocalPlayer localPlayer = gameMode.createPlayer(level, new StatsCounter(), new ClientRecipeBook());
		localPlayer.input = new KeyboardInput(minecraft.options);
		// Entity#id starts at 0 and Entity#getId throws until it is assigned — which ClientLevel
		// #addEntity does immediately. Vanilla assigns the server's id in handleLogin just before
		// the same call; this level is private, so any non-zero value will do.
		localPlayer.setId(PLACEHOLDER_ENTITY_ID);
		localPlayer.snapTo(pos.x, pos.y, pos.z, yRot, xRot);
		localPlayer.setOldPosAndRot();
		level.addEntity(localPlayer);
		player = localPlayer;
	}

	/** A connection with no channel; {@code send} parks packets in a queue nobody drains. */
	private static Connection deadConnection() {
		return new Connection(PacketFlow.CLIENTBOUND);
	}

	/**
	 * Since 26.2 the time of day is carried by {@code WorldClock}s rather than a field on the level,
	 * and a client clock that was never told anything reads zero — midnight. Best effort: the sky is
	 * cosmetic, so a registry that does not carry the overworld clock just gets whatever it defaults
	 * to rather than failing the whole placeholder.
	 */
	private static void setNoon(final ClientPacketListener listener, final RegistryAccess.Frozen registries) {
		try {
			Holder<WorldClock> clock = registries.lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD);
			listener.clockManager().handleUpdates(NOON, Map.of(clock, new ClockNetworkState(NOON, 0.0F, 1.0F)));
		} catch (RuntimeException e) {
			NoLoadingScreen.LOGGER.debug("Placeholder world keeps the default time of day", e);
		}
	}

	// --- Lifecycle ----------------------------------------------------------------------------

	/**
	 * Only now can the loading screen go: {@code Gui#setScreen(null)} answers a null level with the
	 * title screen, so it has to see the placeholder.
	 */
	private static void dropScreen(final Minecraft minecraft) {
		if (bind()) {
			try {
				if (minecraft.gui.screen() != null) {
					minecraft.gui.setScreen(null);
				}
			} finally {
				unbind();
			}
		}
	}

	/**
	 * Drops the placeholder. The render engines are deliberately not detached here: every caller is
	 * a point where vanilla re-points them in the next few statements — {@code setLevel} to the new
	 * world, {@code disconnect} to null — and detaching in between would throw away section meshes
	 * only to rebuild them.
	 */
	public static void uninstall() {
		if (!installed) {
			return;
		}

		// Never leave a binding behind: the next thing to run is usually Minecraft#setLevel.
		releaseAll();

		installed = false;
		synthetic = false;
		Minecraft.getInstance().setCameraEntity(null);

		level = null;
		player = null;
		gameMode = null;
	}

	/** True when this placeholder owns the engine attachments, i.e. it built its own level. */
	public static boolean isSynthetic() {
		return synthetic;
	}

	/**
	 * Called when a frame that had the placeholder bound threw. This is a cosmetic feature standing
	 * in front of a join, so it does not get to take the game down with it: the placeholder is
	 * dropped, the engines are put back where the suppressed teardown would have left them, and
	 * {@code NoLoadingScreen} restores a real screen so the player is not left facing a black window.
	 */
	public static void onRenderFailed(final Throwable failure) {
		Minecraft minecraft = Minecraft.getInstance();
		// Everything needed to tell "the placeholder was malformed" apart from "vanilla was midway
		// through swapping worlds" apart from "some other mod did not like the stand-in", none of
		// which the stack trace on its own distinguishes. Written before the state is torn down.
		NoLoadingScreen.LOGGER.error(
			"Placeholder world failed to render; dropping it and falling back to vanilla "
				+ "(mode={}, bindDepth={}, phase={}, bound level/player/gameMode={}/{}/{}, placeholder level/player/gameMode={}/{}/{})",
			synthetic ? "synthesised" : "adopted",
			bindDepth,
			NoLoadingScreen.phase(),
			minecraft.level != null,
			minecraft.player != null,
			minecraft.gameMode != null,
			level != null,
			player != null,
			gameMode != null,
			failure
		);

		boolean wasAdopted = installed && !synthetic;
		uninstall();

		if (++renderFailures >= MAX_RENDER_FAILURES) {
			NoLoadingScreen.LOGGER.error(
				"Placeholder world has failed {} times; leaving it off for the rest of this session. "
					+ "Joins fall back to the vanilla loading screen, the early readiness gate is unaffected.",
				renderFailures
			);
		}

		if (wasAdopted) {
			// The teardown this mod suppressed never ran, so the engines are still pointing at a
			// level nobody owns. Finish it now.
			minecraft.levelExtractor.setLevel(null);
			minecraft.particleEngine.setLevel(null);
			minecraft.gameRenderer.setLevel(null);
		}
		NoLoadingScreen.onPlaceholderFailed();
	}

	/**
	 * Hands the placeholder to vanilla for the duration of one call. Returns whether it took, so the
	 * caller knows if it owes an {@link #unbind()}.
	 *
	 * <p>All three fields are written on <em>every</em> successful call, nested ones included. That
	 * is the one invariant this class cannot afford to get wrong: vanilla reads the three as a set
	 * and null-checks only the level. {@code GameRenderer#render} gates the level pass on
	 * {@code minecraft.level != null} and {@code renderLevel} then dereferences
	 * {@code minecraft.player} on its first line, so a frame that starts with the level bound and
	 * the player not is a guaranteed {@code NullPointerException} — which is exactly the failure
	 * this used to produce when the earlier version took a "already bound, nothing to do" shortcut
	 * and something had disturbed the fields in the meantime.
	 */
	public static boolean bind() {
		if (!installed) {
			return false;
		}

		ClientLevel placeholderLevel = level;
		LocalPlayer placeholderPlayer = player;
		MultiPlayerGameMode placeholderGameMode = gameMode;
		if (placeholderLevel == null || placeholderPlayer == null || placeholderGameMode == null) {
			// All three or none, for the reason above.
			NoLoadingScreen.LOGGER.error("Placeholder world is incomplete, dropping it");
			uninstall();
			return false;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (bindDepth == 0) {
			if (minecraft.level != null) {
				// A real world is on screen; there is nothing to stand in for.
				return false;
			}
			savedLevel = minecraft.level;
			savedPlayer = minecraft.player;
			savedGameMode = minecraft.gameMode;
		}

		minecraft.level = placeholderLevel;
		minecraft.player = placeholderPlayer;
		minecraft.gameMode = placeholderGameMode;
		bindDepth++;
		return true;
	}

	public static void unbind() {
		if (bindDepth == 0) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (--bindDepth > 0) {
			// An outer scope still owns the binding, so this is a handback to it, not a teardown.
			// Re-asserted rather than left alone, in case the inner call disturbed the fields.
			if (level != null && player != null && gameMode != null) {
				minecraft.level = level;
				minecraft.player = player;
				minecraft.gameMode = gameMode;
			}
			return;
		}

		// Only if the placeholder is still what is mounted. Vanilla replaces these itself on the way
		// into the real world (`setLevel`, then the new player a few statements later), and putting
		// our saved nulls back over that would throw the real world away.
		if (level != null && minecraft.level == level) {
			minecraft.level = savedLevel;
			minecraft.player = savedPlayer;
			minecraft.gameMode = savedGameMode;
		}
		savedLevel = null;
		savedPlayer = null;
		savedGameMode = null;
	}

	/**
	 * Releases the binding whatever depth it is at. Called from the head of {@code Minecraft#tick},
	 * where no render frame can be in progress: any depth left over there is a frame that did not
	 * come back, and carrying it into the next frame is what makes {@link #bind()} return true for a
	 * binding nobody owns.
	 */
	public static void releaseAll() {
		if (bindDepth == 0) {
			return;
		}
		bindDepth = 1;
		unbind();
	}

	/**
	 * Called once per client tick. The placeholder player is deliberately not in anything that ticks
	 * entities, so this is the only thing that moves it — no physics, no {@code aiStep}, and
	 * therefore no chance of the position code that talks to a server ever running.
	 */
	public static void tick() {
		LocalPlayer localPlayer = player;
		ClientLevel placeholderLevel = level;
		if (!installed || localPlayer == null || placeholderLevel == null) {
			return;
		}

		// Interpolation reads the previous position, so stamp it before moving rather than after.
		localPlayer.setOldPosAndRot();

		if (!NoLoadingScreenConfig.get().placeholderFreeMove) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.screen() != null || !minecraft.isWindowActive()) {
			return;
		}

		float forward = axis(minecraft.options.keyUp.isDown(), minecraft.options.keyDown.isDown());
		float strafe = axis(minecraft.options.keyLeft.isDown(), minecraft.options.keyRight.isDown());
		float vertical = axis(minecraft.options.keyJump.isDown(), minecraft.options.keyShift.isDown());
		if (forward == 0.0F && strafe == 0.0F && vertical == 0.0F) {
			return;
		}

		Vec3 ahead = Vec3.directionFromRotation(0.0F, localPlayer.getYRot());
		Vec3 left = Vec3.directionFromRotation(0.0F, localPlayer.getYRot() - 90.0F);
		Vec3 movement = ahead.scale(forward).add(left.scale(strafe)).add(0.0, vertical, 0.0);
		double length = movement.length();
		if (length < 1.0E-4) {
			return;
		}

		double speed = minecraft.options.keySprint.isDown() ? 1.6 : 0.6;
		movement = movement.scale(speed / length);
		// setPos rather than setDeltaMovement: nothing integrates velocity here.
		localPlayer.setPos(
			localPlayer.getX() + movement.x,
			Mth.clamp(localPlayer.getY() + movement.y, placeholderLevel.getMinY(), placeholderLevel.getMaxY()),
			localPlayer.getZ() + movement.z
		);
	}

	private static float axis(final boolean positive, final boolean negative) {
		return (positive ? 1.0F : 0.0F) - (negative ? 1.0F : 0.0F);
	}

	/**
	 * Vanilla's {@code handleKeybinds} is suppressed while the placeholder is up — every branch in
	 * it dereferences the player and half of them talk to the server. Clicks queued in the meantime
	 * are dropped here rather than left to fire the moment the real world opens.
	 */
	public static void discardQueuedClicks() {
		for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
			while (mapping.consumeClick()) {
				// drain
			}
		}
	}
}
