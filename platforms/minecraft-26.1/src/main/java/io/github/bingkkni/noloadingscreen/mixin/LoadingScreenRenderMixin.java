package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig;
import io.github.bingkkni.noloadingscreen.gui.LoadingCanvas;
import io.github.bingkkni.noloadingscreen.gui.LoadingHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mounted-screen fallback moved to GameRenderer's GUI extraction in 26.1. */
@Mixin(GameRenderer.class)
public abstract class LoadingScreenRenderMixin {
	@WrapOperation(method = "extractGui", at = @At(value = "INVOKE", target =
		"Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"), require = 0)
	private void nls$stripMountedScreen(final Screen screen, final GuiGraphicsExtractor graphics,
		final int mouseX, final int mouseY, final float partialTick, final Operation<Void> original) {
		if (NoLoadingScreenConfig.get().enabled && screen instanceof LevelLoadingScreen loading && Minecraft.getInstance().level == null) {
			screen.extractBackground(graphics, mouseX, mouseY, partialTick);
			LoadingHud.draw(new LoadingCanvas(graphics), LoadingHud.trackerOf(loading));
		} else original.call(screen, graphics, mouseX, mouseY, partialTick);
	}
}
