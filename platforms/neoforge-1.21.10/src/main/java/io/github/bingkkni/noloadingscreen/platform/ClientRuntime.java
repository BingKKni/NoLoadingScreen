package io.github.bingkkni.noloadingscreen.platform;

import java.util.concurrent.Executor;
import net.minecraft.Util;

/** Minecraft 1.21 clock and executor access. */
public final class ClientRuntime {
	private ClientRuntime() {}
	public static long millis() { return Util.getMillis(); }
	public static Executor backgroundExecutor() { return Util.backgroundExecutor(); }
}
