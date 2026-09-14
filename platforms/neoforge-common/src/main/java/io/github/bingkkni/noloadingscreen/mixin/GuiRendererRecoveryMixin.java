package io.github.bingkkni.noloadingscreen.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import io.github.bingkkni.noloadingscreen.platform.FrameAbort;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

/** Reset only this renderer's unfinished CPU meshes; never close shared render backends. */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererRecoveryMixin implements FrameAbort {
	@Shadow @Final private List<?> draws;
	@Shadow @Final private List<? extends AutoCloseable> meshesToDraw;
	@Shadow @Final @Mutable private ByteBufferBuilder byteBufferBuilder;
	@Shadow private BufferBuilder bufferBuilder;
	@Shadow private net.minecraft.client.gui.navigation.ScreenRectangle previousScissorArea;
	@Shadow private com.mojang.blaze3d.pipeline.RenderPipeline previousPipeline;
	@Shadow private net.minecraft.client.gui.render.TextureSetup previousTextureSetup;
	@Shadow @Final private GuiRenderState renderState;
	@Shadow private int firstDrawIndexAfterBlur;
	@Shadow @Final private Set<?> pictureInPictureRenderStatesScratch;
	@Shadow protected abstract void invalidateItemAtlas();

	@Override public void nls$abortFrame() {
		try {
			// ByteBufferBuilder.Result.close is idempotent, including meshes recordDraws closed.
			for (AutoCloseable mesh : meshesToDraw) mesh.close();
		} catch (Exception failure) { throw new IllegalStateException("Could not release GUI meshes", failure); }
		meshesToDraw.clear();
		draws.clear();
		bufferBuilder = null;
		previousScissorArea = null;
		previousPipeline = null;
		previousTextureSetup = null;
		// discard() keeps incomplete write bytes: replace the owned arena to reset those too.
		byteBufferBuilder.close();
		byteBufferBuilder = new ByteBufferBuilder(786432);
		renderState.reset();
		firstDrawIndexAfterBlur = Integer.MAX_VALUE;
		pictureInPictureRenderStatesScratch.clear();
		invalidateItemAtlas();
	}
}
