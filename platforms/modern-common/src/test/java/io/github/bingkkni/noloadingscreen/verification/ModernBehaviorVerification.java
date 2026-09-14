package io.github.bingkkni.noloadingscreen.verification;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.bingkkni.noloadingscreen.DisconnectedWorldView;
import io.github.bingkkni.noloadingscreen.LoadingWaitLoop;
import io.github.bingkkni.noloadingscreen.LoadingWork;
import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig;
import io.github.bingkkni.noloadingscreen.PlaceholderRegistries;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import io.github.bingkkni.noloadingscreen.SavingWorldView;
import io.github.bingkkni.noloadingscreen.gui.LoadingHud;
import io.github.bingkkni.noloadingscreen.gui.LoadingPauseScreen;
import io.github.bingkkni.noloadingscreen.platform.ClientRuntime;
import io.github.bingkkni.noloadingscreen.platform.ClientUi;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sun.misc.Unsafe;

/**
 * Native CLIENT-loader fixtures, called after Bootstrap.bootStrap(). No window, sockets or config I/O.
 * Real vanilla tracker/screen/HUD methods run. Disconnect reaches a controlled pre-render failure
 * through its public entrypoint, plus transformed adoption callbacks and the outer wrapper; this
 * does not exercise complete renderer/event/registry teardown or successful scene adoption.
 */
public final class ModernBehaviorVerification {
    private static final Unsafe UNSAFE = unsafe();
    private static int assertions;

    private ModernBehaviorVerification() {}

    public static void run() throws Exception {
        assertions = 0;
        // This fixture replaces the singleton, so refuse to run against an active game session.
        check(!PlaceholderWorld.active() && !LoadingWaitLoop.active() && !SavingWorldView.saving()
            && !DisconnectedWorldView.active() && !NoLoadingScreen.canRevealConfigScreen(), "Fixture requires an idle CLIENT bootstrap");
        try (StaticState saved = new StaticState(Minecraft.class, NoLoadingScreenConfig.class,
                NoLoadingScreen.class, PlaceholderWorld.class, LoadingWork.class, LoadingHud.class,
                SavingWorldView.class, DisconnectedWorldView.class, PlaceholderRegistries.class)) {
            // Never call get() before replacing instance: lazy loading can create a config file.
            NoLoadingScreenConfig config = new NoLoadingScreenConfig();
            set(NoLoadingScreenConfig.class, null, "instance", config);
            Minecraft client = allocate(Minecraft.class);
            set(Minecraft.class, null, "instance", client);
            set(Minecraft.class, client, "gameThread", Thread.currentThread());
            set(Minecraft.class, client, "gui", allocate(Gui.class));
            set(Gui.class, client.gui, "minecraft", client);
            set(Minecraft.class, client, "options", allocate(Options.class));
            set(Minecraft.class, client, "gameRenderer", allocate(GameRenderer.class));
            set(GameRenderer.class, client.gameRenderer, "mainCamera", new Camera());
            set(PlaceholderRegistries.class, null, "loading", CompletableFuture.completedFuture(null));
            NoLoadingScreen.onDisconnected();
            verifyConfigurationOwner(client);
            verifyReadyGate(client, config);
            verifyBindings(client);
            verifyHudTick(client);
            verifyDisconnectBoundaries(client, config);
            check(!PlaceholderWorld.active() && !LoadingWaitLoop.active() && !DisconnectedWorldView.active()
                && !NoLoadingScreen.canRevealConfigScreen(), "Fixture leaves no lifecycle ownership behind");
        }
        System.out.println("ModernBehaviorVerification: " + assertions
            + " assertions passed (configuration ownership, vanilla readiness, scoped fields, real HUD timer, disconnect boundaries)");
    }

