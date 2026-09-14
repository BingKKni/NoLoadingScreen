package io.github.bingkkni.noloadingscreen.verification;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.bingkkni.noloadingscreen.*;
import io.github.bingkkni.noloadingscreen.platform.MatrixStackSnapshot;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import sun.misc.Unsafe;

/** Real transformed queues/readiness and deterministic field-ownership fixtures, no sockets/window. */
public final class NeoForgeBehaviorVerification {
	private static int assertions;
	private static final Unsafe UNSAFE = unsafe();

	public static void run() throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		Minecraft client = allocate(Minecraft.class);
		set(Minecraft.class, null, "instance", client);
		set(Minecraft.class, client, "gameThread", Thread.currentThread());
		set(Minecraft.class, client, "gui", allocate(Gui.class));
		set(Minecraft.class, client, "options", allocate(Options.class));
		NoLoadingScreenConfig.get().enabled = true;
		verifyBinding(client);
		verifyReadiness(client);
		DisconnectHandoffPolicyVerification.run();
		verifyScreenAndWaitScopes(client);
		verifyMatrixRollback();
		verifyOwnedFrameFailure(client);
		verifyGuiMeshAbort();
		LoadingWorkVerification.run();
		SkinPreloadVerification.run();
		JoinClassWarmupVerification.run();
		PlaceholderMovementTest.main(new String[0]);
		System.out.println("NeoForgeBehaviorVerification: " + assertions + " ownership/readiness/wait/matrix assertions passed");
	}

	private static void verifyBinding(Minecraft client) throws Exception {
		ClientLevel scene = allocate(ClientLevel.class);
		LocalPlayer player = allocate(LocalPlayer.class);
		MultiPlayerGameMode mode = allocate(MultiPlayerGameMode.class);
		ClientLevel originalLevel = allocate(ClientLevel.class);
		LocalPlayer originalPlayer = allocate(LocalPlayer.class);
		MultiPlayerGameMode originalMode = allocate(MultiPlayerGameMode.class);
		set(PlaceholderWorld.class, null, "installed", true);
		set(PlaceholderWorld.class, null, "level", scene);
		set(PlaceholderWorld.class, null, "player", player);
		set(PlaceholderWorld.class, null, "gameMode", mode);
		client.level = originalLevel;
		client.player = originalPlayer;
		client.gameMode = originalMode;
		// Binding refuses to replace another live level, exactly as on Fabric.
		check(!PlaceholderWorld.bind(), "Live world ownership is never stolen");
		client.level = null;
		check(PlaceholderWorld.bind(), "Outer bind installs scene");
		check(client.level == scene && client.player == player && client.gameMode == mode, "Three fields bind atomically");
		check(PlaceholderWorld.bind(), "Nested bind shares the same scene");
		PlaceholderWorld.unbind();
		check(client.player == player, "Nested unbind does not restore too soon");
		PlaceholderWorld.unbind();
		check(client.level == null && client.player == originalPlayer && client.gameMode == originalMode, "Outer unbind restores exact null/partial vanilla state");
		PlaceholderWorld.bind();
		PlaceholderWorld.bind();
		PlaceholderWorld.releaseAll();
		check(client.level == null && client.player == originalPlayer && client.gameMode == originalMode, "Tick leak recovery restores all bindings");
		set(PlaceholderWorld.class, null, "installed", false);
		set(PlaceholderWorld.class, null, "level", null);
		set(PlaceholderWorld.class, null, "player", null);
		set(PlaceholderWorld.class, null, "gameMode", null);
		client.player = null;
		client.gameMode = null;
	}

	private static void verifyReadiness(Minecraft client) throws Exception {
		// No packet is synthesized: enabled mode still goes WaitingForServer -> vanilla tick -> Ready.
		LevelLoadTracker enabled = new LevelLoadTracker();
		enabled.startClientLoad(null, null, null);
		check(!enabled.isLevelReady(), "Readiness starts closed");
		enabled.tickClientLoad();
		check(enabled.isLevelReady(), "Enabled gate advances vanilla's real state machine");
		LevelLoadTracker delay = new LevelLoadTracker(60_000L);
		delay.startClientLoad(null, null, null);
		delay.tickClientLoad();
		check(!delay.isLevelReady(), "Vanilla closeDelay remains intact");
		NoLoadingScreenConfig.get().enabled = false;
		LevelLoadTracker disabled = new LevelLoadTracker();
		disabled.startClientLoad(null, null, null);
		disabled.tickClientLoad();
		check(!disabled.isLevelReady(), "Disabled gate does not bypass WaitingForServer");
		Object waiting = get(LevelLoadTracker.class, disabled, "clientState");
		check(waiting.getClass().getSimpleName().equals("WaitingForServer"), "Disabled mode retains original state object family");
		NoLoadingScreenConfig.get().enabled = true;
		NoLoadingScreen.onDisconnected();
	}

	private static void verifyScreenAndWaitScopes(Minecraft client) throws Exception {
		AtomicInteger dispatched = new AtomicInteger();
		Operation<Void> vanilla = args -> { dispatched.incrementAndGet(); return null; };
		LoadingWaitLoop.dispatchInput(client, () -> { throw new AssertionError("Unscoped input ran inline"); }, vanilla);
		check(dispatched.get() == 1, "Normal input preserves original scheduling chain");
		set(LoadingWaitLoop.class, null, "pollingInput", true);
		try { LoadingWaitLoop.dispatchInput(client, dispatched::incrementAndGet, args -> { throw new AssertionError("Scoped input queued"); }); }
		finally { set(LoadingWaitLoop.class, null, "pollingInput", false); }
		check(dispatched.get() == 2, "Only owned synchronous input poll dispatches inline");
		LoadingWaitLoop.begin();
		Object clock = get(LoadingWaitLoop.class, null, "clock");
		LoadingWaitLoop.begin();
		check(clock == get(LoadingWaitLoop.class, null, "clock"), "Nested waits retain interpolation clock");
		LoadingWaitLoop.end();
		check(LoadingWaitLoop.active(), "Nested wait end retains outer ownership");
		LoadingWaitLoop.end();
		check(!LoadingWaitLoop.active() && get(LoadingWaitLoop.class, null, "clock") == null, "Outer end releases clock");
		Method render = method(Minecraft.class, "nls$renderFrame");
		IllegalStateException original = new IllegalStateException("unowned-render-failure");
		try {
			render.invoke(client, null, net.minecraft.client.DeltaTracker.ZERO, false, (Operation<Void>) args -> { throw original; });
			throw new AssertionError("Unowned frame swallowed exception");
		} catch (java.lang.reflect.InvocationTargetException failure) { check(failure.getCause() == original, "Unowned renderer exceptions propagate by identity"); }
		check(client.level == null && !PlaceholderWorld.active(), "Unowned frame never creates a scene");
	}

	private static void verifyOwnedFrameFailure(Minecraft client) throws Exception {
		com.mojang.blaze3d.systems.RenderSystem.initRenderThread();
		var buffers = allocate(net.minecraft.client.renderer.RenderBuffers.class);
		var source = allocate(BufferSourceFixture.class);
		set(net.minecraft.client.renderer.RenderBuffers.class, buffers, "bufferSource", source);
		set(net.minecraft.client.renderer.RenderBuffers.class, buffers, "crumblingBufferSource", source);
		set(Minecraft.class, client, "renderBuffers", buffers);
		set(Options.class, client.options, "keyAttack", allocate(net.minecraft.client.KeyMapping.class));
		set(Options.class, client.options, "keyUse", allocate(net.minecraft.client.KeyMapping.class));
		set(Options.class, client.options, "keyMappings", new net.minecraft.client.KeyMapping[0]);
		var renderer = allocate(RendererFixture.class);
		ClientLevel scene = allocate(ClientLevel.class);
		LocalPlayer player = allocate(LocalPlayer.class);
		MultiPlayerGameMode mode = allocate(MultiPlayerGameMode.class);
		ClientLevel incoming = allocate(ClientLevel.class);
		set(PlaceholderWorld.class, null, "installed", true);
		set(PlaceholderWorld.class, null, "level", scene);
		set(PlaceholderWorld.class, null, "player", player);
		set(PlaceholderWorld.class, null, "gameMode", mode);
		var stack = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
		stack.clear().translation(3, 4, 5);
		Matrix4f matrix = new Matrix4f(stack);
		Method render = method(Minecraft.class, "nls$renderFrame");
		render.invoke(client, renderer, net.minecraft.client.DeltaTracker.ZERO, false, (Operation<Void>) args -> {
			check(Boolean.TRUE.equals(args[2]), "Frame-only wait promotes only the renderer world/HUD flag");
			check(client.level == scene && client.player == player && client.gameMode == mode, "Owned renderer receives all three scene fields");
			stack.pushMatrix().rotateX(1);
			// Simulate a real owner arriving; cleanup must not overwrite its state with saved nulls.
			client.level = incoming; client.player = null; client.gameMode = null;
			throw new IllegalStateException("expected recoverable frame");
		});
		check(renderer.aborts == 1 && source.ends == 2, "Cleanup runs exactly once and drains both shared buffer sources");
		check(stack.equals(matrix), "Failed frame restores matrix and stack depth");
		check(!PlaceholderWorld.active() && client.level == incoming && client.player == null && client.gameMode == null,
			"Failed-frame cleanup releases placeholder without stealing arriving ownership");
		AtomicInteger next = new AtomicInteger();
		render.invoke(client, renderer, net.minecraft.client.DeltaTracker.ZERO, false, (Operation<Void>) args -> { next.incrementAndGet(); return null; });
		check(next.get() == 1 && renderer.aborts == 1, "A subsequent unmodified frame remains usable");
		client.level = null;
	}
	/** Separate JVM: native vanilla codecs with an explicitly empty mod-event container fixture. */
	public static void runRegistryFixture() throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		var mods = net.neoforged.fml.ModList.of(java.util.List.of(), java.util.List.of());
		Method loaded = net.neoforged.fml.ModList.class.getDeclaredMethod("setLoadedMods", java.util.List.class);
		loaded.setAccessible(true);
		loaded.invoke(mods, java.util.List.of());
		verifyRegistryDecoding();
		System.out.println("Vanilla-only registry codec fixture passed (no third-party mod construction)");
	}
	private static void verifyRegistryDecoding() throws Exception {
		var builtins = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
		var originalHolder = builtins.getOrThrow(net.minecraft.world.level.block.Blocks.STONE.builtInRegistryHolder().key());
		Method load = PlaceholderRegistries.class.getDeclaredMethod("load");
		load.setAccessible(true);
		var local = (net.minecraft.core.RegistryAccess.Frozen) load.invoke(null);
		var copy = local.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK).getOrThrow(originalHolder.key());
		check(copy != originalHolder && copy.value() == originalHolder.value(), "Private registry holders preserve vanilla values without mutating global holders");
		check(local.lookupOrThrow(net.minecraft.core.registries.Registries.DIMENSION_TYPE)
			.getOrThrow(net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD) != null, "Actual target vanilla codecs decode the synthetic dimension");
	}

	private static void verifyGuiMeshAbort() throws Exception {
		Class<?> systems = com.mojang.blaze3d.systems.RenderSystem.class;
		Object previousDevice = get(systems, null, "DEVICE");
		Object device = java.lang.reflect.Proxy.newProxyInstance(getClassLoader(),
			new Class<?>[]{com.mojang.blaze3d.systems.GpuDevice.class}, (proxy, method, args) -> {
				if (method.getName().equals("getMaxTextureSize")) return 16384;
				throw new AssertionError("GUI recovery unexpectedly requested GPU operation " + method.getName());
			});
		set(systems, null, "DEVICE", device);
		com.mojang.blaze3d.vertex.ByteBufferBuilder replacement = null;
		try {
			var renderer = allocate(net.minecraft.client.gui.render.GuiRenderer.class);
			var bytes = new com.mojang.blaze3d.vertex.ByteBufferBuilder(128);
			bytes.reserve(16);
			var first = new com.mojang.blaze3d.vertex.MeshData(bytes.build(), null);
			bytes.reserve(16);
			var second = new com.mojang.blaze3d.vertex.MeshData(bytes.build(), null);
			first.close(); // As if recordDraws uploaded only its first mesh before throwing.
			bytes.reserve(12); // Unfinished mesh bytes must not survive abort either.
			Class<?> meshType = Class.forName("net.minecraft.client.gui.render.GuiRenderer$MeshToDraw");
			var constructor = meshType.getDeclaredConstructors()[0]; constructor.setAccessible(true);
			var meshes = new java.util.ArrayList<Object>();
			meshes.add(constructor.newInstance(first, null, null, null));
			meshes.add(constructor.newInstance(second, null, null, null));
			set(net.minecraft.client.gui.render.GuiRenderer.class, renderer, "meshesToDraw", meshes);
			set(net.minecraft.client.gui.render.GuiRenderer.class, renderer, "draws", new java.util.ArrayList<>());
			set(net.minecraft.client.gui.render.GuiRenderer.class, renderer, "byteBufferBuilder", bytes);
			set(net.minecraft.client.gui.render.GuiRenderer.class, renderer, "renderState", new net.minecraft.client.gui.render.state.GuiRenderState());
			set(net.minecraft.client.gui.render.GuiRenderer.class, renderer, "atlasPositions", new java.util.HashMap<>());
			set(net.minecraft.client.gui.render.GuiRenderer.class, renderer, "pictureInPictureRenderStatesScratch", new java.util.HashSet<>());
			((io.github.bingkkni.noloadingscreen.platform.FrameAbort) renderer).nls$abortFrame();
			replacement = (com.mojang.blaze3d.vertex.ByteBufferBuilder) get(net.minecraft.client.gui.render.GuiRenderer.class, renderer, "byteBufferBuilder");
			check(meshes.isEmpty() && replacement != bytes, "Partially uploaded real GUI meshes and unfinished owned arena are discarded");
			replacement.reserve(4);
			try (var result = replacement.build()) { check(result.byteBuffer().remaining() == 4, "Next GUI frame starts at byte zero, not the failed mesh's offset"); }
			check(get(net.minecraft.client.gui.render.GuiRenderer.class, renderer, "bufferBuilder") == null, "Aborted BufferBuilder is not reused");
		} finally {
			if (replacement != null) replacement.close();
			set(systems, null, "DEVICE", previousDevice);
		}
	}
	private static ClassLoader getClassLoader() { return NeoForgeBehaviorVerification.class.getClassLoader(); }

	private static final class RendererFixture extends net.minecraft.client.renderer.GameRenderer implements io.github.bingkkni.noloadingscreen.platform.FrameAbort {
		int aborts;
		private RendererFixture() { super(null, null, null, null); }
		@Override public void nls$abortFrame() { aborts++; }
	}
	private static final class BufferSourceFixture extends net.minecraft.client.renderer.MultiBufferSource.BufferSource {
		int ends;
		private BufferSourceFixture() { super(null, null); }
		@Override public void endBatch() { ends++; }
	}

	private static void verifyMatrixRollback() {
		Matrix4fStack live = new Matrix4fStack(5);
		live.translation(1, 2, 3);
		Matrix4f bottom = new Matrix4f(live);
		live.pushMatrix().rotateY(.4F);
		Matrix4f middle = new Matrix4f(live);
		live.pushMatrix().scale(3);
		Matrix4f top = new Matrix4f(live);
		MatrixStackSnapshot saved = new MatrixStackSnapshot(live);
		live.popMatrix().popMatrix().identity();
		live.pushMatrix().rotateX(.7F);
		saved.restore(live);
		check(live.equals(top), "Restores active matrix value");
		live.popMatrix();
		check(live.equals(middle), "Restores saved middle matrix");
		live.popMatrix();
		check(live.equals(bottom), "Restores bottom/depth");
		for (int i = 0; i < 4; i++) live.pushMatrix();
		check(true, "Original capacity remains usable after rollback");
		saved.restore(live);
		check(live.equals(top), "Snapshot can restore after subsequent mutations");
	}

	private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
	private static Method method(Class<?> type, String fragment) {
		Method method = Arrays.stream(type.getDeclaredMethods()).filter(m -> !m.isSynthetic() && m.getName().contains(fragment)).findFirst().orElseThrow();
		method.setAccessible(true); return method;
	}
	private static Unsafe unsafe() {
		try { Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true); return (Unsafe) field.get(null); }
		catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
	}
	private static <T> T allocate(Class<T> type) throws InstantiationException { return type.cast(UNSAFE.allocateInstance(type)); }
	private static Object get(Class<?> type, Object target, String name) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
	private static void set(Class<?> type, Object target, String name, Object value) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
}
