package io.github.bingkkni.noloadingscreen.verification;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.bingkkni.noloadingscreen.DisconnectedWorldView;
import io.github.bingkkni.noloadingscreen.JoinPhase;
import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import io.github.bingkkni.noloadingscreen.gui.LoadingHud;
import io.github.bingkkni.noloadingscreen.gui.LoadingPauseScreen;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import net.minecraft.util.StringDecomposer;
import net.minecraft.util.Util;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import sun.misc.Unsafe;

/** Regression of the actual transformed lifecycle hooks; graphics/sockets are deliberately absent. */
final class LoadingTransitionsVerification {
	private static int assertions;

	static void run(final Minecraft minecraft, final LocalPlayer player, final LoadingVisualVerification.EmptyLevel level)
		throws ReflectiveOperationException {
		MultiPlayerGameMode mode = new MultiPlayerGameMode(minecraft, player.connection);
		seedScene(minecraft, player, level, mode);
		verifyHudTickBinding(minecraft, player);
		verifyResourceHandoff(minecraft, player, level);
		verifyThirdPerson(minecraft, player);
		verifyHud();
		verifyMessage();
		verifyKickWarn(minecraft, player, level, mode);
		System.out.println("LoadingTransitionsVerification: " + assertions
			+ " assertions passed (continuous resource handoff, local entity visibility, bar-only HUD, KickWarn/explicit exit and rich multiline messages).");
	}

	private static void verifyResourceHandoff(final Minecraft minecraft, final LocalPlayer player, final ClientLevel level)
		throws ReflectiveOperationException {
		Screen resources = new GenericMessageScreen(Component.translatable("selectWorld.resource_load"));
		set(NoLoadingScreen.class, null, "resourceScreen", resources);
		set(NoLoadingScreen.class, null, "resourcePlaceholderAttempted", true);
		player.setYRot(127);
		player.setXRot(-31);
		Object movement = get(PlaceholderWorld.class, null, "movement");
		AtomicInteger disconnects = new AtomicInteger();
		Operation<Void> original = args -> { disconnects.incrementAndGet(); return null; };
		method(Minecraft.class, "nls$keepPreparedScene").invoke(minecraft, minecraft, original);
		check(disconnects.get() == 0 && PlaceholderWorld.active(), "Resource handoff skips only the redundant empty-session disconnect");
		Connection pending = new Connection(PacketFlow.CLIENTBOUND);
		set(Minecraft.class, minecraft, "pendingConnection", pending);
		method(Minecraft.class, "nls$keepPreparedScene").invoke(minecraft, minecraft, original);
		check(disconnects.get() == 1, "A real pending connection must still be cleaned up");
		set(Minecraft.class, minecraft, "pendingConnection", null);

		LevelLoadTracker tracker = new ProgressTracker();
		LevelLoadingScreen loading = new LevelLoadingScreen(tracker, LevelLoadingScreen.Reason.OTHER);
		NoLoadingScreen.onSingleplayerLoadStart(null, loading); // already installed: must not read/build registries
		check(!NoLoadingScreen.preparingResources() && NoLoadingScreen.phase() == JoinPhase.SERVER_BOOT,
			"Successful handoff ends resource ownership, not the join timeline");
		check(get(NoLoadingScreen.class, null, "overlayTracker") == tracker, "The exact vanilla tracker survives hidden-screen installation");
		check(get(PlaceholderWorld.class, null, "level") == level && get(PlaceholderWorld.class, null, "player") == player
			&& get(PlaceholderWorld.class, null, "movement") == movement && player.getYRot() == 127 && player.getXRot() == -31,
			"The same world/player/movement/rotation survive resource-to-boot handoff");
		method(Gui.class, "nls$returnToPlaceholder").invoke(minecraft.gui, loading, (Operation<Void>) args -> {
			check(args[0] == null && PlaceholderWorld.owns(player), "Loading screen is never mounted over the existing scene");
			return null;
		});
		check(minecraft.level == null && minecraft.player == null, "Hiding a phase screen cannot leak bindings");
		Screen menu = new LoadingPauseScreen(Component.empty(), (Runnable) null);
		set(Gui.class, minecraft.gui, "screen", menu);
		method(Gui.class, "nls$returnToPlaceholder").invoke(minecraft.gui, loading, (Operation<Void>) args -> {
			throw new AssertionError("A hidden phase screen must not reinitialize an open local menu");
		});
		check(minecraft.gui.screen() == menu && player.getYRot() == 127, "Open local UI and camera are retained across the phase change");
		set(Gui.class, minecraft.gui, "screen", null);
	}

