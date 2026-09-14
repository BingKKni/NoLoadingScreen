package io.github.bingkkni.noloadingscreen;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.tags.TagLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import io.github.bingkkni.noloadingscreen.platform.RegistryLoader;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import io.github.bingkkni.noloadingscreen.platform.ClientRuntime;
import org.jspecify.annotations.Nullable;

/** Private vanilla client registries, available before a WorldStem or server registry sync exists. */
public final class PlaceholderRegistries {
	private static @Nullable CompletableFuture<RegistryAccess.Frozen> loading;

	private PlaceholderRegistries() {}

	/** No world files, selected datapacks, server connection or client registry/tag mutation. */
	public static void preload() {
		if (loading != null || !NoLoadingScreenConfig.get().enabled) return;
		loading = CompletableFuture.supplyAsync(PlaceholderRegistries::load, ClientRuntime.backgroundExecutor())
			.exceptionally(failure -> {
				NoLoadingScreen.LOGGER.warn("Could not prepare local placeholder registries; early loading stays vanilla", failure);
				return null;
			});
	}

	/** Never block the render or network thread on the warm-up. */
	public static RegistryAccess.@Nullable Frozen ready() {
		preload();
		return loading == null ? null : loading.getNow(null);
	}

	private static <T> Registry<T> copyRegistry(final Registry<T> source) {
		MappedRegistry<T> copy = new MappedRegistry<>(source.key(), source.registryLifecycle());
		source.listElements().forEach(holder -> copy.register(holder.key(), holder.value(),
			source.registrationInfo(holder.key()).orElse(RegistrationInfo.BUILT_IN)));
		return copy.freeze();
	}

	static RegistryAccess.Frozen load() {
		RegistryAccess.Frozen builtins = new RegistryAccess.ImmutableRegistryAccess(BuiltInRegistries.REGISTRY.stream()
			.map(PlaceholderRegistries::copyRegistry).toList()).freeze();
		try (var resources = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(ServerPacksSource.createVanillaPackSource()))) {
			// Dynamic codecs reference static block/item tags even on the title screen. Apply
			// vanilla tags ONLY to private holders, never to the real client's global registries.
			TagLoader.loadTagsForExistingRegistries(resources, builtins).forEach(Registry.PendingTags::apply);
			// Use vanilla's client codecs and loader, not a cast/copy of VanillaRegistries' lookup:
			// the loader binds references and tags to their actual owning registries.
			RegistryAccess.Frozen dynamic = RegistryLoader.load(resources, builtins);
			return new RegistryAccess.ImmutableRegistryAccess(Stream.concat(builtins.registries(), dynamic.registries())).freeze();
		}
	}
}
