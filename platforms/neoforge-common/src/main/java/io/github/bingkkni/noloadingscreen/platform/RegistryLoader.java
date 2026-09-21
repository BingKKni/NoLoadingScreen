package io.github.bingkkni.noloadingscreen.platform;

import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;

/** 1.21 registry decoding is synchronous, already dispatched to the shared background executor. */
public final class RegistryLoader {
	private RegistryLoader() {}
	public static CloseableResourceManager vanillaData() {
		return new MultiPackResourceManager(PackType.SERVER_DATA, List.of(ServerPacksSource.createVanillaPackSource()));
	}
	public static RegistryAccess.Frozen load(ResourceManager resources, RegistryAccess.Frozen builtins) {
		return RegistryDataLoader.load(resources, builtins.listRegistries().toList(), RegistryDataLoader.SYNCHRONIZED_REGISTRIES);
	}
}
