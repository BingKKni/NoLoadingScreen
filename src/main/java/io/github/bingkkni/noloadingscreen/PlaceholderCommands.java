package io.github.bingkkni.noloadingscreen;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import io.github.bingkkni.noloadingscreen.platform.ClientUi;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

/** Private dispatcher. Never reads/replaces the server dispatcher or requests remote completions. */
public final class PlaceholderCommands {
	private static final Object SOURCE = new Object();
	private static final CommandDispatcher<Object> DISPATCHER = create();
	private PlaceholderCommands() {}

	private static CommandDispatcher<Object> create() {
		var dispatcher = new CommandDispatcher<Object>();
		var modes = LiteralArgumentBuilder.<Object>literal("gamemode");
		for (GameType mode : GameType.values()) {
			modes.then(LiteralArgumentBuilder.<Object>literal(mode.getName()).executes(context -> {
				if (!PlaceholderWorld.setLocalMode(mode)) return 0;
				message(Component.translatable("commands.gamemode.success.self", mode.getLongDisplayName()));
				return 1;
			}));
		}
		dispatcher.register(modes);
		dispatcher.register(LiteralArgumentBuilder.<Object>literal("tp")
			.then(RequiredArgumentBuilder.<Object, String>argument("player", StringArgumentType.word())
				.suggests((context, builder) -> {
					for (String name : PlaceholderWorld.playerNames()) if (name.startsWith(builder.getRemaining())) builder.suggest(name);
					return builder.buildFuture();
				}).executes(context -> {
					String name = StringArgumentType.getString(context, "player");
					if (!PlaceholderWorld.teleportToPlayer(name)) message(Component.translatable("noloadingscreen.message.spectatorTarget"));
					return 1;
				})));
		return dispatcher;
	}

	public static boolean execute(String message) {
		if (!PlaceholderWorld.active() || !message.startsWith("/")) return false;
		String command = message.substring(1);
		if (!(command.equals("gamemode") || command.startsWith("gamemode ") || command.equals("tp") || command.startsWith("tp "))) return false;
		try { DISPATCHER.execute(command, SOURCE); }
		catch (CommandSyntaxException error) { message(Component.literal(error.getMessage())); }
		return true;
	}

	public static CompletableFuture<Suggestions> suggest(String text, int cursor) {
		if (!PlaceholderWorld.active() || !text.startsWith("/")) return Suggestions.empty();
		StringReader reader = new StringReader(text);
		reader.skip();
		return DISPATCHER.getCompletionSuggestions(DISPATCHER.parse(reader, SOURCE), cursor);
	}

	private static void message(Component message) {
		ClientUi.localMessage(ClientUi.chat(Minecraft.getInstance()), message);
	}
}
