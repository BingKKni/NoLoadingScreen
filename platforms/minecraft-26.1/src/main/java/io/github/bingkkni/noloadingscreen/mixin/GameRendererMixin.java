package io.github.bingkkni.noloadingscreen.mixin;

import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@ModifyVariable(method = "update", at = @At("HEAD"), argsOnly = true)
	private DeltaTracker nls$preparePlaceholderCamera(final DeltaTracker deltaTracker) {
		return PlaceholderWorld.prepareRender(deltaTracker);
	}

	@Inject(method = "update", at = @At("RETURN"))
	private void nls$refreshEnvironment(final DeltaTracker deltaTracker, final boolean advanceGameTime, final CallbackInfo ci) {
		PlaceholderWorld.refreshEnvironment();
	}

	@ModifyVariable(method = {"extract", "render"}, at = @At("HEAD"), argsOnly = true)
	private DeltaTracker nls$freezePlaceholderScene(final DeltaTracker deltaTracker) {
		return PlaceholderWorld.renderDelta(deltaTracker);
	}
}
