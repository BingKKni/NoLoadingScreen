package io.github.bingkkni.noloadingscreen.mixin;

import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {
	@Unique private volatile boolean nls$earlyPlaceholder;

	@Inject(method = "updateStatus", at = @At("TAIL"))
	private void nls$observeEncryption(final Component status, final CallbackInfo ci) {
		// This callback can run on the authentication/Netty thread. Publish only a flag here;
		// construction, rendering and screen changes belong exclusively to the next client tick.
		if (status.getContents() instanceof TranslatableContents text
			&& (text.getKey().equals("connect.encrypting") || text.getKey().equals("connect.joining"))) {
			nls$earlyPlaceholder = true; // offline-mode servers skip encryption altogether
		}
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void nls$earlyConnectWorld(final CallbackInfo ci) {
		if (nls$earlyPlaceholder) NoLoadingScreen.onConnecting((ConnectScreen) (Object) this);
	}
}
