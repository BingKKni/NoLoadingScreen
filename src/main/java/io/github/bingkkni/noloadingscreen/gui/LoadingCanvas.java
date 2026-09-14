package io.github.bingkkni.noloadingscreen.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** The small drawing vocabulary used by the shared loading overlay. */
public record LoadingCanvas(GuiGraphicsExtractor graphics) {
	public int guiWidth() { return graphics.guiWidth(); }
	public int guiHeight() { return graphics.guiHeight(); }
	public void centeredText(Font font, Component text, int x, int y, int color) { graphics.centeredText(font, text, x, y, color); }
	public void fill(int left, int top, int right, int bottom, int color) { graphics.fill(left, top, right, bottom, color); }
}