	private static void verifyThirdPerson(final Minecraft minecraft, final LocalPlayer player) throws ReflectiveOperationException {
		Object extractor = allocate(LevelExtractor.class);
		Method visible = method(LevelExtractor.class, "nls$showLocalPlayerWithoutTerrain");
		AtomicInteger compiledQueries = new AtomicInteger();
		Operation<Boolean> uncompiled = args -> { compiledQueries.incrementAndGet(); return false; };
		check(PlaceholderWorld.bind(), "Visibility test binds the actual disposable local player");
		try {
			check((boolean) visible.invoke(extractor, null, player.blockPosition(), uncompiled, player, null, 0.0, 0.0, 0.0),
				"Local player is visible without compiled terrain, including the synthetic void");
			check(compiledQueries.get() == 0, "Player visibility does not fabricate a chunk or rebuild its mesh");
			check(!(boolean) visible.invoke(extractor, null, BlockPos.ZERO, uncompiled, allocate(LocalPlayer.class), null, 0.0, 0.0, 0.0),
				"Other players retain the exact vanilla section-visibility decision");
		} finally { PlaceholderWorld.unbind(); }
		check(!(boolean) visible.invoke(extractor, null, player.blockPosition(), uncompiled, player, null, 0.0, 0.0, 0.0)
			&& compiledQueries.get() == 2, "Unbound/live rendering retains vanilla culling");
	}

	private static void verifyHudTickBinding(final Minecraft minecraft, final LocalPlayer player)
		throws ReflectiveOperationException {
		Hud hud = allocate(Hud.class);
		CapturingChat chat = allocate(CapturingChat.class);
		Object previousDisplayTime = get(Options.class, minecraft.options, "notificationDisplayTime");
		ItemStack previousItem0 = player.getInventory().getItem(0);
		ItemStack previousItem1 = player.getInventory().getItem(1);
		ItemStack previousItem2 = player.getInventory().getItem(2);
		int previousSelectedSlot = player.getInventory().getSelectedSlot();
		var camera = minecraft.gameRenderer.mainCamera();
		var previousCameraEntity = minecraft.getCameraEntity();
		camera.reset();
		try {
			set(Hud.class, hud, "minecraft", minecraft);
			set(Hud.class, hud, "lastToolHighlight", ItemStack.EMPTY);
			set(Hud.class, hud, "chat", chat);
			OptionInstance<Double> displayTime = allocate(OptionInstance.class);
			set(OptionInstance.class, displayTime, "value", 1.0D);
			set(Options.class, minecraft.options, "notificationDisplayTime", displayTime);
			player.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
			player.getInventory().setItem(1, new ItemStack(Items.STONE));
			player.getInventory().setItem(2, ItemStack.EMPTY);
			player.getInventory().setSelectedSlot(0);
			Method wrapper = method(Gui.class, "nls$tickPlaceholderHud");
			Operation<Void> vanillaTick = args -> {
				((Hud) args[0]).tick((boolean) args[1]);
				return null;
			};

			wrapper.invoke(minecraft.gui, hud, false, vanillaTick);
			int started = (int) get(Hud.class, hud, "toolHighlightTimer");
			check(started > 0 && ((ItemStack) get(Hud.class, hud, "lastToolHighlight")).is(Items.DIAMOND),
				"Bound HUD tick observes the selected placeholder item");
			check(minecraft.player == null && minecraft.level == null && minecraft.gameMode == null,
				"HUD timer binding is released before the rest of Gui.tick");
			wrapper.invoke(minecraft.gui, hud, false, vanillaTick);
			check((int) get(Hud.class, hud, "toolHighlightTimer") == started - 1,
				"Selected-item text countdown advances instead of freezing forever");
			player.getInventory().setSelectedSlot(1);
			wrapper.invoke(minecraft.gui, hud, false, vanillaTick);
			int switched = (int) get(Hud.class, hud, "toolHighlightTimer");
			check(switched >= started && ((ItemStack) get(Hud.class, hud, "lastToolHighlight")).is(Items.STONE),
				"Switching placeholder slots restarts the vanilla item-name notification");
			wrapper.invoke(minecraft.gui, hud, true, vanillaTick);
			check((int) get(Hud.class, hud, "toolHighlightTimer") == switched,
				"Paused HUD timing keeps vanilla semantics");
			for (int tick = 0; tick < switched; tick++) wrapper.invoke(minecraft.gui, hud, false, vanillaTick);
			check((int) get(Hud.class, hud, "toolHighlightTimer") == 0,
				"Selected-item text reaches zero without requiring a slot change");
			player.getInventory().setSelectedSlot(2);
			wrapper.invoke(minecraft.gui, hud, false, vanillaTick);
			check((int) get(Hud.class, hud, "toolHighlightTimer") == 0,
				"Selecting an empty placeholder slot clears stale item text immediately");
		} finally {
			set(Options.class, minecraft.options, "notificationDisplayTime", previousDisplayTime);
			player.getInventory().setItem(0, previousItem0);
			player.getInventory().setItem(1, previousItem1);
			player.getInventory().setItem(2, previousItem2);
			player.getInventory().setSelectedSlot(previousSelectedSlot);
			camera.setEntity(previousCameraEntity);
		}
	}

