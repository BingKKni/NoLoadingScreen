package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import io.github.bingkkni.noloadingscreen.LoadingWaitLoop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	// Chain with other input hooks (notably ViaFabricPlus's storeEvent Redirect). A second
	// Redirect consumes their injection point and crashes before the game window can open.
	@WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;execute(Ljava/lang/Runnable;)V"), require = 4)
	private void nls$dispatchWaitingMouse(final Minecraft minecraft, final Runnable input, final Operation<Void> original) {
		LoadingWaitLoop.dispatchInput(minecraft, input, original);
	}

	@WrapMethod(method = "onScroll")
	private void nls$scrollPlaceholder(final long handle, final double x, final double y, final Operation<Void> original) {
		// Vanilla handles wheel sensitivity, fractional deltas and GUI scrolling correctly, but
		// ignores hotbar scrolling when player is null. The scroll path itself sends no packets.
		boolean bound = PlaceholderWorld.bind();
		try {
			original.call(handle, x, y);
		} finally {
			if (bound) PlaceholderWorld.unbind();
		}
	}
}
