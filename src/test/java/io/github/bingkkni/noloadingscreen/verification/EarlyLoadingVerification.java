package io.github.bingkkni.noloadingscreen.verification;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.bingkkni.noloadingscreen.LoadingWaitLoop;
import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig;
import io.github.bingkkni.noloadingscreen.PlaceholderRegistries;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import io.github.bingkkni.noloadingscreen.SavingWorldView;
import io.github.bingkkni.noloadingscreen.mixin.ConnectScreenAccessor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.util.Util;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import sun.misc.Unsafe;

/** Actual transformed early hooks, registry decoding and wait ownership, without a window/socket. */
final class EarlyLoadingVerification {
	private static int assertions;

	static void run() throws ReflectiveOperationException {
		verifyRegistries();
		verifyInitialConnection();
		verifyResourceWait();
		verifyLocalClockAndInput();
		verifyInputChaining();
		verifyDebugGuard();
		System.out.println("EarlyLoadingVerification: " + assertions + " assertions passed (vanilla registry decoding, encryption trigger, connection ownership, resource wait and local clocks/input)." );
	}

	private static void verifyRegistries() throws ReflectiveOperationException {
		long tagCount = BuiltInRegistries.ITEM.getTags().count();
		Method load = PlaceholderRegistries.class.getDeclaredMethod("load");
		load.setAccessible(true);
		RegistryAccess.Frozen registries = (RegistryAccess.Frozen) load.invoke(null);
		check(registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD).isBound(), "Early overworld dimension is bound");
		check(registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS).isBound(), "Early plains biome is bound");
		check(registries.lookupOrThrow(Registries.WORLD_CLOCK).size() > 0 && registries.lookupOrThrow(Registries.TIMELINE).size() > 0,
			"26.2 clock and timeline registries exist before any WorldStem");
		check(registries.lookupOrThrow(Registries.DAMAGE_TYPE).size() > 0, "Real LocalPlayer damage-source construction has its registry");
		check(registries.lookupOrThrow(Registries.ITEM) != BuiltInRegistries.ITEM && BuiltInRegistries.ITEM.getTags().count() == tagCount,
			"Private loading does not replace or rebind static item registries/tags");
		check(registries.lookupOrThrow(Registries.ITEM).getTags().count() > 0
			&& registries.lookupOrThrow(Registries.ITEM).getTags().allMatch(net.minecraft.core.HolderSet.Named::isBound),
			"Private static tags are bound without changing global holders");
		set(PlaceholderRegistries.class, null, "loading", new CompletableFuture<RegistryAccess.Frozen>());
		check(PlaceholderRegistries.ready() == null, "Unfinished warm-up never joins a Future on the client thread");
		set(PlaceholderRegistries.class, null, "loading", CompletableFuture.completedFuture(registries));
		check(PlaceholderRegistries.ready() == registries, "Cached early registries do not reload per frame");
	}

	private static void verifyInitialConnection() throws ReflectiveOperationException {
		Minecraft minecraft = Minecraft.getInstance();
		ConnectScreen owner = allocate(ConnectScreen.class);
		ConnectScreenAccessor access = (ConnectScreenAccessor) owner;
		FakeConnection connection = new FakeConnection();
		set(ConnectScreen.class, owner, "connection", connection);
		set(ConnectScreen.class, owner, "parent", allocate(TitleScreen.class));
		NoLoadingScreen.onScreenChanging(owner);
		check(NoLoadingScreen.canRevealConfigScreen() && !PlaceholderWorld.active(), "Initial owner is retained before encryption without opening a placeholder");
		set(Gui.class, minecraft.gui, "screen", new GenericMessageScreen(Component.literal("Early pack prompt")));
		NoLoadingScreen.tickPlaceholder();
		check(connection.ticks == 1, "Prompt before the first encryption tick cannot orphan the initial connection");
		connection.ticks = 0;
		Method status = ConnectScreen.class.getDeclaredMethod("updateStatus", Component.class);
		status.setAccessible(true);
		status.invoke(owner, Component.translatable("connect.authorizing"));
		check(!flag(owner, "nls$earlyPlaceholder"), "Authorizing/login is not the encryption trigger");
		status.invoke(owner, Component.translatable("connect.encrypting"));
		check(flag(owner, "nls$earlyPlaceholder") && !PlaceholderWorld.active(), "Off-thread encryption status only publishes a flag");
		ConnectScreen offline = allocate(ConnectScreen.class);
		status.invoke(offline, Component.translatable("connect.joining"));
		check(flag(offline, "nls$earlyPlaceholder"), "Offline servers skipping encryption have a joining fallback");

		// Mark construction already attempted so these are pure ownership tests without a GPU.
		set(NoLoadingScreen.class, null, "suppressedConnectScreen", owner);
		set(NoLoadingScreen.class, null, "connectPlaceholderAttempted", true);
		set(Gui.class, minecraft.gui, "screen", null);
		NoLoadingScreen.tickPlaceholder();
		check(connection.ticks == 1, "Hidden initial connection ticks independently of pendingConnection");
		set(Gui.class, minecraft.gui, "screen", new GenericMessageScreen(Component.literal("Pack prompt")));
		NoLoadingScreen.tickPlaceholder();
		check(connection.ticks == 2, "Resource-pack prompts cannot starve the hidden connection");
		set(Gui.class, minecraft.gui, "screen", owner);
		NoLoadingScreen.tickPlaceholder();
		check(connection.ticks == 2, "Mounted owner is not double-ticked");
		owner.tick();
		check(connection.ticks == 3, "Vanilla mounted owner still ticks once");
		NoLoadingScreen.onScreenChanging(owner);
		check(!access.nls$aborted(), "Remounting fallback does not cancel its own connection");
		Method returnGui = Arrays.stream(Gui.class.getDeclaredMethods())
			.filter(m -> m.getName().contains("nls$returnToPlaceholder")).findFirst().orElseThrow();
		returnGui.setAccessible(true);
		returnGui.invoke(minecraft.gui, null, (Operation<Void>) args -> {
			check(args[0] == owner, "Null-parent prompt after placeholder failure returns to its live owner, not the title screen");
			return null;
		});
		NoLoadingScreen.onLoginStart();
		check(connection.connected && !NoLoadingScreen.canRevealConfigScreen(), "Successful play login releases ownership without closing socket");

		set(NoLoadingScreen.class, null, "suppressedConnectScreen", owner);
		set(Gui.class, minecraft.gui, "screen", null);
		connection.connected = false;
		NoLoadingScreen.tickPlaceholder();
		check(connection.disconnections == 1, "Initial login failures retain vanilla disconnect handling");
		connection.connected = true;
		var future = new io.netty.channel.DefaultChannelPromise(new io.netty.channel.embedded.EmbeddedChannel());
		access.nls$setChannelFuture(future);
		NoLoadingScreen.onDisconnected();
		check(access.nls$aborted() && future.isCancelled() && access.nls$channelFuture() == null && !connection.connected,
			"Cancellation closes socket AND marks/cancels the in-flight connector");
		((io.netty.channel.embedded.EmbeddedChannel) future.channel()).finishAndReleaseAll();
		int ticks = connection.ticks;
		owner.tick();
		check(!NoLoadingScreen.canRevealConfigScreen() && connection.ticks == ticks, "Stale aborted status cannot resurrect a placeholder");
		set(Gui.class, minecraft.gui, "screen", null);
	}

	private static void verifyResourceWait() throws ReflectiveOperationException {
		Minecraft minecraft = Minecraft.getInstance();
		Method wait = method(WorldOpenFlows.class, "nls$interactiveResourceWait");
		Object flows = allocate(WorldOpenFlows.class);
		BooleanSupplier done = () -> true;
		AtomicInteger calls = new AtomicInteger();
		Operation<Void> passthrough = args -> {
			check(args[0] == minecraft && args[1] == done && !LoadingWaitLoop.active(), "Unrelated registry loads retain exact vanilla wait");
			calls.incrementAndGet();
			return null;
		};
		wait.invoke(flows, minecraft, done, passthrough);
		check(calls.get() == 1, "Disabled/unscoped wait is called once");
		Screen resources = new GenericMessageScreen(Component.translatable("selectWorld.resource_load"));
		set(NoLoadingScreen.class, null, "resourceScreen", resources);
		set(NoLoadingScreen.class, null, "resourcePlaceholderAttempted", true);
		Operation<Void> completed = args -> {
			check(LoadingWaitLoop.active(), "Resource wait owns a separate clock");
			check(((BooleanSupplier) args[1]).getAsBoolean(), "Already-complete future skips input/render, including headless use");
			check(minecraft.level == null && minecraft.player == null, "Resource completion work is not bound to a fake player");
			return null;
		};
		wait.invoke(flows, minecraft, done, completed);
		check(!LoadingWaitLoop.active(), "Successful resource wait releases local clock");
		try {
			wait.invoke(flows, minecraft, done, (Operation<Void>) args -> { throw new IllegalStateException("resource failure"); });
			throw new AssertionError("Resource errors must propagate");
		} catch (java.lang.reflect.InvocationTargetException expected) {
			check(expected.getCause() instanceof IllegalStateException && !LoadingWaitLoop.active(), "Resource exception preserves vanilla failure path and releases clock");
		}
		NoLoadingScreen.onScreenChanging(new GenericMessageScreen(Component.literal("Datapack error/backup warning")));
		check(!NoLoadingScreen.preparingResources(), "Failure/warning screens release early resource ownership");
		NoLoadingScreenConfig.get().enabled = false;
		set(Gui.class, minecraft.gui, "screen", resources);
		NoLoadingScreen.onPreparingResources();
		check(!NoLoadingScreen.preparingResources(), "Disabled mod does not enter early loading");
		NoLoadingScreenConfig.get().enabled = true;
		set(Gui.class, minecraft.gui, "screen", null);
	}

	private static void verifyLocalClockAndInput() throws ReflectiveOperationException {
		Minecraft minecraft = Minecraft.getInstance();
		set(Minecraft.class, minecraft, "gameThread", Thread.currentThread());
		DeltaTracker.Timer pausedVanilla = new DeltaTracker.Timer(20, Util.getMillis(), ms -> ms);
		pausedVanilla.updatePauseState(true);
		check(LoadingWaitLoop.renderTime(pausedVanilla) == pausedVanilla, "Normal render clock is unchanged");
		LoadingWaitLoop.begin();
		try {
			DeltaTracker.Timer local = (DeltaTracker.Timer) LoadingWaitLoop.renderTime(pausedVanilla);
			long origin = (long) get(DeltaTracker.Timer.class, local, "lastMs");
			check(local.advanceGameTime(origin + 75) == 1 && local.getGameTimeDeltaPartialTick(true) == .5F,
				"Save movement/interpolation advances even when the outgoing game was paused");
			check(pausedVanilla.getGameTimeDeltaPartialTick(true) == 0, "Local clock cannot unpause or advance vanilla gameplay");
			LoadingWaitLoop.begin();
			LoadingWaitLoop.end();
			check(LoadingWaitLoop.renderTime(pausedVanilla) == local, "Nested scopes restore the outer clock");
			AtomicInteger input = new AtomicInteger();
			set(LoadingWaitLoop.class, null, "pollingInput", true);
			LoadingWaitLoop.dispatchInput(minecraft, input::incrementAndGet, args -> {
				throw new AssertionError("Polled local input must not be deferred behind the synchronous wait");
			});
			check(input.get() == 1 && minecraft.level == null, "Polled input runs immediately without binding or draining unrelated tasks");
		} finally {
			set(LoadingWaitLoop.class, null, "pollingInput", false);
			LoadingWaitLoop.end();
		}
		check(!LoadingWaitLoop.active() && LoadingWaitLoop.renderTime(pausedVanilla) == pausedVanilla, "Local wait clock never leaks into live gameplay");
		check(!SavingWorldView.saving(), "Save ownership was released by previous lifecycle tests");
	}

	private static void verifyInputChaining() throws ReflectiveOperationException {
		Minecraft minecraft = Minecraft.getInstance();
		for (Class<?> target : new Class<?>[]{net.minecraft.client.MouseHandler.class, net.minecraft.client.KeyboardHandler.class}) {
			Object handler = allocate(target);
			Method dispatch = method(target, "nls$dispatchWaiting");
			AtomicInteger inputs = new AtomicInteger();
			AtomicInteger delegated = new AtomicInteger();
			Runnable input = inputs::incrementAndGet;
			Operation<Void> original = args -> {
				check(args[0] == minecraft && args[1] == input, "Input chain receives the exact original client and runnable");
				delegated.incrementAndGet();
				return null;
			};
			try {
				dispatch.invoke(handler, minecraft, input, original);
				check(delegated.get() == 1 && inputs.get() == 0, "Normal input retains other mods' scheduling, not Minecraft.execute directly");
				LoadingWaitLoop.begin();
				dispatch.invoke(handler, minecraft, input, original);
				check(delegated.get() == 2 && inputs.get() == 0, "Active wait alone cannot bypass other mods outside its event poll");
				set(LoadingWaitLoop.class, null, "pollingInput", true);
				dispatch.invoke(handler, minecraft, input, original);
				check(delegated.get() == 2 && inputs.get() == 1, "Client-thread polled input runs exactly once without deferral");
				set(Minecraft.class, minecraft, "gameThread", new Thread(() -> {}, "unused-input-test-thread"));
				dispatch.invoke(handler, minecraft, input, original);
				check(delegated.get() == 3 && inputs.get() == 1, "Off-thread input still uses the original chain, even during a poll");
			} finally {
				set(Minecraft.class, minecraft, "gameThread", Thread.currentThread());
				set(LoadingWaitLoop.class, null, "pollingInput", false);
				LoadingWaitLoop.end();
			}
		}
	}

	private static void verifyDebugGuard() throws ReflectiveOperationException {
		Object keyboard = allocate(net.minecraft.client.KeyboardHandler.class);
		Method debug = net.minecraft.client.KeyboardHandler.class.getDeclaredMethod("handleDebugKeys", net.minecraft.client.input.KeyEvent.class);
		debug.setAccessible(true);
		set(PlaceholderWorld.class, null, "installed", true);
		try {
			check((boolean) debug.invoke(keyboard, new net.minecraft.client.input.KeyEvent(78, 0, 0)),
				"Raw F3 actions are consumed before accessing partial teardown fields or sending game requests");
			Method friends = Minecraft.class.getDeclaredMethod("toggleFriendsScreen");
			friends.setAccessible(true);
			check((boolean) friends.invoke(Minecraft.getInstance()), "Friends hotkey cannot nest another join inside a loading/save wait");
		} finally {
			set(PlaceholderWorld.class, null, "installed", false);
		}
	}

	private static boolean flag(final Object target, final String fragment) throws ReflectiveOperationException {
		Field field = Arrays.stream(target.getClass().getDeclaredFields()).filter(f -> f.getName().contains(fragment)).findFirst().orElseThrow();
		field.setAccessible(true);
		return field.getBoolean(target);
	}

	private static Method method(final Class<?> type, final String fragment) {
		Method method = Arrays.stream(type.getDeclaredMethods()).filter(m -> m.getName().contains(fragment) && m.getParameterCount() == 3).findFirst().orElseThrow();
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

	private static final class FakeConnection extends Connection {
		boolean connected = true;
		int ticks;
		int disconnections;
		FakeConnection() { super(PacketFlow.CLIENTBOUND); }
		@Override public boolean isConnected() { return connected; }
		@Override public void tick() { ticks++; }
		@Override public void handleDisconnection() { disconnections++; }
		@Override public void disconnect(final Component reason) { connected = false; }
	}
}
