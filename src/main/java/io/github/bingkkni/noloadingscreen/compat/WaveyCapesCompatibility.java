package io.github.bingkkni.noloadingscreen.compat;

import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.platform.LoaderServices;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Avatar;

/** Optional public CapeHolder API. Never ticks a player or disables WaveyCapes' renderer/physics. */
public final class WaveyCapesCompatibility {
	private static boolean resolved, failed;
	private static Class<?> holder;
	private static Constructor<?> delegate, vector;
	private static Method get, set, create, incorrect, init, simulate, applyMovement;

	public static boolean clockHookSupported() {
		return LoaderServices.modVersion("waveycapes").filter("1.10.2"::equals).isPresent();
	}

	public static float partialTick(net.minecraft.client.renderer.entity.state.AvatarRenderState state, float original) {
		return io.github.bingkkni.noloadingscreen.PlaceholderWorld.avatarPartialTick(state.id, original);
	}
	private WaveyCapesCompatibility() {}

	private static boolean available(LocalPlayer player) {
		if (!resolved) {
			resolved = true;
			if (LoaderServices.modVersion("waveycapes").isEmpty()) return false;
			try {
				holder = Class.forName("dev.tr7zw.waveycapes.versionless.CapeHolder");
				Class<?> simulation = Class.forName("dev.tr7zw.waveycapes.versionless.sim.BasicSimulation");
				Class<?> proxy = Class.forName("dev.tr7zw.waveycapes.versionless.nms.MinecraftPlayer");
				delegate = Class.forName("dev.tr7zw.waveycapes.delegate.PlayerDelegate").getConstructor(Avatar.class);
				get = holder.getMethod("getSimulation");
				set = holder.getMethod("setSimulation", simulation);
				create = holder.getMethod("createSimulation");
				incorrect = holder.getMethod("incorrectSimulation", simulation);
				init = simulation.getMethod("init", int.class);
				Class<?> vectorType = Class.forName("dev.tr7zw.waveycapes.versionless.util.Vector3");
				vector = vectorType.getConstructor(float.class, float.class, float.class);
				applyMovement = simulation.getMethod("applyMovement", vectorType);
				simulate = holder.getMethod("simulate", proxy);
			} catch (ReflectiveOperationException | LinkageError error) { fail(error); }
		}
		return !failed && holder != null && holder.isInstance(player);
	}

	public static void tick(LocalPlayer player) {
		if (!available(player)) return;
		try {
			Object simulation = get.invoke(player);
			if (simulation == null || (boolean) incorrect.invoke(player, simulation)) {
				simulation = create.invoke(player);
				set.invoke(player, simulation);
			}
			if (simulation == null) return; // WaveyCapes' vanilla/off mode is intentional.
			boolean fresh = (boolean) init.invoke(simulation, 16);
			Object proxy = delegate.newInstance(player);
			// Initialize through the public simulation API rather than updateSimulation/setDirty:
			// a dirty flag would settle/reset the cape again on the first real Player.tick.
			if (fresh) {
				applyMovement.invoke(simulation, vector.newInstance(1.0F, 1.0F, 0.0F));
				for (int i = 0; i < 5; i++) simulate.invoke(player, proxy);
			}
			simulate.invoke(player, proxy);
		} catch (ReflectiveOperationException | LinkageError error) { fail(error); }
	}

	public static Object capture(LocalPlayer player) {
		if (!available(player)) return null;
		try { return get.invoke(player); }
		catch (ReflectiveOperationException error) { fail(error); return null; }
	}

	public static void restore(LocalPlayer player, Object simulation) {
		if (simulation == null || !available(player)) return;
		try { set.invoke(player, simulation); }
		catch (ReflectiveOperationException error) { fail(error); }
	}

	private static void fail(Throwable error) {
		failed = true;
		NoLoadingScreen.LOGGER.warn("Could not bridge the installed WaveyCapes API; its normal gameplay behavior is unchanged.", error);
	}
}
