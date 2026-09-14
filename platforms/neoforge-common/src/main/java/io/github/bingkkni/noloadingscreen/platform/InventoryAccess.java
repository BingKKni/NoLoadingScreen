package io.github.bingkkni.noloadingscreen.platform;

import io.github.bingkkni.noloadingscreen.InventoryClick;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;

/** Vanilla 1.21 click type bridge. */
public final class InventoryAccess {
	private InventoryAccess() {}
	public static void click(LocalPlayer player, int slot, int button, InventoryClick input) {
		player.inventoryMenu.clicked(slot, button, ClickType.valueOf(input.name()), player);
	}
}
