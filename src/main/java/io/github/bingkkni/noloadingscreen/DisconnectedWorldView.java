package io.github.bingkkni.noloadingscreen;

import io.github.bingkkni.noloadingscreen.platform.ClientUi;
import io.github.bingkkni.noloadingscreen.mixin.DisconnectedScreenAccessor;
import io.github.bingkkni.noloadingscreen.mixin.LivingEntityAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** KickWarn: vanilla closes the session, while its disposable scene stays until an explicit exit. */
public final class DisconnectedWorldView {
	private static @Nullable DisconnectedScreen fallback;
	private static @Nullable OutgoingWorld outgoing;
	private static ChatComponent.@Nullable State chatState;

	private DisconnectedWorldView() {}

	public static boolean active() {
		return fallback != null;
	}

	public static boolean visible() {
		return active() && PlaceholderWorld.active();
	}

	/** An intentional exit/transfer and all integrated-server paths remain vanilla. */
	public static boolean canRetain() {
		Minecraft minecraft = Minecraft.getInstance();
		return NoLoadingScreenConfig.get().enabled && !active() && !minecraft.isLocalServer()
			&& minecraft.getSingleplayerServer() == null && !SavingWorldView.saving() && !NoLoadingScreen.preparingResources()
			&& (PlaceholderWorld.active() || minecraft.level != null && minecraft.player != null);
	}

	/** Called before Minecraft.disconnect clears the player, chat and renderer. */
	public static boolean begin(final Screen screen, final boolean transferring) {
		if (transferring || !(screen instanceof DisconnectedScreen disconnected) || !canRetain()) return false;
		outgoing = PlaceholderWorld.active() ? null : OutgoingWorld.capture();
		chatState = ClientUi.chat(Minecraft.getInstance()).storeState();
		fallback = disconnected; // retain vanilla's exact details, report links and return destination
		return true;
	}

	/** After the real level is nulled, but before the forced disconnect frame/engine detachment. */
	public static void install() {
		if (!active()) return;
		OutgoingWorld snapshot = outgoing;
		outgoing = null;
		if (!(PlaceholderWorld.active() || snapshot != null && snapshot.install(false))) {
			// A kick at the death screen (or before a real player exists) can still use the void scene.
			var registries = PlaceholderRegistries.ready();
			if (registries != null) PlaceholderWorld.synthesise(registries, null, null, null,
				new Vec3(0.5, 80, 0.5), 0, 0, false);
		}
		if (PlaceholderWorld.bind()) {
			try {
				Minecraft minecraft = Minecraft.getInstance();
				GameType mode = minecraft.gameMode.getPlayerMode();
				if (mode == GameType.ADVENTURE || mode == GameType.SPECTATOR) {
					// Vanilla updates build permissions and abilities without sending a packet.
					// Keep the sandbox's inherited flight; its controls still own flight/noclip.
					boolean flying = minecraft.player.getAbilities().flying;
					minecraft.gameMode.setLocalMode(GameType.SURVIVAL);
					minecraft.player.getAbilities().flying = flying;
					// Spectator invisibility was synchronized separately from game mode. Restore
					// vanilla's effect-based visibility, without stripping an invisibility potion.
					if (mode == GameType.SPECTATOR) ((LivingEntityAccessor) minecraft.player).nls$updateInvisibilityStatus();
				}
			} finally { PlaceholderWorld.unbind(); }
		}
	}

	public static boolean hides(final @Nullable Screen screen) {
		return visible() && screen == fallback;
	}

	/** Run only after vanilla finishes its real network/resource/HUD teardown. No timeout is armed. */
	public static void finish(final boolean completed) {
		if (!completed) {
			NoLoadingScreen.onDisconnected();
			return;
		}
		if (!visible()) {
			clear(); // cosmetic construction failed: leave the original DisconnectedScreen intact
			return;
		}
		ChatComponent chat = ClientUi.chat(Minecraft.getInstance());
		if (chatState != null) chat.restoreState(chatState);
		chatState = null;
		// ChatListener requires a live player. This vanilla local-message API does not, and it
		// retains rich component styling, explicit newlines and the normal chat-width wrapping.
		ClientUi.localMessage(chat, message(((DisconnectedScreenAccessor) fallback).nls$details().reason()));
	}

	public static Component message(final Component reason) {
		// A neutral parent prevents the red prefix leaking into the server's real colours:
		// equivalent to "§cUnable to connect to server: §r", without flattening the server text.
		return Component.empty()
			.append(Component.translatable("noloadingscreen.message.disconnected").withStyle(ChatFormatting.RED))
			.append(reason.copy());
	}

	public static @Nullable Screen fallbackScreen() {
		return fallback;
	}

	/** Esc opens our usual local menu; its Disconnect button is the only normal way out. */
	public static void leave() {
		if (fallback == null) return;
		Screen parent = ((DisconnectedScreenAccessor) fallback).nls$parent();
		NoLoadingScreen.onDisconnected();
		ClientUi.setScreen(Minecraft.getInstance(), parent);
	}

	public static void clear() {
		fallback = null;
		outgoing = null;
		chatState = null;
	}
}
