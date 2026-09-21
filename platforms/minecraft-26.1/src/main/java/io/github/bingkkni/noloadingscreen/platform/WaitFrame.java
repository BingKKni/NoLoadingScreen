package io.github.bingkkni.noloadingscreen.platform;

import io.github.bingkkni.noloadingscreen.mixin.MinecraftFrameAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;

/** Frame/event access for synchronous local-only waits. */
public final class WaitFrame {
	private WaitFrame() {}
	public static void pollEvents(Minecraft client) { RenderSystem.pollEvents(); }
	public static int advance(DeltaTracker.Timer clock, long now) {
		int ticks = clock.advanceGameTime(now);
		clock.advanceRealTime(now);
		return ticks;
	}
	public static void draw(Minecraft client, boolean world) { ((MinecraftFrameAccessor) client).nls$renderFrame(world); }
}
