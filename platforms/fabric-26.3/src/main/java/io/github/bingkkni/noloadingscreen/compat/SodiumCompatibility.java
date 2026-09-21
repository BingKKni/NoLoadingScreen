package io.github.bingkkni.noloadingscreen.compat;

import io.github.bingkkni.noloadingscreen.platform.LoaderServices;

/** Private Sodium layouts are enabled only for the exact build verified on this target. */
public final class SodiumCompatibility {
	// Latest stable Sodium for this game version. Update only after the new private layout passes
	// this target's compatibility verifier; never enable a prerelease or unknown build by prefix.
	private static final String CURRENT_STABLE_VERSION = "0.9.2+mc26.3";

	private SodiumCompatibility() {}

	public static boolean supported() {
		return LoaderServices.modVersion("sodium")
			.map(CURRENT_STABLE_VERSION::equals)
			.orElse(false);
	}
}
