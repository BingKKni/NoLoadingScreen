package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.bingkkni.noloadingscreen.LoadingWaitLoop;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Inject(method = "handleDebugKeys", at = @At("HEAD"), cancellable = true)
	private void nls$blockLoadingDebugActions(final KeyEvent event, final CallbackInfoReturnable<Boolean> cir) {
		// F3+N/I dereference teardown's partial player/gameMode or issue game requests; F3+T
		// starts a nested resource reload. None belongs in the disposable local input whitelist.
		if (PlaceholderWorld.active()) cir.setReturnValue(true);
	}

	// Only setup's input callbacks, not debug actions or unrelated Minecraft executables.
	// Wrap instead of redirecting: preserve ViaFabricPlus's normal-game input scheduling.
	@WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;execute(Ljava/lang/Runnable;)V"), require = 3)
	private void nls$dispatchWaitingKey(final Minecraft minecraft, final Runnable input, final Operation<Void> original) {
		LoadingWaitLoop.dispatchInput(minecraft, input, original);
	}
}
