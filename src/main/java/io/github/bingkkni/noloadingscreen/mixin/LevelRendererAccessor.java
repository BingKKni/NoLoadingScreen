package io.github.bingkkni.noloadingscreen.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Access to the lazily-created sky renderer for the first placeholder frame. */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
	@Accessor("skyRenderer")
	SkyRenderer nls$skyRenderer();

	@Accessor("skyRenderer")
	void nls$setSkyRenderer(SkyRenderer skyRenderer);
}
