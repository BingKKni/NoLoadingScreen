package io.github.bingkkni.noloadingscreen.platform;

import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.device.GpuSurface;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import net.minecraft.client.Minecraft;

/**
 * Puts the swapchain image back after a frame this mod swallowed. Same contract as the 26.2
 * version; 26.3 moved the GPU surface and device types into the renderpearl API package.
 *
 * <p>{@code renderFrame} acquires a surface image near its start and hands it back with
 * {@code present()} at the very end. An exception in between skips that, and the next
 * {@code renderFrame} returns at once while the image is still acquired, leaving the window
 * frozen on its last image for the rest of the session.
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
