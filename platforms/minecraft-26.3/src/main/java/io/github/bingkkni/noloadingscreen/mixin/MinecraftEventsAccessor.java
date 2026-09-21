package io.github.bingkkni.noloadingscreen.mixin;

import com.mojang.blaze3d.platform.SDLEventHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The SDL event pump vanilla polls once per frame; local wait frames poll it too. */
@Mixin(Minecraft.class)
public interface MinecraftEventsAccessor {
	@Accessor("sdlEventHandler") SDLEventHandler nls$eventHandler();
}
