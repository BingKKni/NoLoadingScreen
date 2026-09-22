package io.github.bingkkni.noloadingscreen;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import io.github.bingkkni.noloadingscreen.gui.NoLoadingScreenOptionsScreen;
import io.github.bingkkni.noloadingscreen.platform.ClientUi;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Loader-independent client commands. The remote command tree and connection remain untouched. */
public final class ClientCommands {
	private static final Object SOURCE = new Object();
	private static final CommandDispatcher<Object> DISPATCHER = create();
	private static Runnable pending;
	private ClientCommands() {}

	private static CommandDispatcher<Object> create() {
		var dispatcher = new CommandDispatcher<Object>();
		for (String root : new String[]{"nls", "noloadingscreen"}) {
			dispatcher.register(LiteralArgumentBuilder.<Object>literal(root).executes(context -> {
				pending = () -> ClientUi.setScreen(Minecraft.getInstance(), new NoLoadingScreenOptionsScreen(null));
				return 1;
			}));
		}
		dispatcher.register(LiteralArgumentBuilder.<Object>literal("nlsdebug")
			.executes(context -> {
				String root = context.getNodes().getFirst().getNode().getName();
				message(Component.translatable("noloadingscreen.command.debugHelp", root));
				return 1;
			})
			.then(LiteralArgumentBuilder.<Object>literal("kick").executes(context -> {
				Minecraft client = Minecraft.getInstance();
				var expected = client.level;
				pending = () -> {
					if (client.level != expected || !DisconnectedWorldView.simulateKick())
						message(Component.translatable("noloadingscreen.command.kickUnavailable"));
				};
				return 1;
			})));
		return dispatcher;
	}

	public static boolean owns(String text) {
		if (!text.startsWith("/")) return false;
		int end = 1;
		while (end < text.length() && !Character.isWhitespace(text.charAt(end))) end++;
		return DISPATCHER.getRoot().getChild(text.substring(1, end)) != null;
	}

	public static boolean execute(String text) {
		if (!owns(text)) return false;
		try { DISPATCHER.execute(text.substring(1), SOURCE); }
		catch (CommandSyntaxException error) { message(Component.literal(error.getMessage())); }
		return true; // even malformed local commands must never fall through to the server
	}

	/** Run after ChatScreen has closed itself, including inside our isolated save-wait loop. */
	public static void runPending() {
		Runnable action = pending;
		pending = null;
		if (action != null) action.run();
	}

	public static CompletableFuture<Suggestions> suggest(String text, int cursor) {
		if (!text.startsWith("/") || cursor < 1 || cursor > text.length()) return Suggestions.empty();
		StringReader reader = new StringReader(text);
		reader.skip();
		return DISPATCHER.getCompletionSuggestions(DISPATCHER.parse(reader, SOURCE), cursor);
	}

	private static void message(Component message) {
		ClientUi.localMessage(ClientUi.chat(Minecraft.getInstance()), message);
	}
}
