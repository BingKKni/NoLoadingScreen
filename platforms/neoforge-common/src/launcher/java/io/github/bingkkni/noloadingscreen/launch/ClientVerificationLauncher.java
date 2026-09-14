package io.github.bingkkni.noloadingscreen.launch;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.startup.StartupArgs;

/** FML's native loader, with CLIENT selected at creation; no server bootstrap or game window. */
public final class ClientVerificationLauncher {
	public static void main(String[] args) throws Throwable {
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		try (FMLLoader loader = FMLLoader.create(new StartupArgs(Path.of(""), !Boolean.getBoolean("nls.verify.gpu"), Dist.CLIENT, false,
			args, Set.of(), List.of(), previous))) {
			if (loader.getLoadingModList().hasErrors()) throw new ModLoadingException(loader.getLoadingModList().getModLoadingIssues());
			Thread.currentThread().setContextClassLoader(loader.getCurrentClassLoader());
			try {
				if (Boolean.getBoolean("nls.verify.gpu")) {
					org.spongepowered.asm.mixin.Mixins.addConfiguration("noloadingscreen.smoke.mixins.json");
					Class.forName("net.minecraft.client.main.Main", true, loader.getCurrentClassLoader())
						.getMethod("main", String[].class).invoke(null, (Object) args);
				} else {
					Class.forName("io.github.bingkkni.noloadingscreen.ClientVerification", true, loader.getCurrentClassLoader())
						.getMethod("run").invoke(null);
				}
			} catch (InvocationTargetException failure) { throw failure.getCause(); }
		} finally { Thread.currentThread().setContextClassLoader(previous); }
	}
}
