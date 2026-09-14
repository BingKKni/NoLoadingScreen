package io.github.bingkkni.noloadingscreen.verification;

import io.github.bingkkni.noloadingscreen.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sun.misc.Unsafe;

/** Default-check guard regression; full mapped disconnect/event/wait execution is in GPU smoke. */
public final class DisconnectHandoffPolicyVerification {
	public static void run() throws Exception {
		Minecraft client = Minecraft.getInstance();
		Field access = Unsafe.class.getDeclaredField("theUnsafe"); access.setAccessible(true);
		Unsafe unsafe = (Unsafe) access.get(null);
		ClientLevel outgoing = (ClientLevel) unsafe.allocateInstance(ClientLevel.class);
		LocalPlayer player = (LocalPlayer) unsafe.allocateInstance(LocalPlayer.class);
		Object previousLoading = field(PlaceholderRegistries.class, "loading").get(null);
		int previousFailures = field(PlaceholderWorld.class, "renderFailures").getInt(null);
		// Refuse construction, not the handoff: fallback must work without weakening adopt's guard.
		field(PlaceholderRegistries.class, "loading").set(null, CompletableFuture.completedFuture(null));
		field(PlaceholderWorld.class, "renderFailures").setInt(null, 3);
		try {
			for (boolean enabled : new boolean[]{false, true}) {
				NoLoadingScreenConfig.get().enabled = enabled;
				client.level = outgoing; client.player = player;
				invoke(client, "nls$showSavingWorld");
				check(client.level == (enabled ? null : outgoing) && client.player == (enabled ? null : player), "Saving host must relinquish LIVE fields only when enabled");
				check(!SavingWorldView.visible() && !LoadingWaitLoop.active(), "Failed saving adoption starts no clock");
				var fallback = new DisconnectedScreen(new TitleScreen(), Component.literal("kick"), Component.literal("reason"));
				field(DisconnectedWorldView.class, "fallback").set(null, fallback);
				client.level = outgoing; client.player = player;
				invoke(client, "nls$savingFinished");
				check(client.level == (enabled ? null : outgoing) && client.player == (enabled ? null : player), "Kick host must relinquish LIVE fields only when enabled");
				check(!PlaceholderWorld.active() && DisconnectedWorldView.fallbackScreen() == fallback, "Refused adoption preserves vanilla fallback ownership");
				DisconnectedWorldView.finish(true);
				check(!DisconnectedWorldView.active(), "Unsuccessful kick installation clears retention state");
			}
			client.level = outgoing; client.player = player;
			invoke(client, "nls$savingFinished");
			check(client.level == outgoing && client.player == player, "Explicit non-KickWarn exit keeps vanilla pointer ordering");
		} finally {
			client.level = null; client.player = null;
			NoLoadingScreenConfig.get().enabled = true;
			DisconnectedWorldView.clear(); SavingWorldView.finish();
			field(PlaceholderRegistries.class, "loading").set(null, previousLoading);
			field(PlaceholderWorld.class, "renderFailures").setInt(null, previousFailures);
		}
		System.out.println("DisconnectHandoffPolicyVerification: 11 live-pointer disabled/refusal/fallback assertions passed");
	}
	private static void invoke(Minecraft client, String name) throws Exception {
		Method hook = Arrays.stream(Minecraft.class.getDeclaredMethods()).filter(m -> !m.isSynthetic() && m.getName().contains(name)).findFirst().orElseThrow();
		hook.setAccessible(true);
		Object[] args = new Object[hook.getParameterCount()];
		args[0] = new TitleScreen();
		Arrays.fill(args, 1, args.length - 1, false);
		args[args.length - 1] = new CallbackInfo("disconnect", false);
		hook.invoke(client, args);
	}
	private static Field field(Class<?> type, String name) throws Exception { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
	private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
