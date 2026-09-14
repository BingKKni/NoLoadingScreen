package io.github.bingkkni.noloadingscreen.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Typed access to client UI ownership, selected at compile time for the Minecraft API family. */
public final class ClientUi {
	private ClientUi() {}

	public static @Nullable Screen screen(final Minecraft client) { return client.gui.screen(); }
	public static @Nullable Overlay overlay(final Minecraft client) { return client.gui.overlay(); }
	public static void setScreen(final Minecraft client, final @Nullable Screen screen) { client.gui.setScreen(screen); }
	public static ChatComponent chat(final Minecraft client) { return client.gui.hud.getChat(); }
	public static void openChat(final Minecraft client, final ChatComponent.ChatMethod method) { client.gui.openChatScreen(method); }
	public static void systemMessage(final Minecraft client, final Component message) { client.gui.chatListener().handleSystemMessage(message, false); }
	public static void localMessage(final ChatComponent chat, final Component message) { chat.addClientSystemMessage(message); }
	public static Component savingLevel() { return Gui.SAVING_LEVEL; }
}