    private static void verifyConfigurationOwner(Minecraft client) throws Exception {
        TestConnection connection = new TestConnection();
        ServerReconfigScreen owner = new ServerReconfigScreen(Component.literal("configuration fixture"), connection);
        set(NoLoadingScreen.class, null, "suppressedConfigScreen", owner);
        NoLoadingScreen.tickPlaceholder();
        check(connection.ticks == 1, "Hidden vanilla configuration screen ticks its connection");
        Screen prompt = allocate(LoadingPauseScreen.class);
        mount(client, prompt);
        NoLoadingScreen.tickPlaceholder();
        check(connection.ticks == 2 && NoLoadingScreen.canRevealConfigScreen(), "Pause screen does not take connection ownership");
        mount(client, new TestScreen());
        NoLoadingScreen.tickPlaceholder();
        check(connection.ticks == 3 && NoLoadingScreen.waitingScreen() == owner, "Unrelated prompt retains the exact configuration owner");
        mount(client, owner);
        NoLoadingScreen.tickPlaceholder();
        check(connection.ticks == 3, "Mounted vanilla owner is not double-ticked");
        owner.tick();
        check(connection.ticks == 4, "Mounted owner still advances through its normal vanilla tick");
        mount(client, prompt);
        Component reason = Component.literal("original backend reason");
        connection.disconnect(reason);
        NoLoadingScreen.tickPlaceholder();
        check(connection.handled == 1 && connection.reason == reason, "Kick is delivered immediately, before the 600-tick button delay, with original reason");
        NoLoadingScreen.onDisconnected();
        check(connection.closes == 1 && !NoLoadingScreen.canRevealConfigScreen(), "Cleanup releases failed ownership without replacing its reason");

        connection = new TestConnection();
        owner = new ServerReconfigScreen(Component.literal("login fixture"), connection);
        set(NoLoadingScreen.class, null, "suppressedConfigScreen", owner);
        NoLoadingScreen.onLoginStart();
        NoLoadingScreen.tickPlaceholder();
        check(connection.connected && connection.closes == 0 && connection.ticks == 0
            && !NoLoadingScreen.canRevealConfigScreen(), "Successful login releases configuration ownership without closing or ticking the live connection");
        set(NoLoadingScreen.class, null, "suppressedConfigScreen", owner);
        client.level = allocate(ClientLevel.class);
        NoLoadingScreen.tickPlaceholder();
        check(connection.ticks == 0 && !NoLoadingScreen.canRevealConfigScreen(), "Arriving real level relinquishes hidden ownership before ticking");
        client.level = null;
        set(NoLoadingScreen.class, null, "suppressedConfigScreen", owner);
        NoLoadingScreen.onScreenChanging(allocate(TitleScreen.class));
        check(!connection.connected && connection.closes == 1 && !NoLoadingScreen.canRevealConfigScreen(), "Explicit menu exit closes abandoned configuration exactly once");
        NoLoadingScreen.onDisconnected();
        check(connection.closes == 1, "Repeated disconnect cleanup is idempotent");
        mount(client, null);
    }

