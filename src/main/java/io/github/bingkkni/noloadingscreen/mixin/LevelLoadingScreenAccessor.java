package io.github.bingkkni.noloadingscreen.mixin;

import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The tracker a mounted loading screen is showing. During singleplayer world creation this is the
 * one {@code Minecraft#doWorldLoad} built, which no client-side code has handed to us yet — the
 * screen is the only thing holding it.
 */
@Mixin(LevelLoadingScreen.class)
public interface LevelLoadingScreenAccessor {
	@Accessor("loadTracker")
	LevelLoadTracker nls$loadTracker();
}
