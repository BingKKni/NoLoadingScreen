package io.github.bingkkni.noloadingscreen.platform;

import java.util.concurrent.Executor;
import net.minecraft.util.Util;

/** Minecraft's monotonic clock and shared worker executor (package differs by API family). */
public final class ClientRuntime {
	private ClientRuntime() {}
	public static long millis() { return Util.getMillis(); }
	public static Executor backgroundExecutor() { return Util.backgroundExecutor(); }
}
