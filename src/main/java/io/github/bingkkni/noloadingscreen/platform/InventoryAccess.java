package io.github.bingkkni.noloadingscreen.platform;

import io.github.bingkkni.noloadingscreen.InventoryClick;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerInput;

/** Vanilla click type bridge. Whitelist and ownership checks live in PlaceholderInteraction. */
public final class InventoryAccess {
	private InventoryAccess() {}
	public static void click(LocalPlayer player, int slot, int button, InventoryClick input) {
		player.inventoryMenu.clicked(slot, button, ContainerInput.valueOf(input.name()), player);
	}
}
