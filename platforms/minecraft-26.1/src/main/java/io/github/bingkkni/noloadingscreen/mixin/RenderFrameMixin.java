package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.TracyFrameCapture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.TimerQuery;
import io.github.bingkkni.noloadingscreen.DisconnectedWorldView;
import io.github.bingkkni.noloadingscreen.LoadingWork;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import io.github.bingkkni.noloadingscreen.SavingWorldView;
import io.github.bingkkni.noloadingscreen.platform.FrameAbort;
import io.github.bingkkni.noloadingscreen.platform.MatrixStackSnapshot;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** The 26.1 frame includes camera update and extraction, but no live ticks or packet drain. */
@Mixin(Minecraft.class)
public abstract class RenderFrameMixin {
	@Unique private boolean nls$presentationStarted;

	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target =
		"Lcom/mojang/blaze3d/systems/RenderSystem;flipFrame(Lcom/mojang/blaze3d/TracyFrameCapture;)V"))
	private void nls$presentFrame(final TracyFrameCapture capture, final Operation<Void> original) {
		nls$presentationStarted = true;
		original.call(capture);
	}

	@WrapMethod(method = "renderFrame")
	private void nls$renderFrame(final boolean advanceGameTime, final Operation<Void> original) {
		long timing = LoadingWork.startTiming();
		boolean bound = PlaceholderWorld.bind();
		boolean previousPresentation = nls$presentationStarted;
		nls$presentationStarted = false;
		try {
			if (!bound) {
				original.call(advanceGameTime);
				return;
			}
			Minecraft client = (Minecraft) (Object) this;
			MatrixStackSnapshot matrices = new MatrixStackSnapshot(RenderSystem.getModelViewStack());
			var projection = RenderSystem.getProjectionMatrixBuffer();
			var projectionType = RenderSystem.getProjectionType();
			var fog = RenderSystem.getShaderFog();
			var color = RenderSystem.outputColorTextureOverride;
			var depth = RenderSystem.outputDepthTextureOverride;
			boolean queryWasRecording = TimerQuery.getInstance().isRecording();
			try {
				original.call(advanceGameTime || SavingWorldView.visible() || DisconnectedWorldView.visible());
			} catch (Throwable failure) {
				// A failed/finished backend presentation cannot safely be repeated.
				if (nls$presentationStarted) throw failure;
				try {
					((FrameAbort) client.gameRenderer).nls$abortFrame();
					client.renderBuffers().bufferSource().endBatch();
					client.renderBuffers().crumblingBufferSource().endBatch();
					matrices.restore(RenderSystem.getModelViewStack());
					RenderSystem.setProjectionMatrix(projection, projectionType);
					RenderSystem.setShaderFog(fog);
					RenderSystem.outputColorTextureOverride = color;
					RenderSystem.outputDepthTextureOverride = depth;
					// The GL backend has no acquired surface, but skipped frame tails still own a
					// timer query and per-frame GPU arenas. Release them before the next frame.
					if (!queryWasRecording && TimerQuery.getInstance().isRecording()) {
						TimerQuery.getInstance().endProfile().cancel();
					}
					RenderSystem.flipFrame(null);
					PlaceholderWorld.onRenderFailed(failure);
				} catch (Throwable cleanup) {
					if (cleanup != failure) failure.addSuppressed(cleanup);
					throw failure;
				}
			}
		} finally {
			nls$presentationStarted = previousPresentation;
			if (bound) PlaceholderWorld.unbind();
			LoadingWork.endTiming("render frame", timing);
		}
	}
}
