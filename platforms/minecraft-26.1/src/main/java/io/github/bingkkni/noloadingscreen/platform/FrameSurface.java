package io.github.bingkkni.noloadingscreen.platform;

import net.minecraft.client.Minecraft;

/**
 * 26.1 has no swapchain surface to hand back: its frame is drawn and presented inside
 * {@code runTick}, and a failed placeholder frame is cleaned up through {@link FrameAbort} by
 * {@code RenderFrameMixin} instead.
 */
public final class FrameSurface {
	private FrameSurface() {}
	public static boolean handBack(final Minecraft minecraft) { return true; }
}
