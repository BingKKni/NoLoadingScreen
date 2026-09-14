package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.bingkkni.noloadingscreen.LoadingWork;
import io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public abstract class SodiumWorldRendererMixin {
	@Shadow @Final private Minecraft client;
	@Shadow private int renderDistance;
	@Unique private boolean nls$freshRenderer;
	@Unique private long nls$resourceGeneration;

	@WrapMethod(method = "initRenderer")
	private void nls$measureRendererInitialization(@org.spongepowered.asm.mixin.injection.Coerce final Object commands, final Operation<Void> original) {
		long timing = LoadingWork.startTiming();
		try { original.call(commands); } finally { LoadingWork.endTiming("Sodium renderer initialization", timing); }
	}

	@Inject(method = "loadLevel", at = @At("RETURN"))
	private void nls$rememberFreshRenderer(final CallbackInfo ci) {
		this.nls$freshRenderer = NoLoadingScreenConfig.get().enabled;
		this.nls$resourceGeneration = LoadingWork.resourceGeneration();
	}

	@Inject(method = "reload", at = @At("HEAD"), cancellable = true)
	private void nls$coalesceFirstReload(final CallbackInfo ci) {
		boolean fresh = this.nls$freshRenderer;
		this.nls$freshRenderer = false;
		// setLevel builds Sodium state immediately, then vanilla invalidates geometry on the
		// first extraction. Rebuilding an unused, same-distance/same-resource renderer is redundant.
		if (fresh && NoLoadingScreenConfig.get().enabled && this.nls$resourceGeneration == LoadingWork.resourceGeneration()
			&& this.renderDistance == this.client.options.getEffectiveRenderDistance()) ci.cancel();
	}

	@Inject(method = {"setupTerrain", "scheduleRebuildForChunk", "unloadLevel"}, at = @At("HEAD"))
	private void nls$rendererNoLongerFresh(final CallbackInfo ci) {
		this.nls$freshRenderer = false;
	}
}
