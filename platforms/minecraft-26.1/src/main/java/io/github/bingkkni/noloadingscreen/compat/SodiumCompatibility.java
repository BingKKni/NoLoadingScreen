package io.github.bingkkni.noloadingscreen.compat;

import io.github.bingkkni.noloadingscreen.platform.LoaderServices;
import java.util.Set;

/** Exact private layouts verified across the 26.1 family; unknown builds keep native scheduling. */
public final class SodiumCompatibility {
	private static final Set<String> VERIFIED_VERSIONS = Set.of("0.8.9+mc26.1.1", "0.9.2+mc26.1.2");

	private SodiumCompatibility() {}

	public static boolean supported() {
		return LoaderServices.modVersion("sodium").filter(VERIFIED_VERSIONS::contains).isPresent();
	}
}
