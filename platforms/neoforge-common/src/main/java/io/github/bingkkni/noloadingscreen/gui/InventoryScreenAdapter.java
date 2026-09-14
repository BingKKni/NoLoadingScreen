package io.github.bingkkni.noloadingscreen.gui;

import io.github.bingkkni.noloadingscreen.PlaceholderInteraction;
import io.github.bingkkni.noloadingscreen.InventoryClick;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;

/** API-owned inventory drawing and vanilla click conversion; local behavior stays shared. */
abstract class InventoryScreenAdapter extends AbstractContainerScreen<InventoryMenu> {
	protected final LocalPlayer player;
	protected InventoryScreenAdapter(LocalPlayer player) {
		super(player.inventoryMenu, player.getInventory(), Component.translatable("container.inventory"));
		this.player = player;
	}
	@Override
	protected void renderBg(final GuiGraphics graphics, final float partialTick, final int mouseX, final int mouseY) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_LOCATION, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
		InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, this.leftPos + 26, this.topPos + 8,
			this.leftPos + 75, this.topPos + 78, 30, .0625F, mouseX, mouseY, this.player);
	}

	@Override
	protected void renderLabels(final GuiGraphics graphics, final int mouseX, final int mouseY) {
		// Same crafting label as vanilla; no extra title, snapshot text, or read-only warning.
		graphics.drawString(this.font, Component.translatable("container.crafting"), 97, 6, 0xFF404040, false);
	}

	@Override
	protected void slotClicked(final Slot slot, final int slotId, final int button, final ClickType input) {
		PlaceholderInteraction.clickSlot(this.player, slot != null ? slot.index : slotId, button, InventoryClick.valueOf(input.name()));
	}

}
