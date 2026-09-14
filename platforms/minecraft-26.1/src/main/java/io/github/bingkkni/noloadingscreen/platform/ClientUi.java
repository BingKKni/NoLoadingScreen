package io.github.bingkkni.noloadingscreen.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Minecraft 26.1 keeps screen, overlay and chat ownership on Minecraft. */
public final class ClientUi {
	private ClientUi() {}

	public static @Nullable Screen screen(final Minecraft client) { return client.screen; }
	public static @Nullable Overlay overlay(final Minecraft client) { return client.getOverlay(); }
	public static void setScreen(final Minecraft client, final @Nullable Screen screen) { client.setScreen(screen); }
	public static ChatComponent chat(final Minecraft client) { return client.gui.getChat(); }
	public static void openChat(final Minecraft client, final ChatComponent.ChatMethod method) { client.openChatScreen(method); }
	public static void systemMessage(final Minecraft client, final Component message) { client.getChatListener().handleSystemMessage(message, false); }
	public static void localMessage(final ChatComponent chat, final Component message) { chat.addClientSystemMessage(message); }
	public static Component savingLevel() { return Component.translatable("menu.savingLevel"); }
}
