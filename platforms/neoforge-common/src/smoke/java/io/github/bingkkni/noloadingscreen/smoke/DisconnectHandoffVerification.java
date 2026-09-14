package io.github.bingkkni.noloadingscreen.smoke;

import io.github.bingkkni.noloadingscreen.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import sun.misc.Unsafe;

/** Executes vanilla's transformed disconnect on a live in-memory scene, never a save/socket/server thread. */
public final class DisconnectHandoffVerification {
	private static Scenario current;
	private static int assertions;
	private static final IllegalStateException SAVE_FAILURE = new IllegalStateException("expected isolated save-wait failure");

	public static void run(Minecraft client) throws Exception {
		NeoForge.EVENT_BUS.addListener(DisconnectHandoffVerification::logout);
		NeoForge.EVENT_BUS.addListener(DisconnectHandoffVerification::unload);
		String selected = System.getProperty("nls.verify.handoffCase", "all");
		if (!selected.equals("kick")) {
			scenario(client, "save", true, true, false, false, false);
			if (selected.equals("all")) {
				scenario(client, "disabled-save", true, false, false, false, false);
				scenario(client, "refused-save", true, true, true, false, false);
				scenario(client, "failed-save", true, true, false, true, false);
			}
		}
		if (!selected.equals("save")) {
			scenario(client, "kick", false, true, false, false, false);
			if (selected.equals("all")) {
				scenario(client, "disabled-kick", false, false, false, false, false);
				scenario(client, "refused-kick", false, true, true, false, false);
				scenario(client, "explicit-exit", false, true, false, false, true);
			}
		}
		NoLoadingScreen.LOGGER.info("DisconnectHandoffVerification PASSED: {} assertions on transformed live-level save/kick/disabled/refusal/failure/explicit-exit ordering; no save/server thread", assertions);
	}

	private static void scenario(Minecraft client, String name, boolean integrated, boolean enabled, boolean refuse,
		boolean fail, boolean explicit) throws Exception {
		NoLoadingScreen.onDisconnected();
		NoLoadingScreenConfig.get().enabled = true;
		set(PlaceholderWorld.class, null, "renderFailures", 0);
		client.setScreen(new TitleScreen());
		check(PlaceholderWorld.synthesise(PlaceholderRegistries.ready(), null, null, null, new Vec3(.5, 80, .5), 0, 0, false), "Fixture scene construction");
		check(PlaceholderWorld.bind(), "Fixture scene binding");
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		MultiPlayerGameMode mode = client.gameMode;
		PlaceholderWorld.unbind();
		// Promote the initialized memory scene to a LIVE owner. Do not detach engines or pre-null
		// the fields: the regression must exercise actual mapped disconnect ordering from entry.
		set(PlaceholderWorld.class, null, "installed", false);
		set(PlaceholderWorld.class, null, "synthetic", false);
		set(PlaceholderWorld.class, null, "level", null);
		set(PlaceholderWorld.class, null, "player", null);
		set(PlaceholderWorld.class, null, "gameMode", null);
		client.level = level; client.player = player; client.gameMode = mode;
		set(Minecraft.class, client, "isLocalServer", integrated);
		if (integrated) set(Minecraft.class, client, "singleplayerServer", allocate(ShutdownProbe.class));
		NoLoadingScreenConfig.get().enabled = enabled;
		if (refuse) set(PlaceholderWorld.class, null, "renderFailures", 3);
		Screen target = integrated || explicit ? new TitleScreen() : new DisconnectedScreen(new TitleScreen(),
			Component.literal("Fixture kick"), Component.literal("Original rich disconnect reason"));
		Scenario test = new Scenario(name, client, level, player, mode, player.connection.getConnection(), integrated,
			enabled && !refuse && !explicit, enabled, fail, target);
		current = test;
		try {
			check(client.level == level && client.player == player && client.gameMode == mode, name + ": must start with live fields");
			try {
				client.disconnect(target, false);
				check(!fail, name + ": save failure was swallowed");
			} catch (IllegalStateException error) {
				check(fail && error == SAVE_FAILURE, name + ": original exception identity must propagate");
			}
			check(test.events.subList(0, 2).equals(List.of("logout", "unload")), name + ": native event order");
			check(test.reverts == (fail ? 0 : 1), name + ": registry decision survives early pointer release; vanilla failure skips revert");
			check(!(boolean) field(Minecraft.class, client, "clientLevelTeardownInProgress"), name + ": native teardown finally cleared");
			check(!SavingWorldView.saving() && !LoadingWaitLoop.active(), name + ": save finally releases scene/clock");
			check(client.level == null && client.player == null && client.gameMode == null, name + ": no client field leakage");
			if (!integrated && test.adopts) {
				check(DisconnectedWorldView.visible() && client.screen == null, name + ": KickWarn scene replaces disconnect screen");
				check(test.detaches == 0, name + ": retain loaded meshes through kick teardown");
				check(player.connection.getConnection() != test.connection && player.connection.getConnection().channel() == null,
					name + ": adopted listener is isolated on a new channel-less connection");
				check(PlaceholderWorld.bind(), name + ": retained scene can render");
				try { check(client.level == level && client.player == player && client.gameMode == mode, name + ": exact outgoing identities retained"); }
				finally { PlaceholderWorld.unbind(); }
			} else {
				check(!PlaceholderWorld.active() && !DisconnectedWorldView.active(), name + ": vanilla/failure path retains no placeholder");
				if (!fail) check(client.screen == target, name + ": original final screen remains usable");
			}
			NoLoadingScreen.LOGGER.info("Disconnect fixture {} passed: {}", name, test.events);
		} finally {
			current = null;
			NoLoadingScreenConfig.get().enabled = true;
			NoLoadingScreen.onDisconnected();
			set(PlaceholderWorld.class, null, "renderFailures", 0);
			set(Minecraft.class, client, "singleplayerServer", null);
			set(Minecraft.class, client, "isLocalServer", false);
		}
	}