    private static void verifyReadyGate(Minecraft client, NoLoadingScreenConfig config) throws Exception {
        TestPlayer player = allocate(TestPlayer.class);
        TestLevel level = allocate(TestLevel.class);
        set(Entity.class, player, "blockPosition", BlockPos.ZERO);
        set(Entity.class, player, "position", net.minecraft.world.phys.Vec3.ZERO); // Sodium reads the vector, vanilla reads the cached BlockPos
        LevelLoadTracker idle = new LevelLoadTracker();
        idle.tickClientLoad();
        check(!idle.isLevelReady(), "Enabled gate does not invent a load on an idle tracker");
        config.enabled = false;
        LevelLoadTracker waiting = startTracker(player, level, 0);
        Object initial = get(LevelLoadTracker.class, waiting, "clientState");
        waiting.tickClientLoad();
        check(!waiting.isLevelReady() && get(LevelLoadTracker.class, waiting, "clientState") == initial,
            "Disabled gate preserves vanilla WaitingForServer by identity");
        config.enabled = true;
        waiting.tickClientLoad();
        check(waiting.isLevelReady(), "Enabling mid-wait advances the real vanilla state machine in one tick");
        check((boolean) get(NoLoadingScreen.class, null, "gateReleased"), "Ready release notifies shared lifecycle before completion");
        Object ready = get(LevelLoadTracker.class, waiting, "clientState");
        check(ready.getClass().getSimpleName().equals("ClientLevelReady"), "Readiness uses vanilla's terminal state, not a replaced query result");
        waiting.loadingPacketsReceived();
        waiting.tickClientLoad();
        check(waiting.isLevelReady() && get(LevelLoadTracker.class, waiting, "clientState") == ready,
            "Late server loading signal and subsequent ticks do not restart a ready tracker");
        check(get(NoLoadingScreen.class, null, "overlayTracker") == waiting, "Early readiness retains the real overlay tracker");

        LevelLoadTracker delayed = startTracker(player, level, 60_000);
        check(!(boolean) get(NoLoadingScreen.class, null, "gateReleased"), "New load resets the prior gate release");
        delayed.tickClientLoad();
        check(!delayed.isLevelReady(), "Vanilla close delay is not bypassed by early release");
        Object delayedReady = get(LevelLoadTracker.class, delayed, "clientState");
        check(delayedReady.getClass().getSimpleName().equals("ClientLevelReady"), "Delayed gate reaches readiness but waits to report it");
        // Move vanilla's timestamp, not wall-clock time; no sleeps or timeout-dependent assertions.
        var constructor = delayedReady.getClass().getDeclaredConstructor(long.class);
        constructor.setAccessible(true);
        set(LevelLoadTracker.class, delayed, "clientState", constructor.newInstance(ClientRuntime.millis() - 60_001L));
        check(delayed.isLevelReady(), "Expired vanilla close delay reports readiness normally");
        config.enabled = false;
        LevelLoadTracker disabledAgain = startTracker(player, level, 0);
        disabledAgain.tickClientLoad();
        check(!disabledAgain.isLevelReady(), "Disabling again restores the server wait for the next load");
        config.enabled = true;
        set(NoLoadingScreen.class, null, "holdActive", true);
        set(NoLoadingScreen.class, null, "heldPlayer", player);
        NoLoadingScreen.onDisconnected();
        check(!(boolean) get(NoLoadingScreen.class, null, "gateReleased")
            && !(boolean) get(NoLoadingScreen.class, null, "holdActive")
            && get(NoLoadingScreen.class, null, "heldPlayer") == null
            && get(NoLoadingScreen.class, null, "overlayTracker") == null && !NoLoadingScreen.timelineActive(),
            "Disconnect resets gate, hold, overlay and join timeline");
        check(client.level == null && client.player == null, "Tracker fixture never mounts a live world/player");
    }

    private static LevelLoadTracker startTracker(LocalPlayer player, ClientLevel level, long delay) throws Exception {
        LevelLoadTracker tracker = new LevelLoadTracker(delay);
        Method start = uniqueMethod(LevelLoadTracker.class, "startClientLoad");
        Object[] args = new Object[start.getParameterCount()];
        args[0] = player;
        args[1] = level;
        // Remaining API-selected renderer input stays null: an enabled gate must not query meshes.
        invoke(start, tracker, args);
        return tracker;
    }

