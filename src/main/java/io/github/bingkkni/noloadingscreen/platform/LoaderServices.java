package io.github.bingkkni.noloadingscreen.platform;

import java.nio.file.Path;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;

/** Loader-owned paths and metadata. Replaced at compile time by other loader builds. */
public final class LoaderServices {
	private LoaderServices() {}

	public static String sodiumFluidRenderer() { return "net.caffeinemc.mods.sodium.fabric.render.FluidRendererImpl"; }

	public static Path configDirectory() {
		return FabricLoader.getInstance().getConfigDir();
	}

	public static Optional<String> modVersion(final String id) {
		return FabricLoader.getInstance().getModContainer(id)
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString());
	}
}
