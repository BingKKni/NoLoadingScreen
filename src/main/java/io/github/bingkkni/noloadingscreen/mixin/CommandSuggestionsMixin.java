package io.github.bingkkni.noloadingscreen.mixin;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.CommandDispatcher;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.bingkkni.noloadingscreen.ClientCommands;
import com.mojang.brigadier.suggestion.Suggestions;
import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.PlaceholderCommands;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
	@Shadow @Final private EditBox input;
	@Shadow @Final private List<FormattedCharSequence> commandUsage;
	@Shadow private ParseResults<ClientSuggestionProvider> currentParse;
	@Shadow private CompletableFuture<Suggestions> pendingSuggestions;
	@Shadow private boolean keepSuggestions;
	@Shadow private boolean allowSuggestions;
	@Shadow public abstract void hide();
	@Shadow public abstract void showSuggestions(boolean narrate);

	@WrapMethod(method = "updateCommandInfo")
	private void nls$localSuggestions(final Operation<Void> original) {
		if (!NoLoadingScreen.isLoading() && !ClientCommands.owns(input.getValue())) {
			original.call();
			return;
		}
		currentParse = null;
		commandUsage.clear();
		if (keepSuggestions) return;
		input.setSuggestion(null);
		hide();
		String text = input.getValue();
		int cursor = input.getCursorPosition();
		pendingSuggestions = PlaceholderCommands.suggest(text, cursor).thenCombine(ClientCommands.suggest(text, cursor),
			(local, global) -> Suggestions.merge(text, List.of(local, global)));
		if (allowSuggestions && Minecraft.getInstance().options.autoSuggestions().get()) showSuggestions(false);
	}

	/** Merge root-prefix completions; do not hide unrelated server or other mod commands. */
	@WrapOperation(method = "updateCommandInfo", at = @At(value = "INVOKE",
		target = "Lcom/mojang/brigadier/CommandDispatcher;getCompletionSuggestions(Lcom/mojang/brigadier/ParseResults;I)Ljava/util/concurrent/CompletableFuture;"))
	private CompletableFuture<Suggestions> nls$mergeClientSuggestions(CommandDispatcher<ClientSuggestionProvider> dispatcher,
		ParseResults<ClientSuggestionProvider> parse, int cursor, Operation<CompletableFuture<Suggestions>> original) {
		String text = input.getValue();
		return original.call(dispatcher, parse, cursor).thenCombine(ClientCommands.suggest(text, cursor),
			(remote, local) -> Suggestions.merge(text, List.of(remote, local)));
	}
}
