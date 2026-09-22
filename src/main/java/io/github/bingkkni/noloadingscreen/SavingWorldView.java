package io.github.bingkkni.noloadingscreen;

import io.github.bingkkni.noloadingscreen.platform.ClientUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/** Disposable, interactive outgoing scene during vanilla's synchronous integrated-server save wait. */
public final class SavingWorldView {
	private static @Nullable OutgoingWorld outgoing;
	private static boolean ownsPlaceholder;

	private SavingWorldView() {}

	static boolean retains(final ClientLevel level) {
		return outgoing != null && outgoing.level() == level;
	}

	/** Called after join cleanup, before disconnect closes the listener or resets the camera. */
	public static void capture() {
		Minecraft minecraft = Minecraft.getInstance();
		outgoing = NoLoadingScreenConfig.get().enabled && minecraft.getSingleplayerServer() != null
			? OutgoingWorld.capture() : null;
	}

	/** A captured scene that vanilla's teardown has not handed over yet. */
	public static boolean pending() {
		return outgoing != null;
	}

	/** Vanilla has nulled its world, but has not detached the already-built chunk meshes yet. */
	public static void install() {
		OutgoingWorld snapshot = outgoing;
		outgoing = null;
		if (snapshot != null) {
			// GuiMixin dismisses only the saving message, after the disconnected scene is installed.
			ownsPlaceholder = snapshot.install(false);
			if (ownsPlaceholder) LoadingWaitLoop.begin();
		}
	}

	public static boolean saving() {
		return ownsPlaceholder;
	}

	public static boolean visible() {
		return saving() && PlaceholderWorld.active();
	}

	public static boolean isSavingScreen(final @Nullable Screen screen) {
		return screen instanceof GenericMessageScreen && ClientUi.savingLevel().equals(screen.getTitle());
	}

	/** Always invoked from disconnect's finally, including failed saves and render fallbacks. */
	public static void finish() {
		outgoing = null;
		try {
			// Keep the narrowly scoped local-GUI permission until our inventory/menu has closed.
			if (ownsPlaceholder && !DisconnectedWorldView.active()) PlaceholderWorld.uninstall();
		} finally {
			if (ownsPlaceholder) LoadingWaitLoop.end();
			ownsPlaceholder = false;
		}
	}

}
