package io.github.bingkkni.noloadingscreen.gui;

import io.github.bingkkni.noloadingscreen.platform.ClientUi;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.ItemSlotMouseAction;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;

/** Vanilla inventory presentation and local manipulation, without a server container lifecycle. */
public final class LoadingInventoryScreen extends InventoryScreenAdapter {
	public LoadingInventoryScreen(final LocalPlayer player) {
		super(player);
	}

	@Override
	protected void addItemSlotMouseAction(final ItemSlotMouseAction action) {
		// Bundle scrolling actions in vanilla call MultiPlayerGameMode and send packets. Stack
		// clicks remain local; do not register those network-backed mouse extensions here.
	}


	@Override
	protected void handleSlotStateChanged(final int slotId, final int containerId, final boolean newState) {
		// Not a server-owned container.
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		return this.withPlayer(() -> super.mouseClicked(event, doubleClick));
	}

	@Override
	public boolean mouseReleased(final MouseButtonEvent event) {
		return this.withPlayer(() -> super.mouseReleased(event));
	}

	@Override
	public boolean mouseDragged(final MouseButtonEvent event, final double dx, final double dy) {
		return this.withPlayer(() -> super.mouseDragged(event, dx, dy));
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		return this.withPlayer(() -> super.keyPressed(event));
	}

	private boolean withPlayer(final BooleanSupplier action) {
		if (!PlaceholderWorld.bind()) return false;
		try {
			return PlaceholderWorld.owns(this.player) && action.getAsBoolean();
		} finally {
			PlaceholderWorld.unbind();
		}
	}

	@Override
	public void onClose() {
		// Never LocalPlayer.closeContainer: it emits ServerboundContainerClosePacket.
		ClientUi.setScreen(this.minecraft, null);
	}

	@Override
	public void removed() {
		// Keep the local cursor/crafting stacks when reopening. Vanilla menu.removed may drop
		// them (and swing/send); the entire disposable player is discarded at login instead.
	}
}
