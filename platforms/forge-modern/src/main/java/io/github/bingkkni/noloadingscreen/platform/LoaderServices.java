package io.github.bingkkni.noloadingscreen.platform;

import java.nio.file.Path;
import java.util.Optional;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.FMLPaths;

/** Uses discovery metadata, available before the runtime ModList and mod constructors. */
public final class LoaderServices {
    private LoaderServices() {}

    public static String sodiumFluidRenderer() {
        return "net.caffeinemc.mods.sodium.forge.render.FluidRendererImpl";
    }

    /**
     * Forge rejects (rather than queues) handshakes until ServerLifecycleHooks allows logins.
     * The server's volatile isReady flag is published after handleServerStarted opens that gate.
     * Keep the real readiness wait; LoadingWaitLoop supplies local input/rendering meanwhile.
     */
    public static boolean allowsEarlyLocalConnection() { return false; }

    public static Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    public static Optional<String> modVersion(final String id) {
        return Optional.ofNullable(LoadingModList.getModFileById(id))
            .flatMap(file -> file.getMods().stream().filter(mod -> id.equals(mod.getModId())).findFirst())
            .map(mod -> mod.getVersion().toString());
    }
}
