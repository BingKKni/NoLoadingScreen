package io.github.bingkkni.noloadingscreen.platform;

import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;

/** Client registry codec loader; caller supplies private built-in holders. */
public final class RegistryLoader {
	private RegistryLoader() {}
	/** 26.3's VanillaPackResources is a bundle of layers; the full-resources layer is the data pack itself. */
	public static CloseableResourceManager vanillaData() {
		return new MultiPackResourceManager(PackType.SERVER_DATA, List.of(ServerPacksSource.createVanillaPackSource().fullResources()));
	}
	public static RegistryAccess.Frozen load(ResourceManager resources, RegistryAccess.Frozen builtins) {
		return RegistryDataLoader.load(resources, builtins.listRegistries().toList(),
			RegistryDataLoader.SYNCHRONIZED_REGISTRIES, Runnable::run).join();
	}
}