	private static void verifyHud() throws ReflectiveOperationException {
		RecordingGraphics graphics = allocate(RecordingGraphics.class);
		graphics.rectangles = new ArrayList<>();
		graphics.texts = new ArrayList<>();
		LoadingHud.draw(new io.github.bingkkni.noloadingscreen.gui.LoadingCanvas(graphics), new ProgressTracker());
		check(graphics.rectangles.size() == 2 && graphics.rectangles.stream().allMatch(r -> r[3] - r[1] == 2),
			"Only the two progress-bar fills are submitted, never the central chunk rectangle");
		check(graphics.texts.size() == 2 && graphics.rectangles.getFirst()[1] < 300,
			"Phase, elapsed text and the bar stay above the old rectangle position");
	}

	private static void verifyMessage() {
		Component reason = Component.literal("§bLegacy\n")
			.append(Component.literal("Green").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
			.append(Component.literal("\nPlain long explanation wraps normally"));
		Component message = DisconnectedWorldView.message(reason);
		check(message.getStyle().equals(Style.EMPTY) && message.getSiblings().get(1).equals(reason),
			"The original rich reason is preserved under a neutral parent, not flattened or recoloured red");
		check(message.getSiblings().getFirst().getStyle().getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.RED)),
			"Only the local connection-failed prefix is red");
		StringBuilder plain = new StringBuilder();
		List<Style> styles = new ArrayList<>();
		StringDecomposer.iterateFormatted(message, Style.EMPTY, (index, style, codepoint) -> {
			plain.appendCodePoint(codepoint);
			styles.add(style);
			return true;
		});
		check(styles.get(plain.indexOf("Legacy")).getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.AQUA))
			&& styles.get(plain.indexOf("Green")).isBold(), "Vanilla decomposition preserves legacy section codes AND styled child components");
		check(styles.get(plain.indexOf("Plain")).getColor() == null, "Unstyled server text does not inherit the warning colour");
		var lines = new StringSplitter((codepoint, style) -> 1).splitLines(message, 20, Style.EMPTY);
		check(lines.size() >= 4 && lines.stream().anyMatch(line -> line.getString().contains("Green")),
			"Vanilla chat splitting handles explicit server newlines and width wrapping together");
	}

	private static void verifyKickWarn(final Minecraft minecraft, final LocalPlayer player,
		final LoadingVisualVerification.EmptyLevel level, final MultiPlayerGameMode mode) throws ReflectiveOperationException {
		Hud oldHud = minecraft.gui.hud;
		Hud hud = allocate(Hud.class);
		CapturingChat chat = allocate(CapturingChat.class);
		set(Hud.class, hud, "chat", chat);
		set(Gui.class, minecraft.gui, "hud", hud);
		Screen parent = allocate(TitleScreen.class);
		Component reason = Component.literal("Unsupported backend version\n").append(Component.literal("Try another version").withStyle(ChatFormatting.YELLOW));
		DisconnectedScreen disconnected = new DisconnectedScreen(parent, Component.literal("Disconnected"), new DisconnectionDetails(reason));
		Method disconnect = method(Minecraft.class, "nls$disconnect", 4);
		Operation<Void> vanillaTeardown = args -> {
			try {
				check(args[0] == disconnected && args[1].equals(false), "Vanilla receives its original failure screen and cleanup flags");
				check(DisconnectedWorldView.active() && !NoLoadingScreen.canRevealConfigScreen(), "Kick immediately releases all connection owners");
				method(Minecraft.class, "nls$keepDisconnectedCamera").invoke(minecraft, minecraft.gameRenderer, (Operation<Void>) ignored -> {
					throw new AssertionError("KickWarn must retain the existing camera");
				});
				minecraft.level = null;
				minecraft.gameMode = null;
				// GPU attachments are outside this fixture. Seed the existing disposable objects,
				// as if adoption had attached them; all lifecycle routing remains the real Mixins.
				seedScene(minecraft, player, level, mode);
				DisconnectedWorldView.install();
				method(Gui.class, "nls$returnToPlaceholder").invoke(minecraft.gui, disconnected, (Operation<Void>) screenArgs -> {
					check(screenArgs[0] == null && PlaceholderWorld.owns(player), "Kick screen is replaced by a complete local scene in the same call");
					try {
						check(!(boolean) method(Gui.class, "nls$allowLocalSavingUi").invoke(minecraft.gui, true), "Only the bound disconnected player bypasses the null-screen teardown guard");
					} catch (ReflectiveOperationException e) { throw new AssertionError(e); }
					return null;
				});
				return null;
			} catch (ReflectiveOperationException e) { throw new AssertionError(e); }
		};
		try {
			// Deliver through vanilla's hidden ServerReconfigScreen.tick, not a custom timeout.
			FakeConnection connection = new FakeConnection();
			connection.deliver = () -> {
				try { disconnect.invoke(minecraft, disconnected, false, false, vanillaTeardown); }
				catch (ReflectiveOperationException e) { throw new AssertionError(e); }
			};
			ServerReconfigScreen owner = new ServerReconfigScreen(Component.empty(), connection);
			set(NoLoadingScreen.class, null, "suppressedConfigScreen", owner);
			NoLoadingScreen.tickPlaceholder();
			check(connection.deliveries == 1 && chat.messages == 1 && chat.last.equals(DisconnectedWorldView.message(reason)),
				"Failed backend kick is printed immediately on the first tick, not after 600 ticks");
			check(chat.restores == 1 && get(DisconnectedWorldView.class, null, "chatState") == null,
				"Vanilla chat history is restored once; the temporary history snapshot is released");
			check(!NoLoadingScreen.shouldDrawOverlay() && NoLoadingScreen.isLoading(), "Offline scene hides loading progress but still blocks outgoing actions/chat");
			verifyOfflineModes(minecraft, player, mode);
			set(NoLoadingScreen.class, null, "overlayStartMs", Util.getMillis() - 120_000L);
			for (int tick = 0; tick < 650; tick++) NoLoadingScreen.tickPlaceholder();
			check(DisconnectedWorldView.visible() && connection.deliveries == 1 && chat.messages == 1
				&& minecraft.getConnection() == null, "More than 30 seconds cannot exit the scene, re-deliver a kick or tick a fake live session");
			check(PlaceholderWorld.bind(), "Permanent offline world retains its normal local interaction binding");
			try { BlockFeedbackVerification.seedSavingFeedback(player, level); }
			finally { PlaceholderWorld.unbind(); }

			Gui originalGui = minecraft.gui;
			CapturingGui menuGui = allocate(CapturingGui.class);
			set(Minecraft.class, minecraft, "gui", menuGui);
			try {
				check(NoLoadingScreen.openLoadingPauseScreen() && menuGui.current instanceof LoadingPauseScreen,
					"Esc opens the normal local return/disconnect menu after a kick");
				Screen menu = menuGui.current;
				menu.width = 800;
				menu.height = 600;
				method(LoadingPauseScreen.class, "init").invoke(menu);
				check(menu.children().size() == 2 && ((Button) menu.children().get(1)).active, "Offline Disconnect is immediately available");
				// Incoming sentinel suppresses only GPU detachment and proves cleanup cannot stomp a new world.
				minecraft.level = allocate(LoadingVisualVerification.EmptyLevel.class);
				((Button) menu.children().get(1)).onPress(null);
				check(menuGui.current == parent && !DisconnectedWorldView.active() && !PlaceholderWorld.active(),
					"Explicit exit releases the permanent scene and returns to vanilla's original destination");
				BlockFeedbackVerification.assertCleared();
			} finally { set(Minecraft.class, minecraft, "gui", originalGui); }

			minecraft.level = level;
			minecraft.player = player;
			minecraft.gameMode = mode;
			check(DisconnectedWorldView.canRetain(), "A normal in-server kick is eligible without a pre-existing loading scene");
			check(!DisconnectedWorldView.begin(disconnected, true), "Protocol transfer itself is not an unexpected kick");
			check(!DisconnectedWorldView.begin(new GenericMessageScreen(Component.empty()), false), "Normal menu disconnect is not KickWarn");
			NoLoadingScreenConfig.get().enabled = false;
			check(!DisconnectedWorldView.canRetain(), "Disabled mod preserves vanilla kicks");
			NoLoadingScreenConfig.get().enabled = true;
			set(Minecraft.class, minecraft, "isLocalServer", true);
			check(!DisconnectedWorldView.canRetain(), "Integrated-server errors/saving are not retained permanently");
			set(Minecraft.class, minecraft, "isLocalServer", false);
			disconnect.invoke(minecraft, disconnected, false, false, (Operation<Void>) args -> {
				try {
					Object snapshot = get(DisconnectedWorldView.class, null, "outgoing");
					check(snapshot != null && get(snapshot.getClass(), snapshot, "level") == level,
						"A normal kick captures the real outgoing world before vanilla teardown");
				} catch (ReflectiveOperationException e) { throw new AssertionError(e); }
				return vanillaTeardown.call(args);
			});
			check(DisconnectedWorldView.visible() && chat.messages == 2, "Normal in-game kicks share the same persistent scene/message lifecycle");

			// Failed teardown propagates rather than swallowing the exception or retaining half-state.
			DisconnectedWorldView.clear();
			try {
				disconnect.invoke(minecraft, disconnected, false, false, (Operation<Void>) args -> {
					try { minecraft.level = allocate(LoadingVisualVerification.EmptyLevel.class); }
					catch (ReflectiveOperationException e) { throw new AssertionError(e); }
					throw new IllegalStateException("expected disconnect failure");
				});
				throw new AssertionError("Disconnect exceptions must propagate");
			} catch (java.lang.reflect.InvocationTargetException expected) {
				check(expected.getCause() instanceof IllegalStateException && !DisconnectedWorldView.active() && !PlaceholderWorld.active(),
					"Exceptional disconnection cannot leave a half-owned KickWarn session");
			}
		} finally {
			set(Gui.class, minecraft.gui, "hud", oldHud);
			set(Minecraft.class, minecraft, "isLocalServer", false);
			NoLoadingScreenConfig.get().enabled = true;
			minecraft.level = null;
			minecraft.player = null;
			minecraft.gameMode = null;
			DisconnectedWorldView.clear();
		}
	}

	@SuppressWarnings("unchecked")
	private static void verifyOfflineModes(final Minecraft minecraft, final LocalPlayer player, final MultiPlayerGameMode controller)
		throws ReflectiveOperationException {
		Object previousInfo = get(AbstractClientPlayer.class, player, "playerInfo");
		GameType previousMode = controller.getPlayerMode();
		boolean previousFlying = player.getAbilities().flying;
		boolean previousInvisible = player.isInvisible();
		PlayerInfo info = new PlayerInfo(player.getGameProfile(), false);
		LocalPlayer other = allocate(LocalPlayer.class);
		set(AbstractClientPlayer.class, player, "playerInfo", info);
		set(AbstractClientPlayer.class, other, "playerInfo", info); // even a shared cache must stay unmodified
		try {
			for (GameType source : GameType.values()) {
				check(PlaceholderWorld.bind(), "Bind mode initialization");
				try {
					controller.setLocalMode(source);
					set(PlayerInfo.class, info, "gameMode", source);
					player.setInvisible(source == GameType.SPECTATOR);
				} finally { PlaceholderWorld.unbind(); }
				boolean flying = player.getAbilities().flying;
				DisconnectedWorldView.install();
				check(minecraft.player == null && minecraft.gameMode == null, "Mode conversion restores the unbound session");
				GameType expected = source == GameType.ADVENTURE || source == GameType.SPECTATOR ? GameType.SURVIVAL : source;
				check(PlaceholderWorld.bind(), "Bind mode assertions");
				try {
					check(controller.getPlayerMode() == expected && player.gameMode() == expected && !player.isSpectator(),
						"Controller and cached player-mode query agree after KickWarn: " + source);
					check(player.getAbilities().mayBuild && player.getAbilities().instabuild == (expected == GameType.CREATIVE),
						"Vanilla abilities follow the converted mode; existing creative mode is untouched");
					check(player.getAbilities().flying == flying && !player.isInvisible(),
						"Conversion retains sandbox flight and clears spectator-only invisibility");
					check(info.getGameMode() == source && other.gameMode() == source,
						"Other players and shared PlayerInfo keep their original mode");
				} finally { PlaceholderWorld.unbind(); }
			}
			check(PlaceholderWorld.bind(), "Bind stale player-info regression");
			try { controller.setLocalMode(GameType.CREATIVE); } finally { PlaceholderWorld.unbind(); }
			DisconnectedWorldView.install();
			check(PlaceholderWorld.bind(), "Bind stale player-info assertions");
			try {
				check(info.getGameMode() == GameType.SPECTATOR && player.gameMode() == GameType.CREATIVE,
					"A stale tab-list mode cannot override the retained controller's actual creative mode");
			} finally { PlaceholderWorld.unbind(); }
			var effects = (java.util.Map<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>, net.minecraft.world.effect.MobEffectInstance>)
				get(net.minecraft.world.entity.LivingEntity.class, player, "activeEffects");
			var invisibility = net.minecraft.world.effect.MobEffects.INVISIBILITY;
			var oldEffect = effects.put(invisibility, new net.minecraft.world.effect.MobEffectInstance(invisibility, 200));
			try {
				check(PlaceholderWorld.bind(), "Bind invisible spectator initialization");
				try { controller.setLocalMode(GameType.SPECTATOR); } finally { PlaceholderWorld.unbind(); }
				DisconnectedWorldView.install();
				check(player.isInvisible() && player.hasEffect(invisibility), "An actual invisibility effect is not stripped by leaving spectator mode");
			} finally {
				if (oldEffect == null) effects.remove(invisibility); else effects.put(invisibility, oldEffect);
			}
			Object fallback = get(DisconnectedWorldView.class, null, "fallback");
			DisconnectedWorldView.clear();
			try {
				check(PlaceholderWorld.bind(), "Bind ordinary loading-mode initialization");
				try {
					controller.setLocalMode(GameType.ADVENTURE);
					set(PlayerInfo.class, info, "gameMode", GameType.ADVENTURE);
				} finally { PlaceholderWorld.unbind(); }
				DisconnectedWorldView.install();
				check(PlaceholderWorld.bind(), "Bind ordinary loading-mode assertions");
				try {
					check(controller.getPlayerMode() == GameType.ADVENTURE && player.gameMode() == GameType.ADVENTURE,
						"Normal loading/saving without a kick does not change adventure mode");
				} finally { PlaceholderWorld.unbind(); }
			} finally { set(DisconnectedWorldView.class, null, "fallback", fallback); }
		} finally {
			check(PlaceholderWorld.bind(), "Bind mode fixture restoration");
			try {
				controller.setLocalMode(previousMode);
				player.getAbilities().flying = previousFlying;
				player.setInvisible(previousInvisible);
				set(AbstractClientPlayer.class, player, "playerInfo", previousInfo);
			} finally { PlaceholderWorld.unbind(); }
		}
	}

	private static void seedScene(final Minecraft minecraft, final LocalPlayer player, final ClientLevel level, final MultiPlayerGameMode mode)
		throws ReflectiveOperationException {
		set(PlaceholderWorld.class, null, "level", level);
		set(PlaceholderWorld.class, null, "player", player);
		set(PlaceholderWorld.class, null, "gameMode", mode);
		set(PlaceholderWorld.class, null, "installed", true);
		Object hands = get(GameRenderer.class, minecraft.gameRenderer, "itemInHandRenderer");
		set(net.minecraft.client.renderer.ItemInHandRenderer.class, hands, "mainHandItem", player.getMainHandItem());
		set(net.minecraft.client.renderer.ItemInHandRenderer.class, hands, "offHandItem", player.getOffhandItem());
		minecraft.level = null;
		minecraft.player = null;
		minecraft.gameMode = null;
	}

	private static final class FakeConnection extends Connection {
		Runnable deliver;
		int deliveries;
		FakeConnection() { super(PacketFlow.CLIENTBOUND); }
		@Override public boolean isConnected() { return false; }
		@Override public void handleDisconnection() { deliveries++; deliver.run(); }
	}

	private static final class CapturingChat extends ChatComponent {
		Component last;
		int messages;
		int restores;
		private CapturingChat() { super(null); }
		@Override public void tick() {}
		@Override public State storeState() { return new State(List.of(), List.of("history"), List.of()); }
		@Override public void restoreState(State state) { restores++; }
		@Override public void addClientSystemMessage(Component message) { last = message; messages++; }
	}

	private static final class CapturingGui extends Gui {
		Screen current;
		private CapturingGui() { super(null, null, null); }
		@Override public Screen screen() { return current; }
		@Override public void setScreen(Screen screen) { current = screen; }
	}

	private static final class ProgressTracker extends LevelLoadTracker {
		@Override public boolean hasProgress() { return true; }
		@Override public float serverProgress() { return .75F; }
		@Override public ChunkLoadStatusView statusView() {
			return new ChunkLoadStatusView() {
				@Override public void moveTo(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, net.minecraft.world.level.ChunkPos pos) {}
				@Override public int radius() { return 11; }
				@Override public ChunkStatus get(int x, int z) { throw new AssertionError("The central chunk map must not be drawn"); }
			};
		}
	}

	private static final class RecordingGraphics extends GuiGraphicsExtractor {
		List<int[]> rectangles;
		List<Component> texts;
		private RecordingGraphics() { super(null, null, 0, 0); }
		@Override public int guiWidth() { return 800; }
		@Override public int guiHeight() { return 600; }
		@Override public void fill(int x1, int y1, int x2, int y2, int color) { rectangles.add(new int[]{x1, y1, x2, y2}); }
		@Override public void centeredText(Font font, Component text, int x, int y, int color) { texts.add(text); }
	}

	private static Method method(final Class<?> type, final String fragment) { return method(type, fragment, -1); }
	private static Method method(final Class<?> type, final String fragment, final int parameters) {
		Method method = Arrays.stream(type.getDeclaredMethods()).filter(m -> m.getName().contains(fragment)
			&& (parameters < 0 || m.getParameterCount() == parameters)).findFirst().orElseThrow();
		method.setAccessible(true);
		return method;
	}
	private static <T> T allocate(final Class<T> type) throws ReflectiveOperationException {
		Field field = Unsafe.class.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
	}
	private static void set(final Class<?> type, final Object target, final String name, final Object value) throws ReflectiveOperationException {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
	private static Object get(final Class<?> type, final Object target, final String name) throws ReflectiveOperationException {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}
	private static void check(final boolean condition, final String message) {
		assertions++;
		if (!condition) throw new AssertionError(message);
	}
}
