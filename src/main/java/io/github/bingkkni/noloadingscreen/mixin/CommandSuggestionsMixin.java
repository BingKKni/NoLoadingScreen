package io.github.bingkkni.noloadingscreen.mixin;

import com.mojang.brigadier.ParseResults;
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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

	@Inject(method = "updateCommandInfo", at = @At("HEAD"), cancellable = true)
	private void nls$localSuggestions(final CallbackInfo ci) {
		if (!NoLoadingScreen.isLoading()) return;
		ci.cancel();
		currentParse = null;
		commandUsage.clear();
		if (keepSuggestions) return;
		input.setSuggestion(null);
		hide();
		pendingSuggestions = PlaceholderCommands.suggest(input.getValue(), input.getCursorPosition());
		if (allowSuggestions && Minecraft.getInstance().options.autoSuggestions().get()) showSuggestions(false);
	}
}
