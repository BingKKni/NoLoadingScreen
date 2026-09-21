package io.github.bingkkni.noloadingscreen.platform;

import io.github.bingkkni.noloadingscreen.mixin.MinecraftFrameAccessor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** runTick(false) is vanilla's frame-only path: no tasks, packets or gameplay ticks. */
public final class WaitFrame {
	private WaitFrame() {}
	public static void pollEvents(Minecraft client) { GLFW.glfwPollEvents(); }
	public static int advance(DeltaTracker.Timer clock, long now) { return clock.advanceTime(now, true); }
	public static void draw(Minecraft client, boolean world) { ((MinecraftFrameAccessor) client).nls$runFrame(false); }
}
