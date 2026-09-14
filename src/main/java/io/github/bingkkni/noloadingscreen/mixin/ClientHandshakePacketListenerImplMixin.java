package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.bingkkni.noloadingscreen.DisconnectedWorldView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class ClientHandshakePacketListenerImplMixin {
	@WrapOperation(method = "onDisconnect", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/Gui;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
	private void nls$retainFailedLogin(final Gui gui, final Screen screen, final Operation<Void> original) {
		// Login's vanilla failure only sets a screen. Once we own a scene, use the same complete
		// cleanup/KickWarn path as play/configuration, not a second copy of disconnection logic.
		if (DisconnectedWorldView.canRetain()) Minecraft.getInstance().disconnect(screen, false);
		else original.call(gui, screen);
	}
}