    private static void verifyBindings(Minecraft client) throws Exception {
        Scene scene = scene();
        ClientLevel realLevel = allocate(ClientLevel.class);
        LocalPlayer realPlayer = allocate(LocalPlayer.class);
        MultiPlayerGameMode realMode = allocate(MultiPlayerGameMode.class);
        client.level = realLevel;
        client.player = realPlayer;
        client.gameMode = realMode;
        check(!PlaceholderWorld.bind(), "Outermost bind refuses another live world");
        check(client.level == realLevel && client.player == realPlayer && client.gameMode == realMode,
            "Refused binding leaves all three real fields untouched");
        client.level = null;
        check(!PlaceholderWorld.owns(scene.player), "Installed but unbound disposable player is not locally mutable");
        check(PlaceholderWorld.bind(), "Outer bind succeeds in the teardown gap");
        assertBound(client, scene);
        check(PlaceholderWorld.owns(scene.player) && !PlaceholderWorld.owns(realPlayer), "Local mutations require the exact bound disposable player");
        client.player = null;
        client.gameMode = null;
        check(PlaceholderWorld.bind(), "Nested bind succeeds even after disturbed partial fields");
        assertBound(client, scene);
        client.player = realPlayer;
        PlaceholderWorld.unbind();
        assertBound(client, scene);
        PlaceholderWorld.unbind();
        check(client.level == null && client.player == realPlayer && client.gameMode == realMode,
            "Outermost unbind restores exact partial vanilla teardown state");
        check(!PlaceholderWorld.owns(scene.player), "Local mutation permission ends with its binding");
        PlaceholderWorld.bind();
        PlaceholderWorld.bind();
        PlaceholderWorld.releaseAll();
        check(client.level == null && client.player == realPlayer && client.gameMode == realMode
            && (int) get(PlaceholderWorld.class, null, "bindDepth") == 0, "Tick leak recovery releases every nested binding");
        PlaceholderWorld.bind();
        client.level = realLevel;
        client.player = realPlayer;
        client.gameMode = realMode;
        PlaceholderWorld.unbind();
        check(client.level == realLevel && client.player == realPlayer && client.gameMode == realMode,
            "Unbind never overwrites an arriving real owner with saved fields");
        PlaceholderWorld.unbind();
        check(client.level == realLevel, "Unmatched unbind is harmless");
        clearScene(client);
    }

    private static void verifyHudTick(Minecraft client) throws Exception {
        Scene scene = scene();
        TestPlayer player = (TestPlayer) scene.player;
        player.inventory = new Inventory(player, null);
        Method wrapper = uiHook("nls$tickPlaceholderHud");
        Object hud = allocate(wrapper.getParameterTypes()[0]);
        Class<?> hudType = wrapper.getParameterTypes()[0];
        set(hudType, hud, "minecraft", client);
        set(hudType, hud, "lastToolHighlight", ItemStack.EMPTY);
        set(hudType, hud, "chat", allocate(TestChat.class));
        OptionInstance<?> display = allocate(OptionInstance.class);
        set(OptionInstance.class, display, "value", 1.0D);
        set(Options.class, client.options, "notificationDisplayTime", display);
        Method tick = hudType.getMethod("tick", boolean.class);
        AtomicInteger calls = new AtomicInteger();
        Operation<Void> vanilla = args -> {
            calls.incrementAndGet();
            check(args[0] == hud, "HUD operation receives the original receiver");
            assertBound(client, scene);
            invokeUnchecked(tick, hud, args[1]);
            return null;
        };
        Object host = wrapper.getDeclaringClass().cast(uiHost(client, wrapper.getDeclaringClass()));
        player.inventory.setItem(0, new ItemStack(Items.DIAMOND));
        player.inventory.setItem(1, new ItemStack(Items.STONE));
        invoke(wrapper, host, hud, false, vanilla);
        int timer = (int) get(hudType, hud, "toolHighlightTimer");
        check(timer == 40 && ((ItemStack) get(hudType, hud, "lastToolHighlight")).is(Items.DIAMOND),
            "Real HUD tick sees the selected local item and vanilla notification duration");
        check(client.level == null && client.player == null && client.gameMode == null, "HUD releases the player before unrelated client lifecycle work");
        invoke(wrapper, host, hud, false, vanilla);
        check((int) get(hudType, hud, "toolHighlightTimer") == timer - 1, "Unchanged item countdown advances exactly once");
        invoke(wrapper, host, hud, true, vanilla);
        check((int) get(hudType, hud, "toolHighlightTimer") == timer - 1 && calls.get() == 3,
            "Paused HUD call is delegated exactly once and retains vanilla pause semantics");
        player.inventory.setSelectedSlot(1);
        invoke(wrapper, host, hud, false, vanilla);
        check((int) get(hudType, hud, "toolHighlightTimer") == timer, "Changing local selected item restarts its name timer");
        player.inventory.setSelectedSlot(2);
        invoke(wrapper, host, hud, false, vanilla);
        check((int) get(hudType, hud, "toolHighlightTimer") == 0, "Empty slot clears a still-active name immediately");
        player.inventory.setSelectedSlot(0);
        invoke(wrapper, host, hud, false, vanilla);
        for (int i = 0; i < timer; i++) invoke(wrapper, host, hud, false, vanilla);
        check((int) get(hudType, hud, "toolHighlightTimer") == 0, "Unchanged local item name expires without requiring another slot switch");
        IllegalStateException failure = new IllegalStateException("expected HUD failure");
        expectFailure(failure, () -> invoke(wrapper, host, hud, false, (Operation<Void>) args -> { throw failure; }));
        check(client.level == null && client.player == null && client.gameMode == null
            && (int) get(PlaceholderWorld.class, null, "bindDepth") == 0, "HUD exception restores binding in finally");
        clearScene(client);
        LocalPlayer real = allocate(LocalPlayer.class);
        client.player = real;
        AtomicInteger normal = new AtomicInteger();
        invoke(wrapper, host, hud, true, (Operation<Void>) args -> {
            check(client.player == real && args[0] == hud && Boolean.TRUE.equals(args[1]), "Unowned HUD preserves fields and arguments");
            normal.incrementAndGet();
            return null;
        });
        check(normal.get() == 1 && client.player == real, "Unowned HUD delegates once without taking field ownership");
        client.player = null;
    }

