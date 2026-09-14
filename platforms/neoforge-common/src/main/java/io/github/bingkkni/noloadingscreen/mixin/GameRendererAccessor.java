package io.github.bingkkni.noloadingscreen.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** In this API family FOV interpolation belongs to GameRenderer, not Camera. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("itemInHandRenderer") ItemInHandRenderer nls$hands();
    @Invoker("tickFov") void nls$tickFov();
}
