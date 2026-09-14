package io.github.bingkkni.noloadingscreen.platform;

import java.nio.file.Path;
import java.util.Optional;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.FMLLoader;

/** Available during Mixin discovery, before the runtime ModList is constructed. */
public final class LoaderServices {
	private LoaderServices() {}

	public static String sodiumFluidRenderer() { return "net.caffeinemc.mods.sodium.neoforge.render.FluidRendererImpl"; }

	public static boolean allowsEarlyLocalConnection() { return true; }

	public static Path configDirectory() {
		return FMLPaths.CONFIGDIR.get();
	}

	public static Optional<String> modVersion(final String id) {
		return Optional.ofNullable(FMLLoader.getCurrent().getLoadingModList().getModFileById(id))
			.flatMap(file -> file.getMods().stream().filter(mod -> id.equals(mod.getModId())).findFirst())
			.map(mod -> mod.getVersion().toString());
	}
}
