package io.github.bingkkni.noloadingscreen.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.bingkkni.noloadingscreen.gui.NoLoadingScreenOptionsScreen;

public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return NoLoadingScreenOptionsScreen::new;
	}
}
