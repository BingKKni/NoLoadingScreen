package io.github.bingkkni.noloadingscreen.platform;

import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.gui.NoLoadingScreenOptionsScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(NoLoadingScreen.MOD_ID)
public final class ForgeBootstrap {
    public ForgeBootstrap(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) Client.initialize(context);
    }

    // Keep client classes out of dedicated-server class resolution, even if manually loaded.
    private static final class Client {
        private static void initialize(FMLJavaModLoadingContext context) {
            NoLoadingScreen.initialize();
            context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                    (minecraft, parent) -> new NoLoadingScreenOptionsScreen(parent)));
        }
    }
}
