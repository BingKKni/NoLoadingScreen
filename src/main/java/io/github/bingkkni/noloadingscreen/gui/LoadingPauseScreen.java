package io.github.bingkkni.noloadingscreen.gui;

import io.github.bingkkni.noloadingscreen.NoLoadingScreen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class LoadingPauseScreen extends Screen {
	private final @Nullable Runnable disconnect;

	public LoadingPauseScreen(final Component title, final Connection connection) {
		this(title, () -> {
			// Release the scene/owner BEFORE vanilla delivers ABORT, so a deliberate exit
			// cannot be mistaken for an unexpected kick and open KickWarn again.
			NoLoadingScreen.onDisconnected();
			connection.disconnect(ConnectScreen.ABORT_CONNECTION);
			connection.handleDisconnection();
		});
	}

	public LoadingPauseScreen(final Component title, final @Nullable Runnable disconnect) {
		super(title);
		this.disconnect = disconnect;
	}

	@Override
	protected void init() {
		this.addRenderableWidget(Button.builder(Component.translatable("menu.returnToGame"), button -> this.onClose())
			.bounds(this.width / 2 - 100, this.height / 2 - 24, 200, 20).build());
		if (this.disconnect != null) {
			this.addRenderableWidget(Button.builder(CommonComponents.GUI_DISCONNECT, button -> {
				button.active = false;
				this.disconnect.run();
			}).bounds(this.width / 2 - 100, this.height / 2 + 4, 200, 20).build());
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
