package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.bingkkni.noloadingscreen.platform.FrameAbort;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererRecoveryMixin implements FrameAbort {
	@Shadow @Final private GuiRenderer guiRenderer;
	@Shadow @Final private SubmitNodeStorage submitNodeStorage;
	@Shadow @Final private FeatureRenderDispatcher featureRenderDispatcher;
	@Shadow @Final private FogRenderer fogRenderer;
	@Unique private boolean nls$fogEnded;
	@Unique private boolean nls$featuresEnded;
	@Inject(method = "render", at = @At("HEAD"))
	private void nls$beginRecoveryScope(final CallbackInfo ci) { nls$fogEnded = false; nls$featuresEnded = false; }
	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
	private void nls$trackFogEnd(final FogRenderer fog, final Operation<Void> original) {
		original.call(fog);
		nls$fogEnded = true;
	}
	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;endFrame()V"))
	private void nls$trackFeatureEnd(final FeatureRenderDispatcher features, final Operation<Void> original) {
		original.call(features);
		nls$featuresEnded = true;
	}
	@Override public void nls$abortFrame() {
		((FrameAbort) guiRenderer).nls$abortFrame();
		submitNodeStorage.clear();
		if (!nls$fogEnded) { fogRenderer.endFrame(); nls$fogEnded = true; }
		if (!nls$featuresEnded) { featureRenderDispatcher.endFrame(); nls$featuresEnded = true; }
	}
}
