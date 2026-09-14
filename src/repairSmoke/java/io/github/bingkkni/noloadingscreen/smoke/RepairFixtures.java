package io.github.bingkkni.noloadingscreen.smoke;

import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;

/** API-family fixture steps for the isolated GPU client. */
public final class RepairFixtures {
	private RepairFixtures() {}

	/**
	 * A 26.x client binds item component maps from the first registry sync it receives. This fixture
	 * never connects, so it runs the same vanilla initializer step over the global registries, with
	 * the private early dynamic registries supplying the referenced data.
	 */
	public static void bindItemComponents(final RegistryAccess.Frozen early) {
		Stream<RegistryAccess.RegistryEntry<?>> builtins = BuiltInRegistries.REGISTRY.stream().map(RepairFixtures::entry);
		Stream<RegistryAccess.RegistryEntry<?>> dynamic = early.registries()
			.filter(registry -> !BuiltInRegistries.REGISTRY.containsKey(registry.key().identifier()));
		HolderLookup.Provider context = new RegistryAccess.ImmutableRegistryAccess(Stream.concat(builtins, dynamic)).freeze();
		BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(context).forEach(DataComponentInitializers.PendingComponents::apply);
	}

	private static <T> RegistryAccess.RegistryEntry<T> entry(final Registry<T> registry) {
		return new RegistryAccess.RegistryEntry<>(registry.key(), registry);
	}
}