    private static void verifyDisconnectBoundaries(Minecraft client, NoLoadingScreenConfig config) throws Exception {
        Method saving = uniqueMethod(Minecraft.class, "nls$showSavingWorld");
        Method finished = uniqueMethod(Minecraft.class, "nls$savingFinished");
        Method disconnect = uniqueMethod(Minecraft.class, "nls$disconnect");
        ClientLevel outgoing = allocate(ClientLevel.class);
        TestPlayer player = allocate(TestPlayer.class);
        MultiPlayerGameMode mode = allocate(MultiPlayerGameMode.class);
        var snapshotType = Class.forName("io.github.bingkkni.noloadingscreen.OutgoingWorld");
        var snapshotConstructor = snapshotType.getDeclaredConstructor(ClientLevel.class, LocalPlayer.class, MultiPlayerGameMode.class);
        snapshotConstructor.setAccessible(true);
        Object snapshot = snapshotConstructor.newInstance(outgoing, player, mode);
        Screen title = allocate(TitleScreen.class);
        DisconnectedScreen fallback = new DisconnectedScreen(title, Component.literal("disconnect fixture"), Component.literal("exact original reason"));
        // Reach adopt's real pre-render guard, then refuse without touching movement or GPU state.
        AtomicInteger adoptionChecks = new AtomicInteger();
        player.dead = true;
        player.deathQuery = () -> {
            check(client.level == null && client.gameMode == null,
                "Adoption evaluates the outgoing player only after live level/game-mode ownership is released");
            adoptionChecks.incrementAndGet();
        };
        set(PlaceholderWorld.class, null, "renderFailures", 0);
        for (boolean enabled : new boolean[]{false, true}) {
            config.enabled = enabled;
            set(SavingWorldView.class, null, "outgoing", snapshot);
            // Vanilla has cleared level/gameMode at both callback sites, but may still own player.
            client.level = null;
            client.player = player;
            client.gameMode = null;
            invoke(saving, client, title, false, false, new CallbackInfo("disconnect", false));
            check(get(SavingWorldView.class, null, "outgoing") == null && !SavingWorldView.visible()
                && !LoadingWaitLoop.active(), "Saving callback consumes refused snapshot without starting a wait clock");
            check(client.level == null && client.gameMode == null, "Saving handoff never remounts rejected live fields");
            if (!enabled) check(client.player == player, "Disabled saving handoff preserves vanilla player ownership");
            set(DisconnectedWorldView.class, null, "fallback", fallback);
            set(DisconnectedWorldView.class, null, "outgoing", snapshot);
            invoke(finished, client, fallback, false, false, new CallbackInfo("disconnect", false));
            check(get(DisconnectedWorldView.class, null, "outgoing") == null && !PlaceholderWorld.active()
                && DisconnectedWorldView.fallbackScreen() == fallback, "Disconnect callback attempts adoption while preserving exact vanilla fallback on refusal");
            DisconnectedWorldView.finish(true);
            check(!DisconnectedWorldView.active(), "Completed failed adoption clears retention instead of hiding the fallback");
        }
        check(adoptionChecks.get() == 4, "Saving and kick callbacks each reach the real adoption guard once per attempted snapshot");
        config.enabled = true;
        client.level = null;
        client.player = null;
        for (boolean fail : new boolean[]{false, true}) {
            TestConnection connection = new TestConnection();
            set(NoLoadingScreen.class, null, "suppressedConfigScreen",
                new ServerReconfigScreen(Component.literal("abandoned fixture"), connection));
            AtomicInteger calls = new AtomicInteger();
            IllegalStateException failure = new IllegalStateException("expected disconnect failure");
            Operation<Void> original = args -> {
                calls.incrementAndGet();
                check(args[0] == title && Boolean.TRUE.equals(args[1]) && Boolean.FALSE.equals(args[2]),
                    "Disconnect wrapper forwards exact screen/resource-pack/sound arguments");
                check(connection.closes == 1 && !NoLoadingScreen.canRevealConfigScreen(),
                    "Real disconnect wrapper releases old configuration ownership before invoking vanilla teardown");
                try { set(SavingWorldView.class, null, "outgoing", snapshot); }
                catch (Exception reflectionFailure) { throw new AssertionError(reflectionFailure); }
                if (fail) throw failure;
                return null;
            };
            if (fail) expectFailure(failure, () -> invoke(disconnect, client, title, true, false, original));
            else invoke(disconnect, client, title, true, false, original);
            check(calls.get() == 1 && get(SavingWorldView.class, null, "outgoing") == null
                && !LoadingWaitLoop.active(), "Disconnect finally releases pending save ownership on success and failure");
        }
        verifyRealDisconnectFailure(client, snapshot, title);
        NoLoadingScreen.onDisconnected();
    }

