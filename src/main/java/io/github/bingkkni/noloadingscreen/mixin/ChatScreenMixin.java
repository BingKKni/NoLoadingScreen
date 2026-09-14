package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blocks messages while the outgoing play connection is retained for a server transfer. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
	@Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
	private void nls$blockLoadingChat(final String message, final boolean addToRecent, final CallbackInfo ci) {
		if (!message.isBlank() && NoLoadingScreen.blockOutgoingMessage(message.stripLeading().startsWith("/"))) {
			ci.cancel();
		} else if (NoLoadingScreen.isLoading()) {
			ci.cancel();
		}
	}

	@WrapMethod(method = "init")
	private void nls$initLoadingChat(final Operation<Void> original) {
		boolean bound = PlaceholderWorld.bind();
		try {
			original.call();
		} finally {
			if (bound) PlaceholderWorld.unbind();
		}
	}

	@WrapMethod(method = "mouseClicked")
	private boolean nls$loadingChatClick(final MouseButtonEvent event, final boolean doubleClick, final Operation<Boolean> original) {
		boolean bound = PlaceholderWorld.bind();
		try {
			return original.call(event, doubleClick);
		} finally {
			if (bound) PlaceholderWorld.unbind();
		}
	}
}
