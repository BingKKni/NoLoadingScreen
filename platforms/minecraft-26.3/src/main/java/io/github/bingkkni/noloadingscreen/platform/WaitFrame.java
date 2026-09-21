package io.github.bingkkni.noloadingscreen.platform;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.bingkkni.noloadingscreen.mixin.MinecraftEventsAccessor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;

/**
 * Frame/event access for synchronous local-only waits.
 *
 * <p>26.3's own save/boot loops call {@code RenderSystem.pumpEvents}, which flushes every queued
 * input event before polling and is what makes those waits ignore the mouse. A local frame polls
 * instead, so the events reach the handlers exactly as they do in a normal frame.
 */
public final class WaitFrame {
	private WaitFrame() {}
	public static void pollEvents(Minecraft client) { RenderSystem.pollEvents(((MinecraftEventsAccessor) client).nls$eventHandler()); }
	public static int advance(DeltaTracker.Timer clock, long now) {
		int ticks = clock.advanceGameTime(now);
		clock.advanceRealTime(now);
		return ticks;
	}
	public static void draw(Minecraft client, boolean world) { client.renderFrame(world); }
}