    private static void verifyRealDisconnectFailure(Minecraft client, Object snapshot, Screen title) throws Exception {
        TestConnection connection = new TestConnection();
        set(NoLoadingScreen.class, null, "suppressedConfigScreen",
            new ServerReconfigScreen(Component.literal("real disconnect fixture"), connection));
        Field social = field(Minecraft.class, "playerSocialManager");
        social.set(client, allocate(social.getType()));
        Field metrics = field(Minecraft.class, "metricsRecorder");
        AtomicInteger reached = new AtomicInteger();
        IllegalStateException expected = new IllegalStateException("controlled pre-render disconnect boundary");
        Object recorder = java.lang.reflect.Proxy.newProxyInstance(ModernBehaviorVerification.class.getClassLoader(),
            new Class<?>[]{metrics.getType()}, (proxy, method, args) -> {
                check(method.getName().equals("isRecording"), "Real disconnect requests only the expected metrics boundary");
                reached.incrementAndGet();
                check(connection.closes == 1 && !NoLoadingScreen.canRevealConfigScreen(),
                    "Public disconnect entrypoint executes ownership cleanup before vanilla teardown");
                set(SavingWorldView.class, null, "outgoing", snapshot);
                throw expected;
            });
        metrics.set(client, recorder);
        expectFailure(expected, () -> client.disconnect(title, true, false));
        check(reached.get() == 1 && get(SavingWorldView.class, null, "outgoing") == null
            && !LoadingWaitLoop.active(), "Actual transformed disconnect executes finally on a vanilla-path failure");
    }

