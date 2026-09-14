package io.github.bingkkni.noloadingscreen.compat;

import io.github.bingkkni.noloadingscreen.platform.LoaderServices;

/** Exact upstream artifact whose private API is verified by this target's compatibility check. */
public final class SodiumCompatibility {
	public static final String VERSION = "0.7.3+mc1.21.10";
	private SodiumCompatibility() {}
	public static boolean supported() { return LoaderServices.modVersion("sodium").map(VERSION::equals).orElse(false); }
}
