package io.github.bingkkni.noloadingscreen;

import io.github.bingkkni.noloadingscreen.platform.ClientUi;
import io.github.bingkkni.noloadingscreen.platform.SceneFactory;
import io.github.bingkkni.noloadingscreen.platform.SceneRenderer;
import io.github.bingkkni.noloadingscreen.platform.PlayerEnvironment;
import io.github.bingkkni.noloadingscreen.gui.LoadingInventoryScreen;
import io.github.bingkkni.noloadingscreen.mixin.AvatarAccessor;
import io.github.bingkkni.noloadingscreen.mixin.ClientCommonPacketListenerImplAccessor;
import io.github.bingkkni.noloadingscreen.mixin.EntityAccessor;
import io.github.bingkkni.noloadingscreen.mixin.LivingEntityAccessor;
import io.github.bingkkni.noloadingscreen.mixin.LocalPlayerAccessor;
import io.github.bingkkni.noloadingscreen.mixin.PlayerAccessor;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerSkin;
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
 * <h2>Synthesising (initial world load / server login)</h2>
 *
 * <p>Starting a singleplayer world has no previous world to keep, so {@link #synthesise} builds an
 * ordinary {@code ClientLevel} — empty chunk cache, plains biome, noon, clear sky — and a
 * {@code LocalPlayer} standing in it. This is deliberately the <em>only</em> path that constructs a
 * {@code ClientPacketListener}, because that constructor is hooked by other mods and has global side
 * effects; see {@link PlayAddonGuard}. It runs only before a real play listener exists.
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
 *       player at the vanilla moment with the server's own position and rotation. Only relative cape animation is handed over. Camera movement, inventory edits and
 *       abilities never survive into the session, so there is no local gameplay state for the
 *       server to correct.</li>
 *   <li>Binding is per-frame: {@code minecraft.level} / {@code player} / {@code gameMode} are only
 *       assigned only around rendering, input/UI initialization and our local visual/movement
 *       update, then set back before vanilla ticks entities or connections.</li>
 * </ul>
 */
public final class PlaceholderWorld {
	/** Small on purpose: {@code ClientChunkCache} allocates {@code (2r+1)^2} slots up front. */
	private static final int CHUNK_RADIUS = 2;
	private static final int SEA_LEVEL = 63;
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
	/** Failed placeholder frames/local updates, counted for the life of the process. */
	private static int renderFailures;
	private static final PlaceholderMovement movement = new PlaceholderMovement();
	private static final PlaceholderDeltaTracker renderClock = new PlaceholderDeltaTracker();
	private static double walkingSpeed;
	private static double flyingSpeed;
	private static final PlaceholderControls controls = new PlaceholderControls();
	private static final PlaceholderInteraction interaction = new PlaceholderInteraction();
	private static double syntheticFloor;
	private static boolean inheritedMayfly;
	private static boolean flightOverride;
	private static net.minecraft.world.level.GameType localMode = net.minecraft.world.level.GameType.SURVIVAL;

	public static net.minecraft.world.level.GameType localMode() { return localMode; }

	/** Local command only; gameMode.setLocalMode does not send a command/ability packet. */
	public static boolean setLocalMode(final net.minecraft.world.level.GameType mode) {
		if (!bind()) return false;
		try {
			gameMode.setLocalMode(mode);
			localMode = mode;
			inheritedMayfly = player.getAbilities().mayfly;
			controls.setFlying(player.getAbilities().flying);
			flightOverride = false;
			applyFlightPolicy(player);
			((LivingEntityAccessor) player).nls$updateInvisibilityStatus();
			if (mode == net.minecraft.world.level.GameType.SPECTATOR) player.setInvisible(true);
			interaction.stopBreaking(player);
			interaction.reset();
			return true;
		} finally { unbind(); }
	}

	public static java.util.List<String> playerNames() {
		if (level == null) return java.util.List.of();
		return level.players().stream().filter(p -> p != player && !p.isRemoved()).map(p -> p.getGameProfile().name()).toList();
	}

	public static boolean teleportToPlayer(final String name) {
		if (localMode != net.minecraft.world.level.GameType.SPECTATOR || !bind()) return false;
		try {
			for (var target : level.players()) {
				if (target != player && !target.isRemoved() && target.getGameProfile().name().equals(name)) {
					player.setPos(target.position());
					movement.reset(target.getX(), target.getY(), target.getZ(), 0, 0, 0, false);
					return true;
				}
			}
			return false;
		} finally { unbind(); }
	}

	private static void applyFlightPolicy(final LocalPlayer localPlayer) {
		boolean override = NoLoadingScreenConfig.get().allowFlightAndNoclip;
		localPlayer.getAbilities().mayfly = inheritedMayfly || override;
		if (override && !flightOverride) controls.setFlying(true);
		flightOverride = override;
		controls.restrictFlight(localPlayer.getAbilities().mayfly);
		localPlayer.getAbilities().flying = controls.flying();
		localPlayer.noPhysics = localMode == net.minecraft.world.level.GameType.SPECTATOR || override && controls.flying();
	}

	// What was in Minecraft's three fields before the outermost bind, put back by the matching
	// unbind. Saved rather than assumed null: vanilla's teardown clears them one at a time —
	// gameMode, then level, then player — so a bind landing in the middle of it must not swallow
	// whatever has not been cleared yet.
	private static @Nullable ClientLevel savedLevel;
	private static @Nullable LocalPlayer savedPlayer;
	private static @Nullable MultiPlayerGameMode savedGameMode;

	private PlaceholderWorld() {
	}

	public static void captureCape() {
		if (installed && player != null) {
			// Restore the simulation position, not the most recent render interpolation.
			player.setPos(movement.x(1), movement.y(1), movement.z(1));
			CapeContinuity.capture(player);
		}
	}

	public static boolean syntheticActive() {
		return installed && synthetic;
	}

	public static boolean active() {
		return installed;
	}

	/** Local mutations require both the correct disposable player and its scoped binding. */
	public static boolean owns(final LocalPlayer candidate) {
		Minecraft minecraft = Minecraft.getInstance();
		return installed && candidate != null && candidate == player && level != null && minecraft.level == level && minecraft.player == candidate;
	}

	/** A synthetic listener has no PlayerInfo; adopted and real players keep vanilla's lookup. */
	public static @Nullable PlayerSkin skinFor(final AbstractClientPlayer candidate) {
		return installed && synthetic && candidate == player ? LocalSkinPreloader.get(candidate.getUUID()) : null;
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
		return adopt(oldLevel, oldPlayer, oldGameMode, true);
	}

	/** Saving defers screen dismissal until GuiMixin handles vanilla's saving message. */
	public static boolean adopt(
		final ClientLevel oldLevel,
		final LocalPlayer oldPlayer,
		final @Nullable MultiPlayerGameMode oldGameMode,
		final boolean dismissScreen
	) {
		return adopt(oldLevel, oldPlayer, oldGameMode, dismissScreen, null);
	}

	static boolean adopt(final ClientLevel oldLevel, final LocalPlayer oldPlayer,
		final @Nullable MultiPlayerGameMode oldGameMode, final boolean dismissScreen, final @Nullable PlaceholderPermissions permissions) {
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

		PlaceholderPermissions inheritedPermissions = permissions != null ? permissions : PlaceholderPermissions.capture(oldPlayer, oldGameMode);
		// Direct handoffs also retire deferred packet work before the first local bind. Never
		// restore this queue on uninstall: this level cannot become a live network world again.
		((RetainedLightQueue) oldLevel).nls$retireLightQueue();
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
			if (!bind()) throw new IllegalStateException("Cannot restore placeholder permissions");
			try { inheritedPermissions.apply(oldPlayer); } finally { unbind(); }
			initializeMovement();

			// clearClientLevel calls gameRenderer.resetData(), which resets the camera; these two put
			// it back. Deliberately *not* levelExtractor.setLevel — that would discard every built
			// section and re-mesh the whole world, which is the exact cost this mod exists to avoid.
			minecraft.gameRenderer.setLevel(oldLevel);
			SceneRenderer.ensureSky(minecraft);
			minecraft.setCameraEntity(oldPlayer);
			if (dismissScreen) dropScreen(minecraft);
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
	 *                   from the {@code WorldStem}; earlier phases use {@link PlaceholderRegistries}.
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
		return synthesise(registries, features, dimensionType, dimension, pos, yRot, xRot, true);
	}

	/** Initial server login may already have a mandatory pack prompt on top of the scene. */
	public static boolean synthesise(
		final RegistryAccess.Frozen registries,
		final @Nullable FeatureFlagSet features,
		final @Nullable Holder<DimensionType> dimensionType,
		final @Nullable ResourceKey<Level> dimension,
		final Vec3 pos,
		final float yRot,
		final float xRot,
		final boolean dismissScreen
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
			initializeMovement();
			// Ours to attach, and ours to keep attached until uninstall(): re-pointing these per
			// frame would rebuild the section graph sixty times a second.
			SceneRenderer.setLevel(minecraft, level);
			minecraft.particleEngine.setLevel(level);
			minecraft.gameRenderer.setLevel(level);
			SceneRenderer.ensureSky(minecraft);
			minecraft.setCameraEntity(player);
			if (dismissScreen) dropScreen(minecraft);
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

		SceneFactory.Scene scene = SceneFactory.createLevel(minecraft, deadConnection(), registries, enabledFeatures, type,
			dimension, CHUNK_RADIUS, SEA_LEVEL);
		level = scene.level();
		ClientPacketListener listener = scene.listener();

		// setLocalMode() is deliberately not called: it reaches straight through to
		// minecraft.player.getAbilities(), which is null at this point, and the field it would set
		// already defaults to GameType.DEFAULT_MODE — survival, which is what we want anyway.
		gameMode = new MultiPlayerGameMode(minecraft, listener);

		LocalPlayer localPlayer = SceneFactory.createPlayer(gameMode, level);
		localPlayer.input = new KeyboardInput(minecraft.options);
		// Entity#id starts at 0 and Entity#getId throws until it is assigned — which ClientLevel
		// #addEntity does immediately. Vanilla assigns the server's id in handleLogin just before
		// the same call; this level is private, so any non-zero value will do.
		localPlayer.setId(PLACEHOLDER_ENTITY_ID);
		localPlayer.snapTo(pos.x, pos.y, pos.z, yRot, xRot);
		localPlayer.setOldPosAndRot();
		level.addEntity(localPlayer);
		player = localPlayer;
		syntheticFloor = pos.y;
		localPlayer.setOnGround(true);
		// Pose queries need Minecraft#getConnection, so initialization happens only after all
		// three placeholder fields exist and can be bound together (see initializeMovement).
	}

	/** A connection with no channel; {@code send} parks packets in a queue nobody drains. */
	private static Connection deadConnection() {
		return new Connection(PacketFlow.CLIENTBOUND);
	}

	// --- Lifecycle ----------------------------------------------------------------------------

	/**
	 * Only now can the loading screen go: {@code Gui#setScreen(null)} answers a null level with the
	 * title screen, so it has to see the placeholder.
	 */
	private static void dropScreen(final Minecraft minecraft) {
		if (bind()) {
			try {
				if (ClientUi.screen(minecraft) != null) {
					ClientUi.setScreen(minecraft, null);
				}
			} finally {
				unbind();
			}
		}
	}

	/** Drops the placeholder and releases engines that still belong to the abandoned world. */
	public static void uninstall() {
		if (!installed) {
			return;
		}

		// Close our local inventory while null-screen handling can still bind the placeholder.
		// Never carry local edits over login, failure, or disconnect; leave unrelated prompts alone.
		Minecraft client = Minecraft.getInstance();
		// A held sandbox click must not become an attack/place packet in the new session. Keep
		// movement keys alone, but require a fresh mouse press after the handoff.
		client.options.keyAttack.setDown(false);
		client.options.keyUse.setDown(false);
		discardQueuedClicks();
		if (ScreenTransitions.isLocalScreen(ClientUi.screen(client))) ClientUi.setScreen(client, null);
		releaseAll();
		PlaceholderBlockEffects.clear();
		PlaceholderItems.clear();
		PlaceholderCombat.clear();
		PlaceholderEquipment.clear();
		installed = false;
		synthetic = false;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			minecraft.setCameraEntity(null);
			SceneRenderer.setLevel(minecraft, null);
			minecraft.particleEngine.setLevel(null);
			minecraft.gameRenderer.setLevel(null);
		}

		level = null;
		player = null;
		gameMode = null;
		renderClock.source = DeltaTracker.ZERO;
		interaction.reset();
	}

	/**
	 * Called when a bound frame or local placeholder update threw. This is a cosmetic feature standing
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
			"Placeholder world failed during rendering or local update; dropping it and falling back to vanilla "
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

		uninstall();

		if (++renderFailures >= MAX_RENDER_FAILURES) {
			NoLoadingScreen.LOGGER.error(
				"Placeholder world has failed {} times; leaving it off for the rest of this session. "
					+ "Joins fall back to the vanilla loading screen, the early readiness gate is unaffected.",
				renderFailures
			);
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

	private static void initializeMovement() {
		if (!bind()) throw new IllegalStateException("Cannot initialize an unbound placeholder");
		try {
			syncAppearance(player);
			resetMovement(player);
			io.github.bingkkni.noloadingscreen.compat.WaveyCapesCompatibility.tick(player);
		} finally {
			unbind();
		}
	}

	private static void syncAppearance(final LocalPlayer localPlayer) {
		if (!synthetic) return;
		Options options = Minecraft.getInstance().options;
		int parts = 0;
		for (PlayerModelPart part : PlayerModelPart.values()) {
			if (options.isModelPartEnabled(part)) parts |= part.getMask();
		}
		// No server will echo ClientInformation back to this disposable player.
		localPlayer.getEntityData().set(AvatarAccessor.nls$modelCustomisation(), (byte)parts);
		localPlayer.setMainArm(options.mainHand().get());
	}

	private static void resetMovement(final LocalPlayer localPlayer) {
		interaction.stopBreaking(localPlayer);
		interaction.reset();
		PlaceholderItems.clear();
		PlaceholderBlockEffects.clear();
		localMode = gameMode.getPlayerMode();
		inheritedMayfly = localPlayer.getAbilities().mayfly;
		flightOverride = false;
		PlaceholderEquipment.reset(localPlayer);
		Vec3 velocity = localPlayer.getDeltaMovement();
		movement.reset(localPlayer.getX(), localPlayer.getY(), localPlayer.getZ(), velocity.x, velocity.y, velocity.z, localPlayer.onGround());
		walkingSpeed = localPlayer.getAttributeValue(Attributes.MOVEMENT_SPEED);
		if (localPlayer.isSprinting()) walkingSpeed /= 1.3;
		flyingSpeed = localPlayer.getAbilities().getFlyingSpeed();
		Minecraft minecraft = Minecraft.getInstance();
		// Preserve ability flight on a server handoff; a fresh synthetic player still starts on
		// foot. Captured held keys are not new taps and must not toggle the inherited state.
		controls.reset(localPlayer.isSprinting(), localPlayer.getAbilities().flying,
			minecraft.options.keyUp.isDown(), minecraft.options.keyJump.isDown());
		applyFlightPolicy(localPlayer);
		// stopFallFlying briefly SETS the flag before clearing it, which starts an elytra sound
		// even on a newly constructed standing player if called unconditionally.
		if (localPlayer.isFallFlying()) localPlayer.stopFallFlying();
		// Clear a captured bow/shield/spyglass pose without releaseUsingItem (which runs item
		// gameplay callbacks). LocalPlayer.stopUsingItem only clears local use state on a client.
		localPlayer.stopUsingItem();
		localPlayer.input = new KeyboardInput(minecraft.options);
		localPlayer.input.tick();
		updatePose(localPlayer);
		localPlayer.setOldPosAndRot();
	}

	/** Advances only local movement, visual state and the camera, never a gameplay/entity tick. */
	public static void tick() {
		LocalPlayer localPlayer = player;
		ClientLevel placeholderLevel = level;
		if (!installed || localPlayer == null || placeholderLevel == null) return;

		// Collision and pose helpers also reach Minecraft#getConnection via getPlayerInfo. A
		// camera-only bind at the end of this method is too late, even for an adopted player whose
		// PlayerInfo happens not to be cached. Release BEFORE vanilla ticks connections/entities.
		if (!bind()) return;
		try {
			tickBound(localPlayer, placeholderLevel);
		} finally {
			unbind();
		}
	}

	private static void tickBound(final LocalPlayer localPlayer, final ClientLevel placeholderLevel) {
		Minecraft minecraft = Minecraft.getInstance();
		syncAppearance(localPlayer);
		boolean simulate = NoLoadingScreenConfig.get().placeholderFreeMove;
		boolean inputEnabled = simulate && ClientUi.screen(minecraft) == null && ClientUi.overlay(minecraft) == null && minecraft.isWindowActive();
		// Menus stop INPUT, not momentum/gravity/animation. In particular, typing W or Space in
		// chat must not move or toggle flight, but opening chat in midair must not suspend a fall.
		boolean forwardDown = inputEnabled && minecraft.options.keyUp.isDown();
		boolean backwardDown = inputEnabled && minecraft.options.keyDown.isDown();
		boolean jumpDown = inputEnabled && minecraft.options.keyJump.isDown();
		boolean shiftDown = inputEnabled && minecraft.options.keyShift.isDown();
		controls.tick(forwardDown, backwardDown, jumpDown, shiftDown,
			inputEnabled && minecraft.options.keySprint.isDown(), minecraft.options.sprintWindow().get(), inputEnabled);
		applyFlightPolicy(localPlayer);
		boolean flying = controls.flying();
		localPlayer.setSprinting(controls.sprinting());
		PlaceholderEquipment.update(localPlayer);
		walkingSpeed = localPlayer.getAttributeValue(Attributes.MOVEMENT_SPEED) / (localPlayer.isSprinting() ? 1.3 : 1);
		localPlayer.input.tick();
		if (!inputEnabled) localPlayer.input.keyPresses = net.minecraft.world.entity.player.Input.EMPTY;
		// Render interpolation must never become the origin for the next physics/collision query.
		localPlayer.setPos(movement.x(1), movement.y(1), movement.z(1));
		localPlayer.setOldPosAndRot();
		localPlayer.setOnGround(movement.onGround());
		updatePose(localPlayer);
		double sneakScale = localPlayer.isCrouching() ? localPlayer.getAttributeValue(Attributes.SNEAKING_SPEED) : 1.0;
		double friction = placeholderLevel.getBlockState(localPlayer.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction();
		friction = modifiedFriction(friction, PlayerEnvironment.frictionModifier(localPlayer));
		double airDragModifier = PlayerEnvironment.airDragModifier(localPlayer);
		boolean inWater = localPlayer.isInWater() && !flying;
		double waterEfficiency = PlayerEnvironment.waterMovementEfficiency(localPlayer) * (localPlayer.onGround() ? 1.0 : 0.5);
		double waterSlowdown = localPlayer.isSprinting() ? 0.9 : ((LivingEntityAccessor) localPlayer).nls$waterSlowDown();
		waterSlowdown += (0.54600006 - waterSlowdown) * waterEfficiency;
		double waterAcceleration = 0.02 + (walkingSpeed - 0.02) * waterEfficiency;
		PlaceholderMovement.Physics physics = new PlaceholderMovement.Physics(walkingSpeed, flyingSpeed,
			((LivingEntityAccessor) localPlayer).nls$jumpPower(), localPlayer.getGravity(), friction,
			modifiedFriction(0.91, airDragModifier), modifiedFriction(0.98, airDragModifier),
			inWater, waterSlowdown, waterAcceleration);
		double forward = axis(forwardDown, backwardDown);
		double strafe = inputEnabled ? axis(minecraft.options.keyLeft.isDown(), minecraft.options.keyRight.isDown()) : 0.0;
		double inputScale = sneakScale / Math.max(1.0, Math.hypot(forward, strafe));
		movement.tick(
			forward * inputScale, strafe * inputScale,
			axis(jumpDown, shiftDown), localPlayer.getYRot(), physics, flying, controls.sprinting(), jumpDown,
			simulate, synthetic ? syntheticFloor : placeholderLevel.getMinY(), placeholderLevel.getMaxY(), localPlayer.noPhysics, requested -> {
				Vec3 delta = new Vec3(requested.x(), requested.y(), requested.z());
				delta = ((PlayerAccessor) localPlayer).nls$backOffFromEdge(delta, MoverType.SELF);
				delta = ((EntityAccessor) localPlayer).nls$collide(delta);
				return new PlaceholderMovement.Motion(delta.x, delta.y, delta.z);
			});
		if (movement.horizontalCollision() && !flying) controls.stopSprinting();
		localPlayer.setPos(movement.x(1), movement.y(1), movement.z(1));
		localPlayer.setOnGround(movement.onGround());
		// Splash volume must use local motion, not the outgoing server player's frozen velocity.
		localPlayer.setDeltaMovement(movement.x(1) - movement.x(0), movement.y(1) - movement.y(0), movement.z(1) - movement.z(0));
		updatePose(localPlayer);
		((LivingEntityAccessor) localPlayer).nls$updateSwimAmount();
		PlaceholderVisuals.tick(localPlayer,
			movement.x(1) - movement.x(0), movement.y(1) - movement.y(0), movement.z(1) - movement.z(0));
		tickArmRotation(localPlayer);
		SceneRenderer.tickCamera(minecraft);
		SceneRenderer.tickHands(minecraft, localPlayer);
		interaction.tick();
		if (!minecraft.options.keyAttack.isDown() || ClientUi.screen(minecraft) != null
			|| ClientUi.overlay(minecraft) != null || !minecraft.isWindowActive()) interaction.stopBreaking(localPlayer);
		else if (consumeClicks(minecraft.options.keyAttack)) interaction.attack(localPlayer);
		else interaction.continueAttack(localPlayer);
		PlaceholderItems.tick(localPlayer);
		PlaceholderBlockEffects.tick();
	}

	private static void tickArmRotation(final LocalPlayer localPlayer) {
		// LocalPlayer.applyInput's visual-only arm lag; leaving these frozen tilts the hand
		// farther and farther as the camera turns. No special flying hand transform exists.
		localPlayer.xBobO = localPlayer.xBob;
		localPlayer.yBobO = localPlayer.yBob;
		localPlayer.xBob += (localPlayer.getXRot() - localPlayer.xBob) * 0.5F;
		localPlayer.yBob += (localPlayer.getYRot() - localPlayer.yBob) * 0.5F;
	}

	private static double modifiedFriction(final double friction, final double modifier) {
		return Math.clamp(1.0 - (1.0 - friction) * modifier, 0.0, 1.0);
	}

	private static void updatePose(final LocalPlayer localPlayer) {
		PlayerEnvironment.sampleFluids(localPlayer);
		PlaceholderBlockEffects.updateUnderwater(localPlayer);
		localPlayer.updateSwimming(); // vanilla explicitly clears swimming during ability flight
		PlayerAccessor pose = (PlayerAccessor) localPlayer;
		((LocalPlayerAccessor) localPlayer).nls$setCrouching(!localPlayer.getAbilities().flying
			&& !localPlayer.isSwimming() && !localPlayer.isPassenger() && pose.nls$canFit(Pose.CROUCHING)
			&& (localPlayer.isShiftKeyDown() || !localPlayer.isSleeping() && !pose.nls$canFit(Pose.STANDING)));
		if (localPlayer.noPhysics) {
			// Noclip must not use Player.updatePlayerPose's forced crawling inside solid blocks.
			localPlayer.setPose(Pose.STANDING);
		} else {
			pose.nls$updatePlayerPose();
		}
	}

	/** Called after Camera.update aligned the camera, before sky/fog extraction. */
	public static void refreshEnvironment() {
		Minecraft minecraft = Minecraft.getInstance();
		if (installed && level != null && minecraft.level == level) {
			SceneRenderer.refreshEnvironment(minecraft, level);
		}
	}

	/** Position is interpolated once here; eye height, FOV, arms and local animation use live alpha. */
	public static DeltaTracker prepareRender(final DeltaTracker deltaTracker) {
		LocalPlayer localPlayer = player;
		if (!installed || localPlayer == null || Minecraft.getInstance().level != level) {
			return deltaTracker;
		}

		DeltaTracker localTime = LoadingWaitLoop.renderTime(deltaTracker);
		float partialTick = localTime.getGameTimeDeltaPartialTick(true);
		localPlayer.setPos(movement.x(partialTick), movement.y(partialTick), movement.z(partialTick));
		localPlayer.setOldPosAndRot();
		renderClock.source = localTime;
		return renderClock;
	}

	public static DeltaTracker renderDelta(final DeltaTracker deltaTracker) {
		return isBound() ? renderClock : deltaTracker;
	}

	public static boolean retainsForRendering(final Object candidate) {
		if (!isBound()) return false;
		return candidate instanceof Entity entity && entity.level() == level
			|| candidate instanceof net.minecraft.world.level.block.entity.BlockEntity block && block.getLevel() == level;
	}

	private static boolean isBound() {
		return installed && level != null && Minecraft.getInstance().level == level;
	}

	/** Only local visuals (player and newly created debris) use the live placeholder clock. */
	public static float localPartialTick(final float original) {
		return isBound() ? renderClock.getGameTimeDeltaPartialTick(true) : original;
	}

	public static float avatarPartialTick(final int entityId, final float original) {
		return isBound() ? (player != null && entityId == player.getId() ? localPartialTick(original) : 1.0F) : original;
	}

	/** Remote entities keep their final pose; the controlled player's animation continues. */
	public static float entityPartialTick(final Entity entity, final float original) {
		return isBound() ? (entity == player || PlaceholderItems.owns(entity) || PlaceholderCombat.animating(entity) ? localPartialTick(original) : 1.0F) : original;
	}

	/** A split clock, not a globally frozen clock: freezing camera alpha quantizes visuals to 20 Hz. */
	private static final class PlaceholderDeltaTracker implements DeltaTracker {
		private DeltaTracker source = DeltaTracker.ZERO;

		@Override
		public float getGameTimeDeltaTicks() {
			return 0.0F;
		}

		@Override
		public float getGameTimeDeltaPartialTick(final boolean ignoreFrozenGame) {
			return ignoreFrozenGame ? this.source.getGameTimeDeltaPartialTick(true) : 1.0F;
		}

		@Override
		public float getRealtimeDeltaTicks() {
			return this.source.getRealtimeDeltaTicks();
		}
	}

	private static float axis(final boolean positive, final boolean negative) {
		return (positive ? 1.0F : 0.0F) - (negative ? 1.0F : 0.0F);
	}

	/**
	 * Vanilla's {@code handleKeybinds} is suppressed while the placeholder is up. After handling
	 * local UI and sandbox actions, leftover clicks are dropped rather than left to fire when
	 * the real world opens.
	 */
	public static void discardQueuedClicks() {
		for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
			while (mapping.consumeClick()) {
				// drain
			}
		}
	}

	/** The tick runs before handleKeybinds; consume a press before attempting held mining. */
	private static boolean consumeClicks(final KeyMapping key) {
		boolean pressed = false;
		while (key.consumeClick()) pressed = true;
		return pressed;
	}

	/** Handles the few local UI actions that remain useful while the real connection is paused. */
	public static void handleSafeKeybinds() {
		if (!installed) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		while (minecraft.options.keyTogglePerspective.consumeClick()) {
			CameraType previous = minecraft.options.getCameraType();
			minecraft.options.setCameraType(previous.cycle());
			if (previous.isFirstPerson() != minecraft.options.getCameraType().isFirstPerson()) {
				minecraft.gameRenderer.checkEntityPostEffect(
					minecraft.options.getCameraType().isFirstPerson() ? minecraft.getCameraEntity() : null
				);
			}
		}

		if (minecraft.options.keyChat.consumeClick() && ClientUi.screen(minecraft) == null) {
			boolean bound = bind();
			try {
				if (bound) {
					ClientUi.openChat(minecraft, ChatComponent.ChatMethod.MESSAGE);
				}
			} finally {
				if (bound) {
					unbind();
				}
			}
		}

		if (minecraft.options.keyCommand.consumeClick() && ClientUi.screen(minecraft) == null) {
			boolean bound = bind();
			try {
				if (bound) {
					ClientUi.openChat(minecraft, ChatComponent.ChatMethod.COMMAND);
				}
			} finally {
				if (bound) {
					unbind();
				}
			}
		}

		if (minecraft.options.keyInventory.consumeClick() && ClientUi.screen(minecraft) == null && bind()) {
			try {
				if (!player.isSpectator()) ClientUi.setScreen(minecraft, player.isCreative()
					? new io.github.bingkkni.noloadingscreen.gui.LoadingCreativeInventoryScreen(player) : new LoadingInventoryScreen(player));
			} finally {
				unbind();
			}
		}

		if (ClientUi.screen(minecraft) == null && ClientUi.overlay(minecraft) == null && bind()) {
			try {
				for (int slot = 0; slot < 9; slot++) {
					while (minecraft.options.keyHotbarSlots[slot].consumeClick()) PlaceholderInteraction.selectSlot(player, slot);
				}
				while (minecraft.options.keySwapOffhand.consumeClick()) PlaceholderInteraction.swapOffhand(player);
				while (minecraft.options.keyDrop.consumeClick()) PlaceholderItems.dropSelected(player, minecraft.hasControlDown());
				boolean attack = consumeClicks(minecraft.options.keyAttack);
				boolean use = minecraft.options.keyUse.consumeClick();
				// Fresh presses start local actions; held mining progresses on the 20 Hz scene tick.
				// Consume vanilla's click queue without replaying it in the incoming session.
				if (attack) interaction.attack(player);
				if (use || minecraft.options.keyUse.isDown()) interaction.use(player);
				while (minecraft.options.keyPickItem.consumeClick()) PlaceholderInteraction.pickBlock(player);
			} catch (Throwable failure) {
				onRenderFailed(failure);
			} finally {
				unbind();
			}
		}
		// Drain unsupported operations and extra clicks; never replay them on the next server.
		discardQueuedClicks();
	}
}
