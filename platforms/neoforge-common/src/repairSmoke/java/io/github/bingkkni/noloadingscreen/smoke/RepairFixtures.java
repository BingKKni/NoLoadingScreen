package io.github.bingkkni.noloadingscreen.smoke;

import net.minecraft.core.RegistryAccess;

/** API-family fixture steps for the isolated GPU client. */
public final class RepairFixtures {
	private RepairFixtures() {}

	/** Mapped 1.21 items carry their component maps from registration; nothing to bind. */
	public static void bindItemComponents(final RegistryAccess.Frozen early) {
	}
}
