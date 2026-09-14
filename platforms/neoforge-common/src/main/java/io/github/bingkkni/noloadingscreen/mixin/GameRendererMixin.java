package io.github.bingkkni.noloadingscreen.mixin;

import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
	private DeltaTracker nls$preparePlaceholderCamera(final DeltaTracker time) { return PlaceholderWorld.prepareRender(time); }

	@Inject(method = "renderLevel", at = @At(value = "INVOKE", target =
		"Lnet/minecraft/client/renderer/GameRenderer;extractCamera(F)V", shift = At.Shift.AFTER))
	private void nls$refreshEnvironment(final DeltaTracker time, final CallbackInfo ci) { PlaceholderWorld.refreshEnvironment(); }
}
