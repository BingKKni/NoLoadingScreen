package io.github.bingkkni.noloadingscreen;

import io.github.bingkkni.noloadingscreen.platform.ClientUi;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.bingkkni.noloadingscreen.platform.WaitFrame;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import io.github.bingkkni.noloadingscreen.platform.ClientRuntime;

/** Local-only frame pump for vanilla's synchronous resource/save waits. Never calls Minecraft.tick. */
public final class LoadingWaitLoop {
	private static DeltaTracker.Timer clock;
	private static int depth;
	/** True for the whole local frame: our explicit poll and any poll inside the API family's frame path. */
	private static boolean pollingInput;
	private static boolean drawing;
	private static long nextFrameMs;

	private LoadingWaitLoop() {}

	public static void begin() {
		if (depth++ == 0) {
			clock = new DeltaTracker.Timer(20.0F, ClientRuntime.millis(), ms -> ms);
			nextFrameMs = 0L;
		}
	}

	public static void end() {
		if (depth > 0 && --depth == 0) clock = null;
	}

	public static boolean active() {
		return depth > 0;
	}

	public static DeltaTracker renderTime(final DeltaTracker vanilla) {
		return clock != null ? clock : vanilla;
	}

	/**
	 * Same-thread input polled by a local frame runs at once. The wait never drains the client task
	 * queue, and a save started from a menu click is already inside a running task, so vanilla's
	 * {@code Minecraft.execute} would park the callback until the wait ends. Outside a local frame,
	 * preserve the full mod chain.
	 */
	public static void dispatchInput(final Minecraft minecraft, final Runnable input, final Operation<Void> original) {
		if (pollingInput && minecraft.isSameThread()) input.run();
		else original.call(minecraft, input);
	}

	/** Called from a managedBlock predicate; its original task processing stays unbound and intact. */
	public static void frameIfDue(final Minecraft minecraft) {
		if (ClientRuntime.millis() >= nextFrameMs) frame(minecraft);
	}

	public static void frame(final Minecraft minecraft) {
		if (!active() || drawing) return;
		drawing = true;
		nextFrameMs = ClientRuntime.millis() + 16L; // resource waits leave time for completion tasks
		// Never pump general executables/packets during saving. Resource loading still pumps
		// its own completion executor through the original managedBlock, outside this scope.
		// Input alone is dispatched inline for the entire frame: older frame paths poll GLFW again
		// while presenting, and a callback queued there would only run after the wait ends.
		pollingInput = true;
		try {
			WaitFrame.pollEvents(minecraft);
			ClientCommands.runPending();
			if (minecraft.getWindow().shouldClose()) minecraft.stop(); // saving must still finish
			NoLoadingScreen.tryResourcePlaceholder();
			long now = ClientRuntime.millis();
			int ticks = Math.min(10, WaitFrame.advance(clock, now));
			try {
				if (PlaceholderWorld.active()) {
					if (ClientUi.screen(minecraft) == null && ClientUi.overlay(minecraft) == null) PlaceholderWorld.handleSafeKeybinds();
					for (int i = 0; i < ticks; i++) {
						PlaceholderWorld.tick();
						Screen screen = ClientUi.screen(minecraft);
						if (ScreenTransitions.isLocalScreen(screen) && PlaceholderWorld.bind()) {
							try { screen.tick(); } finally { PlaceholderWorld.unbind(); }
						}
					}
					if (PlaceholderWorld.bind()) {
						try { minecraft.mouseHandler.handleAccumulatedMovement(); } finally { PlaceholderWorld.unbind(); }
					}
				}
			} catch (Throwable failure) {
				PlaceholderWorld.onRenderFailed(failure);
			}
			WaitFrame.draw(minecraft, PlaceholderWorld.active());
		} finally {
			pollingInput = false;
			drawing = false;
		}
	}
}
