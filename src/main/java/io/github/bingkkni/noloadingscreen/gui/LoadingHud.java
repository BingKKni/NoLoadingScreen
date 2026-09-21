package io.github.bingkkni.noloadingscreen.gui;

import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.SavingWorldView;
import io.github.bingkkni.noloadingscreen.mixin.LevelLoadingScreenAccessor;
import io.github.bingkkni.noloadingscreen.platform.ClientUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import org.jspecify.annotations.Nullable;

/** Vanilla status/bar layout without the chunk rectangle, custom phase text or stopwatch. */
public final class LoadingHud {
	private static float smoothedProgress;
	private LoadingHud() {}
	public static void resetSmoothing() { smoothedProgress = 0; }
	public static @Nullable LevelLoadTracker trackerOf(LevelLoadingScreen screen) {
		return ((LevelLoadingScreenAccessor) screen).nls$loadTracker();
	}
	public static void draw(LoadingCanvas graphics, @Nullable LevelLoadTracker tracker) {
		Font font = Minecraft.getInstance().font;
		int center = graphics.guiWidth() / 2;
		if (SavingWorldView.visible()) {
			// Vanilla actionbar origin is height-68, with the glyph baseline offset at -4.
			graphics.centeredText(font, ClientUi.savingLevel(), center, graphics.guiHeight() - 72, 0xFFFFFFFF);
			return;
		}
		ChunkLoadStatusView view = tracker != null ? tracker.statusView() : null;
		int top = graphics.guiHeight() / 2 - (view != null ? view.radius() * 2 + 27 : 50);
		graphics.centeredText(font, NoLoadingScreen.vanillaLoadingMessage(), center, top, 0xFFFFFFFF);
		if (tracker != null && tracker.hasProgress()) {
			smoothedProgress += (tracker.serverProgress() - smoothedProgress) * 0.2F;
			graphics.fill(center - 100, top + 12, center + 100, top + 14, 0xFF000000);
			graphics.fill(center - 100, top + 12, center - 100 + Math.round(smoothedProgress * 200), top + 14, 0xFF00FF00);
		}
	}
}
