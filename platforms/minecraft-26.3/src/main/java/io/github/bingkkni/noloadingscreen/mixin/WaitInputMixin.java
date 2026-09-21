package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.SDLEventHandler;
import io.github.bingkkni.noloadingscreen.LoadingWaitLoop;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import io.github.bingkkni.noloadingscreen.SavingWorldView;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.3's synchronous boot and save loops call {@code RenderSystem.pumpEvents} before every frame,
 * which flushes all queued input before polling: vanilla's way of ignoring the mouse and keyboard
 * while a menu covers the wait. When this mod's local frame draws that wait instead, the local
 * frame polls the same events itself, so the flush would only discard input a moment before it is
 * read. Skipped under exactly the conditions {@code MinecraftMixin} substitutes the local frame.
 */
@Mixin(Minecraft.class)
public abstract class WaitInputMixin {
	private static final String PUMP = "Lcom/mojang/blaze3d/systems/RenderSystem;pumpEvents(Lcom/mojang/blaze3d/platform/SDLEventHandler;)V";

	@WrapOperation(method = "doWorldLoad", at = @At(value = "INVOKE", target = PUMP))
	private void nls$keepBootInput(final SDLEventHandler handler, final Operation<Void> original) {
		if (!(LoadingWaitLoop.active() && PlaceholderWorld.active())) original.call(handler);
	}

	@WrapOperation(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At(value = "INVOKE", target = PUMP))
	private void nls$keepSaveInput(final SDLEventHandler handler, final Operation<Void> original) {
		if (!SavingWorldView.visible()) original.call(handler);
	}
}
