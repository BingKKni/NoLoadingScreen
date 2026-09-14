package io.github.bingkkni.noloadingscreen.platform;

import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import net.fabricmc.api.ClientModInitializer;

/** Fabric entry point; feature lifecycle is independent of the loader. */
public final class FabricBootstrap implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		NoLoadingScreen.initialize();
	}
}
