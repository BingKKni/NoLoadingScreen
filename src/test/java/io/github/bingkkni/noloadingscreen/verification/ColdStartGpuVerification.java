package io.github.bingkkni.noloadingscreen.verification;

import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.PlaceholderRegistries;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/** Opt-in real GPU smoke test; invoked after normal frames, never from a class-loading worker. */
public final class ColdStartGpuVerification {
	private static long voidStarted;
	private static boolean finished;

	public static void afterFrame(final Minecraft minecraft) {
		if (finished || !minecraft.isGameLoadFinished() || minecraft.gui.overlay() != null || PlaceholderRegistries.ready() == null) return;
		if (voidStarted == 0L) {
			if (minecraft.level != null) throw new AssertionError("GPU smoke test must not open a real world");
			NoLoadingScreen.onPreparingResources();
			if (!PlaceholderWorld.active()) throw new AssertionError("Could not construct the real GPU placeholder");
			voidStarted = System.nanoTime();
			return;
		}
		if (System.nanoTime() - voidStarted < TimeUnit.SECONDS.toNanos(3)) return;
		if (!PlaceholderWorld.active()) throw new AssertionError("Placeholder failed during real GPU rendering");
		if (minecraft.windowSurface().isAcquired()) throw new AssertionError("Render frame leaked the window surface");
		if (minecraft.level != null || minecraft.player != null || minecraft.getSingleplayerServer() != null) {
			throw new AssertionError("GPU test leaked a real session or a placeholder binding");
		}
		NoLoadingScreen.onDisconnected();
		minecraft.gui.setScreen(new TitleScreen());
		NoLoadingScreen.LOGGER.info("ColdStartGpuVerification PASSED: startup and 3 seconds of actual placeholder rendering, no save or server.");
		finished = true;
		minecraft.stop();
	}
}