    private record Scene(ClientLevel level, LocalPlayer player, MultiPlayerGameMode mode) {}

    private static Scene scene() throws Exception {
        Scene scene = new Scene(allocate(ClientLevel.class), allocate(TestPlayer.class), allocate(MultiPlayerGameMode.class));
        set(PlaceholderWorld.class, null, "installed", true);
        set(PlaceholderWorld.class, null, "level", scene.level);
        set(PlaceholderWorld.class, null, "player", scene.player);
        set(PlaceholderWorld.class, null, "gameMode", scene.mode);
        return scene;
    }

    private static void assertBound(Minecraft client, Scene scene) {
        check(client.level == scene.level && client.player == scene.player && client.gameMode == scene.mode,
            "Scoped operation sees all three disposable fields atomically");
    }

    private static void clearScene(Minecraft client) throws Exception {
        PlaceholderWorld.releaseAll();
        set(PlaceholderWorld.class, null, "installed", false);
        set(PlaceholderWorld.class, null, "level", null);
        set(PlaceholderWorld.class, null, "player", null);
        set(PlaceholderWorld.class, null, "gameMode", null);
        client.level = null;
        client.player = null;
        client.gameMode = null;
    }

    // Reflection resolves unique transformed members, never version strings or optional fallbacks.
    // Both known UI containers are inspected uniformly; ambiguity/missing hooks fail the fixture.
    private static Method uiHook(String fragment) {
        List<Method> methods = new ArrayList<>();
        for (Class<?> owner : List.of(Minecraft.class, Gui.class)) {
            methods.addAll(Arrays.stream(owner.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && m.getName().contains(fragment)).toList());
        }
        check(methods.size() == 1, "Exactly one transformed UI hook owns " + fragment);
        Method method = methods.getFirst();
        method.setAccessible(true);
        return method;
    }

    private static Object uiHost(Minecraft client, Class<?> owner) {
        return List.of(client, client.gui).stream().filter(owner::isInstance).findFirst().orElseThrow();
    }

    private static void mount(Minecraft client, Screen screen) throws Exception {
        List<Field> fields = new ArrayList<>();
        for (Class<?> owner : List.of(Minecraft.class, Gui.class)) {
            fields.addAll(Arrays.stream(owner.getDeclaredFields())
                .filter(f -> f.getName().equals("screen") && f.getType() == Screen.class).toList());
        }
        check(fields.size() == 1, "Exactly one vanilla container owns the visible screen");
        Field field = fields.getFirst();
        field.setAccessible(true);
        field.set(uiHost(client, field.getDeclaringClass()), screen);
        check(ClientUi.screen(client) == screen, "Compile-selected ClientUi observes the fixture's mounted screen");
    }

    private static Method uniqueMethod(Class<?> type, String fragment) {
        List<Method> methods = Arrays.stream(type.getDeclaredMethods())
            .filter(m -> !m.isSynthetic() && (fragment.startsWith("nls$") ? m.getName().contains(fragment) : m.getName().equals(fragment)))
            .toList();
        check(methods.size() == 1, "Exactly one method matches " + type.getName() + '.' + fragment);
        Method method = methods.getFirst();
        method.setAccessible(true);
        return method;
    }

