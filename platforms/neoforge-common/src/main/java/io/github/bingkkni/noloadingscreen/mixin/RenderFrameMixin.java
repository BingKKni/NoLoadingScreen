package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.bingkkni.noloadingscreen.LoadingWork;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import io.github.bingkkni.noloadingscreen.platform.FrameAbort;
import io.github.bingkkni.noloadingscreen.platform.MatrixStackSnapshot;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import io.github.bingkkni.noloadingscreen.platform.TextureState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Only renderer work is bound/caught. Tasks, packets, ticks and NeoForge frame events stay vanilla. */
@Mixin(Minecraft.class)
public abstract class RenderFrameMixin {
	@WrapOperation(method = "runTick", at = @At(value = "INVOKE", target =
		"Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"))
	private void nls$renderFrame(final GameRenderer renderer, final DeltaTracker time, final boolean drawWorld,
		final Operation<Void> original) {
		long timing = LoadingWork.startTiming();
		boolean bound = PlaceholderWorld.bind();
		try {
			if (!bound) { original.call(renderer, time, drawWorld); return; }
			MatrixStackSnapshot matrices = new MatrixStackSnapshot(RenderSystem.getModelViewStack());
			var projection = RenderSystem.getProjectionMatrixBuffer();
			var projectionType = RenderSystem.getProjectionType();
			var fog = RenderSystem.getShaderFog();
			Runnable texture = TextureState.rollback();
			var color = RenderSystem.outputColorTextureOverride;
			var depth = RenderSystem.outputDepthTextureOverride;
			try {
				// runTick(false) remains false; only the world/HUD draw flag is promoted.
				original.call(renderer, time, true);
			} catch (Throwable failure) {
				try {
					((FrameAbort) renderer).nls$abortFrame();
					Minecraft client = (Minecraft) (Object) this;
					client.renderBuffers().bufferSource().endBatch();
					client.renderBuffers().crumblingBufferSource().endBatch();
					matrices.restore(RenderSystem.getModelViewStack());
					RenderSystem.setProjectionMatrix(projection, projectionType);
					RenderSystem.setShaderFog(fog);
					texture.run();
					RenderSystem.outputColorTextureOverride = color;
					RenderSystem.outputDepthTextureOverride = depth;
					PlaceholderWorld.onRenderFailed(failure);
				} catch (Throwable cleanup) {
					if (cleanup != failure) failure.addSuppressed(cleanup);
					throw failure;
				}
				// Vanilla still closes GPU profiling, blits and updates the display after this call.
			}
		} finally {
			if (bound) PlaceholderWorld.unbind();
			LoadingWork.endTiming("render frame", timing);
		}
	}
}
