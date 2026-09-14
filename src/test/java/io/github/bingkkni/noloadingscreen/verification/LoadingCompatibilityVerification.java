package io.github.bingkkni.noloadingscreen.verification;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.bingkkni.noloadingscreen.LoadingWork;
import io.github.bingkkni.noloadingscreen.compat.PendingChunkBuild;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ClientPacketListener;
import sun.misc.Unsafe;

/** Optional checks against the user's unmodified renderer/networking jars, without a GPU. */
final class LoadingCompatibilityVerification {
	static void run() throws ReflectiveOperationException {
		SodiumQueueVerification.run();
		Method transfer = Arrays.stream(ClientPacketListener.class.getDeclaredMethods())
			.filter(m -> m.getName().contains("nls$configurationStarted")).findFirst().orElseThrow();
		check(transfer.getParameterTypes()[1] == Operation.class, "Adoption wraps the whole handler, after ViaFabricPlus RETURN hooks");
		check(Arrays.stream(ClientPacketListener.class.getDeclaredMethods()).anyMatch(m -> m.getName().contains("enableAutoRead")),
			"The actual ViaFabricPlus live-channel tail is present");
		verifyAddonGuard();
		SodiumColdStartVerification.run();
		System.out.println("Loading compatibility checks passed with Sodium "
			+ FabricLoader.getInstance().getModContainer("sodium").orElseThrow().getMetadata().getVersion()
			+ "; worker-wait policy and Fabric addon ownership verified without a game window.");
	}


	private static void verifyAddonGuard() throws ReflectiveOperationException {
		Class<?> networking = Class.forName("net.fabricmc.fabric.impl.networking.client.ClientNetworkingImpl");
		Field slot = Arrays.stream(networking.getDeclaredFields()).filter(f -> Modifier.isStatic(f.getModifiers())
			&& f.getType().getSimpleName().equals("ClientPlayNetworkAddon")).findFirst().orElseThrow();
		slot.setAccessible(true);
		Field configSlot = Arrays.stream(networking.getDeclaredFields()).filter(f -> Modifier.isStatic(f.getModifiers())
			&& f.getType().getSimpleName().equals("ClientConfigurationNetworkAddon")).findFirst().orElseThrow();
		configSlot.setAccessible(true);
		Object savedConfig = configSlot.get(null);
		Object saved = slot.get(null);
		Object owner = unsafe().allocateInstance(slot.getType());
		Class<?> guard = Class.forName("io.github.bingkkni.noloadingscreen.PlayAddonGuard");
		Method acquire = guard.getDeclaredMethod("acquire");
		Method close = guard.getDeclaredMethod("close");
		acquire.setAccessible(true);
		close.setAccessible(true);
		try {
			configSlot.set(null, null);
			slot.set(null, owner);
			check(acquire.invoke(null) == null && slot.get(null) == owner, "Occupied real Fabric addon slot is refused and left intact");
			slot.set(null, null);
			Object acquired = acquire.invoke(null);
			check(acquired != null, "Unowned Fabric addon slot can be protected");
			slot.set(null, owner);
			close.invoke(acquired);
			check(slot.get(null) == null, "Synthetic constructor side effects are cleaned up");
			Object configOwner = unsafe().allocateInstance(configSlot.getType());
			configSlot.set(null, configOwner);
			check(acquire.invoke(null) == null && configSlot.get(null) == configOwner && slot.get(null) == null,
				"Occupied configuration addon also refuses PLAY construction without changing either slot");
		} finally {
			slot.set(null, saved);
			configSlot.set(null, savedConfig);
		}
	}

	private static Unsafe unsafe() throws ReflectiveOperationException {
		Field field = Unsafe.class.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		return (Unsafe) field.get(null);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) throw new AssertionError(message);
	}
}
