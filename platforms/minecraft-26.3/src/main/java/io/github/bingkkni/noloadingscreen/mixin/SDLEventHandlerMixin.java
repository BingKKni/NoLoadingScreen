package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.SDLEventHandler;
import io.github.bingkkni.noloadingscreen.LoadingWaitLoop;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.3 replaced GLFW callbacks with an SDL event pump. Every input event it polls is handed to
 * {@code Minecraft.execute} from here rather than from MouseHandler/KeyboardHandler, so this is
 * where a local wait frame has to run its input inline (see {@link LoadingWaitLoop#dispatchInput}).
 * Eight call sites: keymap change, key, text input, text editing, mouse motion, button, wheel and
 * file drop. Wrapped, not redirected, so other input mods keep their normal-game scheduling.
 */
@Mixin(SDLEventHandler.class)
public abstract class SDLEventHandlerMixin {
	@WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;execute(Ljava/lang/Runnable;)V"), require = 8)
	private void nls$dispatchWaitingInput(final Minecraft minecraft, final Runnable input, final Operation<Void> original) {
		LoadingWaitLoop.dispatchInput(minecraft, input, original);
	}
}
