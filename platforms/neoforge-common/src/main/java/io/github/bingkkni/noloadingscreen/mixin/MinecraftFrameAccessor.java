package io.github.bingkkni.noloadingscreen.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftFrameAccessor {
	@Invoker("runTick") void nls$runFrame(boolean advanceGameTime);
}
