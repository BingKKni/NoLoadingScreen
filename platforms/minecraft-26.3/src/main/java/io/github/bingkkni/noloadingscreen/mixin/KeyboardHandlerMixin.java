package io.github.bingkkni.noloadingscreen.mixin;

import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Input scheduling moved to SDLEventHandler in 26.3; the debug-key guard is unchanged. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Inject(method = "handleDebugKeys", at = @At("HEAD"), cancellable = true)
	private void nls$blockLoadingDebugActions(final KeyEvent event, final CallbackInfoReturnable<Boolean> cir) {
		// F3+N/I dereference teardown's partial player/gameMode or issue game requests; F3+T
		// starts a nested resource reload. None belongs in the disposable local input whitelist.
		if (PlaceholderWorld.active()) cir.setReturnValue(true);
	}
}