	private static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
		Scenario test = current; if (test == null) return;
		check(test.client.level == test.level && event.getPlayer() == test.player && event.getMultiPlayerGameMode() == test.mode,
			test.name + ": logout observes original live ownership");
		check(event.getConnection() == test.connection, test.name + ": no listener replacement before logout");
		test.events.add("logout");
	}
	private static void unload(LevelEvent.Unload event) {
		Scenario test = current; if (test == null) return;
		check(event.getLevel() == test.level && test.client.level == test.level && test.client.player == test.player && test.client.gameMode == null,
			test.name + ": unload observes vanilla's live-level/cleared-mode state");
		check(test.player.connection.getConnection() == test.connection, test.name + ": no listener replacement before unload");
		test.events.add("unload");
	}
	public static void revertingRegistries() {
		Scenario test = current; if (test == null) return;
		check(test.client.level == null && test.client.player == null && test.client.gameMode == null, test.name + ": registry revert remains after native field teardown");
		test.events.add("registries"); test.reverts++;
	}
	public static void detachingLevel(ClientLevel next) {
		if (current != null && next == null) { current.detaches++; current.events.add("detach"); }
	}
	private static final class ShutdownProbe extends IntegratedServer {
		private int polls;
		private ShutdownProbe() { super(null, null, null, null, null, null, null); }
		@Override public boolean isShutdown() {
			Scenario test = current;
			check(test.events.contains("unload"), test.name + ": save polling follows unload event");
			check(SavingWorldView.visible() == test.adopts, test.name + ": saving placeholder must install from a still-live outgoing level");
			check(LoadingWaitLoop.active() == test.adopts, test.name + ": save clock matches scene ownership");
			check(test.detaches == 0, test.name + ": no mesh detach before/during save wait");
			if (test.adopts) {
				check(test.client.level == null && test.client.player == null && test.client.gameMode == null, test.name + ": wait runs unbound");
				check(test.client.screen == null, test.name + ": saving screen hidden only after adoption");
				check(test.player.connection.getConnection() != test.connection && test.player.connection.getConnection().channel() == null,
					test.name + ": save listener isolation");
			} else check(SavingWorldView.isSavingScreen(test.client.screen), test.name + ": failed/disabled save keeps vanilla screen");
			if (!test.enabled) check(test.client.level == test.level && test.client.player == test.player, test.name + ": disabled keeps vanilla live-field ordering");
			test.events.add("wait");
			if (test.fail) throw SAVE_FAILURE;
			return polls++ > 0;
		}
	}
	private static final class Scenario {
		final String name; final Minecraft client; final ClientLevel level; final LocalPlayer player; final MultiPlayerGameMode mode;
		final Connection connection; final boolean integrated, adopts, enabled, fail; final Screen target;
		final List<String> events = new ArrayList<>(); int detaches, reverts;
		Scenario(String name, Minecraft client, ClientLevel level, LocalPlayer player, MultiPlayerGameMode mode, Connection connection,
			boolean integrated, boolean adopts, boolean enabled, boolean fail, Screen target) {
			this.name=name; this.client=client; this.level=level; this.player=player; this.mode=mode; this.connection=connection;
			this.integrated=integrated; this.adopts=adopts; this.enabled=enabled; this.fail=fail; this.target=target;
		}
	}
	private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
	private static Object field(Class<?> type, Object owner, String name) throws Exception { Field f=type.getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
	private static void set(Class<?> type, Object owner, String name, Object value) throws Exception { Field f=type.getDeclaredField(name); f.setAccessible(true); f.set(owner,value); }
	private static <T> T allocate(Class<T> type) throws Exception { Field f=Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); return type.cast(((Unsafe)f.get(null)).allocateInstance(type)); }
}
