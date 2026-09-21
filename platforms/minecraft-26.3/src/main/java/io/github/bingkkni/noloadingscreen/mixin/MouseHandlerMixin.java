package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Mixin;

/** Input scheduling moved to SDLEventHandler in 26.3; only the wheel still needs a bound player here. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
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
