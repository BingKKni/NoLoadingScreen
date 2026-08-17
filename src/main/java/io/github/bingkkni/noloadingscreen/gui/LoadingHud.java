package io.github.bingkkni.noloadingscreen.gui;

import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.JoinPhase;
import io.github.bingkkni.noloadingscreen.mixin.LevelLoadingScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import org.jspecify.annotations.Nullable;

/**
 * What is left of {@code LevelLoadingScreen} once the screen itself is gone: the progress bar, the
 * chunk map, and — new — a line of text saying which part of the join you are actually waiting on.
 *
 * <p>The bar and the map come straight out of vanilla's own tracker ({@code serverProgress()},
 * {@code statusView()} and the {@code public static extractChunksForRendering} the screen already
 * exposes), drawn in the same place at the same size. That matters: a join crosses a point where
 * the screen has to disappear and the picture has to survive the crossing without moving, or it
 * reads as a flicker rather than as one continuous indicator.
 *
 * <p>Both of those sources are empty in multiplayer — {@code serverChunkStatusView} is only ever set
 * by {@code Minecraft#doWorldLoad} and {@code hasProgress()} needs a {@code LevelLoadListener}
 * callback that only an integrated server makes, and no packet carries either. That is exactly the
 * case where vanilla's screen is a blank panel with one untranslated word on it, so the status line
 * is drawn unconditionally: on a server it is the only thing there is to say.
 */
public final class LoadingHud {
	private static final int BAR_WIDTH = 200;
	private static final int BAR_HEIGHT = 2;
	private static final int LINE_HEIGHT = 9;
	private static final int BAR_BACKGROUND = 0xFF000000;
	private static final int BAR_FILL = 0xFF00FF00;
	private static final int TEXT_COLOUR = 0xFFFFFFFF;
	private static final int HINT_COLOUR = 0xFFA0A0A0;

	/** Vanilla smooths the bar in {@code tick()}; there is no tick here, so it happens per frame. */
	private static float smoothedProgress;

	private LoadingHud() {
	}

	public static void resetSmoothing() {
		smoothedProgress = 0.0F;
	}

	/** The tracker a mounted loading screen is showing — the only reference to it that exists. */
	public static @Nullable LevelLoadTracker trackerOf(final LevelLoadingScreen screen) {
		return ((LevelLoadingScreenAccessor) screen).nls$loadTracker();
	}

	/** False on a remote server, where neither of the tracker's two sources is ever populated. */
	public static boolean hasAnythingToDraw(final @Nullable LevelLoadTracker tracker) {
		return tracker != null && (tracker.statusView() != null || tracker.hasProgress());
	}

	public static void draw(final GuiGraphicsExtractor graphics, final @Nullable LevelLoadTracker tracker) {
		Font font = Minecraft.getInstance().font;
		int xCenter = graphics.guiWidth() / 2;
		int yCenter = graphics.guiHeight() / 2;

		int textTop;
		ChunkLoadStatusView statusView = tracker != null ? tracker.statusView() : null;
		if (statusView != null) {
			LevelLoadingScreen.extractChunksForRendering(graphics, xCenter, yCenter, 2, 0, statusView);
			textTop = yCenter - statusView.radius() * 2 - LINE_HEIGHT * 3;
		} else {
			textTop = yCenter - 50;
		}

		JoinPhase phase = NoLoadingScreen.phase();
		if (phase != JoinPhase.NONE) {
			graphics.centeredText(font, Component.translatable(phase.translationKey()), xCenter, textTop, TEXT_COLOUR);
			graphics.centeredText(
				font,
				Component.translatable("noloadingscreen.hud.elapsed", String.format("%.1f", NoLoadingScreen.phaseElapsedMs() / 1000.0F)),
				xCenter,
				textTop - LINE_HEIGHT - 2,
				HINT_COLOUR
			);
		}

		if (tracker != null && tracker.hasProgress()) {
			smoothedProgress += (tracker.serverProgress() - smoothedProgress) * 0.2F;
			int left = xCenter - BAR_WIDTH / 2;
			int top = textTop + LINE_HEIGHT + 3;
			graphics.fill(left, top, left + BAR_WIDTH, top + BAR_HEIGHT, BAR_BACKGROUND);
			graphics.fill(left, top, left + Math.round(smoothedProgress * BAR_WIDTH), top + BAR_HEIGHT, BAR_FILL);
		}

		if (NoLoadingScreen.canRevealConfigScreen()) {
			graphics.centeredText(
				font,
				Component.translatable("noloadingscreen.hud.configHint"),
				xCenter,
				graphics.guiHeight() - LINE_HEIGHT * 3,
				HINT_COLOUR
			);
		}
	}
}
