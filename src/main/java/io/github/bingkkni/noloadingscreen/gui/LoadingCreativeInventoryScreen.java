package io.github.bingkkni.noloadingscreen.gui;

import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import io.github.bingkkni.noloadingscreen.platform.ClientUi;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;

/** Vanilla creative catalog/search/tabs with a disposable inventory and guarded packet exits. */
public final class LoadingCreativeInventoryScreen extends CreativeModeInventoryScreen {
	private final LocalPlayer player;
	public LoadingCreativeInventoryScreen(LocalPlayer player) {
		super(player, player.level().enabledFeatures(), false);
		this.player = player;
	}
	private boolean bound(BooleanSupplier action) {
		if (!PlaceholderWorld.bind()) return false;
		try { return PlaceholderWorld.owns(player) && action.getAsBoolean(); }
		finally { PlaceholderWorld.unbind(); }
	}
	@Override protected void init() { bound(() -> { super.init(); return true; }); }
	@Override public void containerTick() { bound(() -> { super.containerTick(); return true; }); }
	@Override public boolean mouseClicked(MouseButtonEvent e, boolean twice) { return bound(() -> super.mouseClicked(e, twice)); }
	@Override public boolean mouseReleased(MouseButtonEvent e) { return bound(() -> super.mouseReleased(e)); }
	@Override public boolean mouseDragged(MouseButtonEvent e, double x, double y) { return bound(() -> super.mouseDragged(e, x, y)); }
	@Override public boolean mouseScrolled(double x, double y, double dx, double dy) { return bound(() -> super.mouseScrolled(x, y, dx, dy)); }
	@Override public boolean keyPressed(KeyEvent e) { return bound(() -> super.keyPressed(e)); }
	@Override public boolean charTyped(CharacterEvent e) { return bound(() -> super.charTyped(e)); }
	@Override public void onClose() { ClientUi.setScreen(minecraft, null); }
	@Override public void removed() {
		bound(() -> { super.removed(); player.containerMenu = player.inventoryMenu; return true; });
	}
}
