package io.github.bingkkni.noloadingscreen.mixin;

import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import net.minecraft.client.gui.components.CommandSuggestions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Editing must neither dereference a null player nor request completions over a play connection. */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
	@Inject(method = "updateCommandInfo", at = @At("HEAD"), cancellable = true)
	private void nls$skipLoadingSuggestions(final CallbackInfo ci) {
		if (NoLoadingScreen.isLoading()) ci.cancel();
	}
}
