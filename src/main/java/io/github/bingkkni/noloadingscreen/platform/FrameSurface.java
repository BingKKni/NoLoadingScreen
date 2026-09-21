package io.github.bingkkni.noloadingscreen.platform;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import net.minecraft.client.Minecraft;

/**
 * Puts the swapchain image back after a frame this mod swallowed.
 *
 * <p>{@code renderFrame} acquires a surface image near its start and hands it back with
 * {@code present()} at the very end. An exception in between skips that, and the first thing the
 * <em>next</em> {@code renderFrame} does is
 *
 * <pre>if (this.windowSurface.isAcquired()) return;</pre>
 *
 * <p>So swallowing a frame without this leaves the window frozen on its last image for the rest
 * of the session — the game keeps running, the log looks healthy, and quitting ends with
 * {@code Shutdown failure! java.lang.IllegalStateException: Cannot close a surface while it is
 * acquired}. That is very much worse than the loading screen this mod set out to remove, and it
 * is what the first version of the safety net actually did on the two failures in the logs.
 */
public final class FrameSurface {
	private FrameSurface() {}

	/** @return whether the surface was handed back, i.e. whether swallowing the frame is survivable. */
	public static boolean handBack(final Minecraft minecraft) {
		GpuSurface surface = minecraft.windowSurface();
		if (!surface.isAcquired()) {
			return true;
		}

		try {
			// Vanilla's own tail, minus profiling and frame capture. The blit is not optional:
			// present() refuses to run without one.
			GpuDevice device = RenderSystem.getDevice();
			surface.blitFromTexture(
				device.createCommandEncoder(),
				minecraft.gameRenderer.mainRenderTarget().getColorTextureView()
			);
			device.createCommandEncoder().submit();
			surface.present();
			return true;
		} catch (Throwable t) {
			NoLoadingScreen.LOGGER.error("Could not hand the window surface back after a failed frame", t);
			return false;
		}
	}
}
