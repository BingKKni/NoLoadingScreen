package io.github.bingkkni.noloadingscreen.gui;

import io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/** Vanilla widgets; switch the whole form to one column if translated labels cannot fit. */
public class NoLoadingScreenOptionsScreen extends OptionsSubScreen {
	private final NoLoadingScreenConfig config = NoLoadingScreenConfig.get();

	public NoLoadingScreenOptionsScreen(final Screen lastScreen) {
		super(lastScreen, Minecraft.getInstance().options, Component.translatable("noloadingscreen.options.title"));
	}

	@Override
	protected void addOptions() {
		var enabled = toggle("enabled", config.enabled, value -> config.enabled = value);
		var movement = toggle("allowMovementWhileLoading", config.placeholderFreeMove, value -> config.placeholderFreeMove = value);
		var flight = toggle("allowFlightAndNoclip", config.allowFlightAndNoclip, value -> config.allowFlightAndNoclip = value);
		var overlay = toggle("loadingOverlay", config.loadingOverlay, value -> config.loadingOverlay = value);
		var kick = toggle("retainWorldOnKick", config.retainWorldOnKick, value -> config.retainWorldOnKick = value);
		this.list.addBig(enabled);
		boolean singleColumn = width < 340;
		for (String key : new String[]{"allowMovementWhileLoading", "allowFlightAndNoclip", "loadingOverlay", "retainWorldOnKick"}) {
			for (String state : new String[]{"options.on", "options.off"}) {
				singleColumn |= font.width(Component.translatable("options.generic_value",
					Component.translatable("noloadingscreen.option." + key), Component.translatable(state))) > 138;
			}
		}
		if (singleColumn) {
			this.list.addBig(movement);
			this.list.addBig(flight);
			this.list.addBig(overlay);
			this.list.addBig(kick);
		} else {
			this.list.addSmall(movement, flight);
			this.list.addSmall(overlay, kick);
		}
		this.list.addBig(new OptionInstance<Integer>("noloadingscreen.option.multiplayerWait",
			OptionInstance.cachedConstantTooltip(Component.translatable("noloadingscreen.option.multiplayerWait.tooltip")),
			(caption, value) -> Component.translatable("options.generic_value", caption,
				value == 61 ? Component.translatable("noloadingscreen.option.unlimited")
					: Component.translatable("noloadingscreen.option.seconds", value)),
			new OptionInstance.IntRange(3, 61), config.waitSeconds() == 0 ? 61 : config.waitSeconds(),
			value -> config.multiplayerWaitSeconds = value == 61 ? 0 : value));
	}

	private OptionInstance<Boolean> toggle(String key, boolean value, Consumer<Boolean> change) {
		return OptionInstance.createBoolean("noloadingscreen.option." + key,
			OptionInstance.cachedConstantTooltip(Component.translatable("noloadingscreen.option." + key + ".tooltip")), value, next -> change.accept(next));
	}

	@Override
	public void onClose() {
		config.save();
		super.onClose();
	}
}
