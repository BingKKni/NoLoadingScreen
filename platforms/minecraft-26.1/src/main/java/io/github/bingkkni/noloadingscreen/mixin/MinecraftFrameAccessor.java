package io.github.bingkkni.noloadingscreen.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftFrameAccessor {
	@Invoker("renderFrame")
	void nls$renderFrame(boolean advanceGameTime);
}
