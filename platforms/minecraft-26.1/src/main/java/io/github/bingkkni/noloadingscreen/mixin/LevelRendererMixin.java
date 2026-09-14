package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.bingkkni.noloadingscreen.LoadingWork;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	@Inject(method = "onResourceManagerReload", at = @At("HEAD"))
	private void nls$invalidateFreshRenderer(final CallbackInfo ci) {
		LoadingWork.resourcesReloaded();
	}

	@WrapOperation(method = "compileSections", at = @At(value = "INVOKE", target =
		"Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;rebuildSectionSync(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;Lnet/minecraft/client/renderer/chunk/RenderRegionCache;)V"))
	private void nls$compileLoadingTerrainAsync(final SectionRenderDispatcher dispatcher,
		final SectionRenderDispatcher.RenderSection section, final RenderRegionCache cache, final Operation<Void> original) {
		if (LoadingWork.active()) section.rebuildSectionAsync(cache);
		else original.call(dispatcher, section, cache);
	}
}
