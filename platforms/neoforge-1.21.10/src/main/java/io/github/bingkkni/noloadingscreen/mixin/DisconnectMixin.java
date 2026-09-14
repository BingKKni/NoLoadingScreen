package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.bingkkni.noloadingscreen.DisconnectedWorldView;
import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.SavingWorldView;
import io.github.bingkkni.noloadingscreen.LoadingWaitLoop;
import io.github.bingkkni.noloadingscreen.platform.WaitFrame;
import io.github.bingkkni.noloadingscreen.platform.DisconnectHandoff;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class DisconnectMixin {
	@Shadow private Connection pendingConnection;
	@WrapMethod(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V")
	private void nls$disconnect(final Screen screen, final boolean keepResourcePacks, final Operation<Void> original) {
		boolean keep = DisconnectedWorldView.begin(screen, keepResourcePacks);
		NoLoadingScreen.onDisconnected(keep);
		if (!keep) SavingWorldView.capture();
		boolean completed = false;
		try {
			original.call(screen, keepResourcePacks);
			completed = true;
		} finally {
			SavingWorldView.finish();
			if (keep) DisconnectedWorldView.finish(completed);
		}
	}

	@Inject(
		method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V")
	)
	private void nls$showSavingWorld(final Screen screen, final boolean keepResourcePacks, final CallbackInfo ci) {
		DisconnectHandoff.releaseLiveFields((Minecraft) (Object) this);
		SavingWorldView.install();
	}

	@Redirect(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTick(Z)V"))
	private void nls$interactiveSaveFrame(final Minecraft minecraft, final boolean advanceGameTime) {
		if (SavingWorldView.visible()) LoadingWaitLoop.frame(minecraft);
		else WaitFrame.draw(minecraft, advanceGameTime);
	}

	@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreenAndShow(Lnet/minecraft/client/gui/screens/Screen;)V"))
	private void nls$savingFinished(final Screen screen, final boolean keepResourcePacks, final CallbackInfo ci) {
		SavingWorldView.finish();
		if (DisconnectedWorldView.active()) DisconnectHandoff.releaseLiveFields((Minecraft) (Object) this);
		DisconnectedWorldView.install();
	}

	@WrapOperation(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;resetData()V"))
	private void nls$keepDisconnectedCamera(final GameRenderer renderer, final Operation<Void> original) {
		if (!DisconnectedWorldView.active()) original.call(renderer);
	}

	@WrapOperation(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/Minecraft;updateLevelInEngines(Lnet/minecraft/client/multiplayer/ClientLevel;)V"))
	private void nls$keepDisconnectedEngines(final Minecraft minecraft, final ClientLevel level,
		final Operation<Void> original) {
		if (!DisconnectedWorldView.visible()) {
			original.call(minecraft, level);
			return;
		}
		// Like configuration adoption, retain built meshes, but never retain a connection owner.
		minecraft.getSoundManager().stop();
		this.pendingConnection = null;
		minecraft.updateTitle();
	}

}
