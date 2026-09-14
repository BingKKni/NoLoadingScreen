package io.github.bingkkni.noloadingscreen.compat;

import io.github.bingkkni.noloadingscreen.platform.LoaderServices;

/** Private Sodium layouts are enabled only for the current stable build covered by verification. */
public final class SodiumCompatibility {
	// Current latest stable for Minecraft 26.2. Update this only after the new private layout passes
	// the compatibility verifier; never enable a prerelease or unknown build by prefix matching.
	private static final String CURRENT_STABLE_VERSION = "0.9.2+mc26.2";

	private SodiumCompatibility() {}

	public static boolean supported() {
		return LoaderServices.modVersion("sodium")
			.map(CURRENT_STABLE_VERSION::equals)
			.orElse(false);
	}
}
