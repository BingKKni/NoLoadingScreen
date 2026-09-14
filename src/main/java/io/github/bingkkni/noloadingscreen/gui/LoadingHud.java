package io.github.bingkkni.noloadingscreen.gui;

import io.github.bingkkni.noloadingscreen.platform.ClientUi;
import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.JoinPhase;
import io.github.bingkkni.noloadingscreen.SavingWorldView;
import net.minecraft.client.gui.Gui;
import io.github.bingkkni.noloadingscreen.mixin.LevelLoadingScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import org.jspecify.annotations.Nullable;

/**
 * Only the progress bar and status text from the loading UI, never its central chunk rectangle.
 *
 * <p>Progress and layout still come from vanilla's tracker. Keeping the text/bar above the old
 * rectangle's position avoids moving the overlay as the loading screen gives way to the world.
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

	public static void draw(final LoadingCanvas graphics, final @Nullable LevelLoadTracker tracker) {
		Font font = Minecraft.getInstance().font;
		int xCenter = graphics.guiWidth() / 2;
		int yCenter = graphics.guiHeight() / 2;
		if (SavingWorldView.visible()) {
			graphics.centeredText(font, ClientUi.savingLevel(), xCenter, graphics.guiHeight() - 50, TEXT_COLOUR);
			return;
		}

		int textTop;
		ChunkLoadStatusView statusView = tracker != null ? tracker.statusView() : null;
		if (statusView != null) {
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
				// Keep the hint above the hotbar/inventory area instead of drawing through its slots.
				graphics.guiHeight() - LINE_HEIGHT * 5,
				HINT_COLOUR
			);
		}
	}
}
