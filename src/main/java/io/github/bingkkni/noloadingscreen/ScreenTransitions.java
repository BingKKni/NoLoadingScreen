package io.github.bingkkni.noloadingscreen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.bingkkni.noloadingscreen.platform.ClientUi;
import io.github.bingkkni.noloadingscreen.gui.LoadingInventoryScreen;
import io.github.bingkkni.noloadingscreen.gui.LoadingPauseScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;

/** Shared screen ownership policy; loader/API mixins only supply vanilla calls. */
public final class ScreenTransitions {
	private ScreenTransitions() {}
	public static void setScreen(Screen screen, Operation<Void> original) {
		// Pack prompts and server dialogs can return to a null parent during configuration.
		boolean hideSaving = SavingWorldView.visible() && SavingWorldView.isSavingScreen(screen);
		boolean hideDisconnected = DisconnectedWorldView.hides(screen);
		boolean hideLoading = NoLoadingScreenConfig.get().enabled && screen instanceof LevelLoadingScreen && PlaceholderWorld.active();
		if (hideLoading && (ClientUi.screen(Minecraft.getInstance()) instanceof LoadingInventoryScreen
			|| ClientUi.screen(Minecraft.getInstance()) instanceof LoadingPauseScreen || ClientUi.screen(Minecraft.getInstance()) instanceof ChatScreen)) {
			return; // a phase change must not close/reinitialize the player's local menu either
		}
		boolean bound = (screen == null || hideSaving || hideDisconnected || hideLoading) && PlaceholderWorld.bind();
		try {
			Screen next = (hideSaving || hideDisconnected || hideLoading) && bound ? null : screen;
			if (next == null && !bound && Minecraft.getInstance().level == null) next = NoLoadingScreen.waitingScreen();
			original.call(next);
		} finally {
			if (bound) {
				PlaceholderWorld.unbind();
			}
		}
	}
	public static Screen intercept(Screen screen) {
		NoLoadingScreen.onScreenChanging(screen);
		if (screen instanceof LevelLoadingScreen) {
			NoLoadingScreen.markTimeline("地形加载界面出现 (加载地形中...)");
		} else if (screen == null && NoLoadingScreen.timelineActive()) {
			NoLoadingScreen.markTimeline("加载界面关闭，画面交还给玩家");
		}

		NoLoadingScreenConfig config = NoLoadingScreenConfig.get();
		if (config.enabled && screen instanceof LevelLoadingScreen) {
			// Only when a level is actually there to look at. Before the login packet arrives there
			// is no ClientLevel, and setScreen(null) would answer that with the title screen — the
			// placeholder world covers that window instead, and drops its own screen from install().
			if (Minecraft.getInstance().level != null) {
				NoLoadingScreen.markTimeline("NoLoadingScreen 隐藏了地形加载界面，直接交还画面");
				return null;
			}
		}

		return screen;
	}
}
