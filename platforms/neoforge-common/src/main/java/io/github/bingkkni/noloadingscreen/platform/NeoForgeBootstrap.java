package io.github.bingkkni.noloadingscreen.platform;

import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.gui.NoLoadingScreenOptionsScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = NoLoadingScreen.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeBootstrap {
	public NeoForgeBootstrap(ModContainer container) {
		NoLoadingScreen.initialize();
		container.registerExtensionPoint(IConfigScreenFactory.class,
			(mod, parent) -> new NoLoadingScreenOptionsScreen(parent));
	}
}
