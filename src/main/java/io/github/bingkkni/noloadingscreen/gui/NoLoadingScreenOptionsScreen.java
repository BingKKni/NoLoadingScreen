package io.github.bingkkni.noloadingscreen.gui;

import io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/**
 * Built entirely out of vanilla {@link OptionInstance} widgets, so the mod needs neither Cloth
 * Config nor YACL and picks up the game's own styling for free.
 */
public class NoLoadingScreenOptionsScreen extends OptionsSubScreen {
	private final NoLoadingScreenConfig config = NoLoadingScreenConfig.get();

	public NoLoadingScreenOptionsScreen(final Screen lastScreen) {
		super(lastScreen, Minecraft.getInstance().options, Component.translatable("noloadingscreen.options.title"));
	}

	@Override
	protected void addOptions() {
		this.list.addBig(this.enabledOption());
		this.list.addBig(this.allowMovementWhileLoadingOption());
		this.list.addSmall(this.loadingOverlayOption(), this.showJoinTimeOption());
	}

	@Override
	public void onClose() {
		this.config.save();
		super.onClose();
	}

	private OptionInstance<Boolean> enabledOption() {
		return OptionInstance.createBoolean(
			"noloadingscreen.option.enabled",
			OptionInstance.cachedConstantTooltip(Component.translatable("noloadingscreen.option.enabled.tooltip")),
			this.config.enabled,
			value -> this.config.enabled = value
		);
	}

	private OptionInstance<Boolean> allowMovementWhileLoadingOption() {
		return OptionInstance.createBoolean(
			"noloadingscreen.option.allowMovementWhileLoading",
			OptionInstance.cachedConstantTooltip(Component.translatable("noloadingscreen.option.allowMovementWhileLoading.tooltip")),
			this.config.placeholderFreeMove,
			value -> this.config.placeholderFreeMove = value
		);
	}

	private OptionInstance<Boolean> loadingOverlayOption() {
		return OptionInstance.createBoolean(
			"noloadingscreen.option.loadingOverlay",
			OptionInstance.cachedConstantTooltip(Component.translatable("noloadingscreen.option.loadingOverlay.tooltip")),
			this.config.loadingOverlay,
			value -> this.config.loadingOverlay = value
		);
	}

	private OptionInstance<Boolean> showJoinTimeOption() {
		return OptionInstance.createBoolean(
			"noloadingscreen.option.showJoinTime",
			OptionInstance.cachedConstantTooltip(Component.translatable("noloadingscreen.option.showJoinTime.tooltip")),
			this.config.showJoinTime,
			value -> this.config.showJoinTime = value
		);
	}
}
