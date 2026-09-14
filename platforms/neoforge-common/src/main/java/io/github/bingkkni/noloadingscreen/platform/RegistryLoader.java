package io.github.bingkkni.noloadingscreen.platform;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.packs.resources.ResourceManager;

/** 1.21 registry decoding is synchronous, already dispatched to the shared background executor. */
public final class RegistryLoader {
	private RegistryLoader() {}
	public static RegistryAccess.Frozen load(ResourceManager resources, RegistryAccess.Frozen builtins) {
		return RegistryDataLoader.load(resources, builtins.listRegistries().toList(), RegistryDataLoader.SYNCHRONIZED_REGISTRIES);
	}
}
