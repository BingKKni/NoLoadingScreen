package io.github.bingkkni.noloadingscreen.verification;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.Method;
import java.util.Arrays;
import net.fabricmc.loader.api.FabricLoader;

/** Transforms the actual input targets with a supplied, unmodified ViaFabricPlus jar. */
final class InputCompatibilityVerification {
	static void run() throws ReflectiveOperationException {
		var via = FabricLoader.getInstance().getModContainer("viafabricplus").orElseThrow(
			() -> new AssertionError("The compatibility run must load the real ViaFabricPlus mod"));
		for (String name : new String[]{"MouseHandler", "KeyboardHandler"}) {
			Class<?> target = Class.forName("net.minecraft.client." + name, false, InputCompatibilityVerification.class.getClassLoader());
			Method[] methods = target.getDeclaredMethods();
			check(Arrays.stream(methods).anyMatch(m -> m.getName().contains("storeEvent")),
				name + ": ViaFabricPlus's input Redirect must still be applied");
			check(Arrays.stream(methods).anyMatch(m -> m.getName().equals("viaFabricPlus$getPendingScreenEvents")),
				name + ": ViaFabricPlus must retain its event queue");
			check(Arrays.stream(methods).anyMatch(m -> m.getName().contains("nls$dispatchWaiting")
				&& m.getParameterCount() == 3 && m.getParameterTypes()[2] == Operation.class),
				name + ": NoLoadingScreen must use a chainable operation rather than another Redirect");
			System.out.println("Verified actual ViaFabricPlus + NoLoadingScreen input target: " + target.getName());
		}
		Class<?> particles = Class.forName("net.minecraft.client.particle.ParticleEngine", false, InputCompatibilityVerification.class.getClassLoader());
		for (String hook : new String[]{"nls$captureLocalDebris", "nls$extractLocalDebris", "nls$clearLocalDebris"}) {
			check(Arrays.stream(particles.getDeclaredMethods()).anyMatch(m -> m.getName().contains(hook)),
				"ParticleEngine: local break feedback must coexist with ViaFabricPlus (" + hook + ")");
		}
		System.out.println("Verified actual ViaFabricPlus + NoLoadingScreen particle target: " + particles.getName());
		for (var target : java.util.Map.of(
			"net.minecraft.client.Minecraft", "nls$keepDisconnectedEngines",
			"net.minecraft.client.gui.Gui", "nls$returnToPlaceholder",
			"net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl", "nls$retainFailedLogin",
			"net.minecraft.client.renderer.extract.LevelExtractor", "nls$showLocalPlayerWithoutTerrain",
			"net.minecraft.client.gui.screens.DisconnectedScreen", "nls$details",
			"net.minecraft.client.player.AbstractClientPlayer", "nls$offlineSurvivalMode",
			"net.minecraft.world.entity.LivingEntity", "nls$updateInvisibilityStatus").entrySet()) {
			Class<?> type = Class.forName(target.getKey(), false, InputCompatibilityVerification.class.getClassLoader());
			check(Arrays.stream(type.getDeclaredMethods()).anyMatch(m -> m.getName().contains(target.getValue())),
				"Loading/KickWarn hook must coexist with ViaFabricPlus: " + target.getKey());
			System.out.println("Verified actual ViaFabricPlus + NoLoadingScreen lifecycle target: " + target.getKey());
		}
		System.out.println("Input/particle/loading/KickWarn compatibility verification passed with ViaFabricPlus " + via.getMetadata().getVersion()
			+ "; no game window or server was started.");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) throw new AssertionError(message);
	}
}
