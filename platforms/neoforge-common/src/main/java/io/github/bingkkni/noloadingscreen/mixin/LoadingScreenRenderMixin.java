package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig;
import io.github.bingkkni.noloadingscreen.gui.LoadingCanvas;
import io.github.bingkkni.noloadingscreen.gui.LoadingHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.ClientHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserve NeoForge's screen render events/layers while replacing only vanilla loading content. */
@Mixin(ClientHooks.class)
public abstract class LoadingScreenRenderMixin {
	@WrapOperation(method = "drawScreenInternal", at = @At(value = "INVOKE", target =
		"Lnet/minecraft/client/gui/screens/Screen;renderWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
	private static void nls$stripMountedScreen(final Screen screen, final GuiGraphics graphics,
		final int mouseX, final int mouseY, final float partialTick, final Operation<Void> original) {
		if (NoLoadingScreenConfig.get().enabled && screen instanceof LevelLoadingScreen loading && Minecraft.getInstance().level == null) {
			screen.renderBackground(graphics, mouseX, mouseY, partialTick);
			LoadingHud.draw(new LoadingCanvas(graphics), LoadingHud.trackerOf(loading));
		} else original.call(screen, graphics, mouseX, mouseY, partialTick);
	}
}