    private static Object invoke(Method method, Object target, Object... args) throws Exception {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception exception) throw exception;
            if (failure.getCause() instanceof Error error) throw error;
            throw new AssertionError(failure.getCause());
        }
    }

    private static void invokeUnchecked(Method method, Object target, Object... args) {
        try { invoke(method, target, args); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }

    private interface CheckedAction { void run() throws Exception; }

    private static void expectFailure(Exception expected, CheckedAction action) throws Exception {
        try { action.run(); }
        catch (Exception actual) { check(actual == expected, "Original exception propagates by identity"); return; }
        throw new AssertionError("Expected exception was swallowed: " + expected);
    }

    private static final class TestConnection extends Connection {
        boolean connected = true;
        int ticks;
        int handled;
        int closes;
        Component reason;
        TestConnection() { super(PacketFlow.CLIENTBOUND); }
        @Override public boolean isConnected() { return connected; }
        @Override public void tick() {
            check(Minecraft.getInstance().level == null && Minecraft.getInstance().player == null,
                "Configuration network tick never sees a locally bound scene");
            ticks++;
        }
        @Override public void handleDisconnection() { handled++; }
        @Override public void disconnect(Component reason) { this.reason = reason; connected = false; closes++; }
    }

    private static final class TestScreen extends Screen {
        TestScreen() { super(Component.literal("unrelated prompt")); }
    }

    // Unsafe bypasses constructors requiring a real client/world; only the overridden queries run.
    private static final class TestPlayer extends LocalPlayer {
        Inventory inventory;
        boolean dead;
        Runnable deathQuery;
        private TestPlayer() { super(null, null, null, null, null, null, false, null); }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isAlive() { return true; }
        @Override public boolean isDeadOrDying() {
            if (deathQuery != null) deathQuery.run();
            return dead;
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class TestLevel extends ClientLevel {
        private TestLevel() { super(null, null, null, null, 2, 2, null, false, 0, 63); }
        @Override public int getMinY() { return -64; }
        @Override public int getHeight() { return 384; }
    }

    private static final class TestChat extends ChatComponent {
        private TestChat() { super(null); }
        @Override public void tick() {}
    }

    private static Unsafe unsafe() {
        try { Field field = field(Unsafe.class, "theUnsafe"); return (Unsafe) field.get(null); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }

    private static <T> T allocate(Class<T> type) throws InstantiationException { return type.cast(UNSAFE.allocateInstance(type)); }
    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static Object get(Class<?> type, Object target, String name) throws Exception { return field(type, name).get(target); }
    private static void set(Class<?> type, Object target, String name, Object value) throws Exception { field(type, name).set(target, value); }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }

    /** Restores fixture mutations even on assertion failure, including final phase-list contents. */
    private static final class StaticState implements AutoCloseable {
        private final List<CheckedAction> restore = new ArrayList<>();
        StaticState(Class<?>... types) throws Exception {
            for (Class<?> type : types) {
                for (Field field : type.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers())) continue;
                    if (type == Minecraft.class && !field.getName().equals("instance")) continue;
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (!Modifier.isFinal(field.getModifiers())) restore.add(() -> field.set(null, value));
                    else if (type == NoLoadingScreen.class && value instanceof List<?> list) {
                        List<?> copy = new ArrayList<>(list);
                        restore.add(() -> restoreList(list, copy));
                    }
                }
            }
            // Item defaults are not loaded in a pre-launch fixture. Bind only the two HUD
            // samples and restore their exact prior component references on exit.
            var components = net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 64).build();
            for (var item : List.of(Items.DIAMOND, Items.STONE)) {
                var holder = item.builtInRegistryHolder();
                Field componentField = field(holder.getClass(), "components");
                Object previous = componentField.get(holder);
                restore.add(() -> componentField.set(holder, previous));
                holder.bindComponents(components);
            }
        }
        @SuppressWarnings({"rawtypes", "unchecked"})
        private static void restoreList(List list, List copy) { list.clear(); list.addAll(copy); }
        @Override public void close() throws Exception {
            Exception failure = null;
            for (CheckedAction action : restore.reversed()) {
                try { action.run(); }
                catch (Exception problem) { if (failure == null) failure = problem; else failure.addSuppressed(problem); }
            }
            if (failure != null) throw failure;
        }
    }
}
