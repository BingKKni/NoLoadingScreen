package io.github.bingkkni.noloadingscreen.verification;

import io.github.bingkkni.noloadingscreen.DisconnectedWorldView;
import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import io.github.bingkkni.noloadingscreen.RetainedLightQueue;
import io.github.bingkkni.noloadingscreen.SavingWorldView;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.world.level.ChunkPos;
import sun.misc.Unsafe;

/** Runs vanilla's transformed queue and a real listener-capturing light-removal task, without GL. */
public final class RetainedLightQueueVerification {
	private static final Unsafe UNSAFE = unsafe();
	private static int assertions;

	private RetainedLightQueueVerification() {}

	public static void run() throws Exception {
		assertions = 0;
		Class<?>[] owners = {NoLoadingScreen.class, SavingWorldView.class, DisconnectedWorldView.class};
		Object[] snapshots = new Object[owners.length];
		for (int i = 0; i < owners.length; i++) {
			snapshots[i] = get(owners[i], null, "outgoing");
			set(owners[i], null, "outgoing", null);
		}
		Minecraft previousClient = Minecraft.getInstance();
		boolean installed = (boolean) get(PlaceholderWorld.class, null, "installed");
		boolean synthetic = (boolean) get(PlaceholderWorld.class, null, "synthetic");
		Object previousLevel = get(PlaceholderWorld.class, null, "level");
		int failures = (int) get(PlaceholderWorld.class, null, "renderFailures");
		try {
			Minecraft client = allocate(Minecraft.class);
			set(Minecraft.class, null, "instance", client);
			set(PlaceholderWorld.class, null, "installed", false);
			set(PlaceholderWorld.class, null, "renderFailures", 0);
			verifyVanillaFailure();
			verifyNormalQueue(level(), "normal world");
			ClientLevel syntheticLevel = level();
			set(PlaceholderWorld.class, null, "installed", true);
			set(PlaceholderWorld.class, null, "synthetic", true);
			set(PlaceholderWorld.class, null, "level", syntheticLevel);
			verifyNormalQueue(syntheticLevel, "active synthetic world");
			set(PlaceholderWorld.class, null, "installed", false);

			// Six distinct handoffs per path exceed the session's three-render-failure cutoff.
			for (Class<?> owner : owners) {
				for (int handoff = 0; handoff < 6; handoff++) {
					ClientLevel oldLevel = level();
					ClientPacketListener listener = listener(oldLevel);
					queueVanillaTask(listener);
					set(owner, null, "outgoing", snapshot(oldLevel));
					check(queue(oldLevel).size() == 1, "Capture must not discard live work");
					client.level = oldLevel;
					check(!PlaceholderWorld.adopt(oldLevel, null, null), "Live-world adoption must be refused");
					check(queue(oldLevel).size() == 1, "Refused adoption preserves queued work");
					verifyNormalQueue(level(), "unrelated world while capture pending");
					ClientLevel unrelated = level();
					ClientPacketListener unrelatedListener = listener(unrelated);
					unrelated.queueLightUpdate(() -> {});
					unrelatedListener.clearLevel();
					check(queue(unrelated).size() == 1, "Other listener teardown must not retire this queue");

					listener.clearLevel(); // actual transformed vanilla teardown, BEFORE adopt/bind
					check(get(ClientPacketListener.class, listener, "level") == null, "Listener stays cleared");
					check(queue(oldLevel).isEmpty(), "Retirement immediately releases pending captures");
					oldLevel.pollLightUpdates(); // used to throw from the captured listener
					oldLevel.queueLightUpdate(() -> { throw new AssertionError("late retired task ran"); });
					check(queue(oldLevel).isEmpty(), "Late tasks are rejected, not accumulated");
					set(owner, null, "outgoing", null);
					client.level = null;
					((RetainedLightQueue) oldLevel).nls$retireLightQueue(); // idempotent adopt boundary
					oldLevel.queueLightUpdate(() -> { throw new AssertionError("released scene task ran"); });
					oldLevel.pollLightUpdates();
					check(queue(oldLevel).isEmpty(), "Retirement outlives pending/active scene ownership");
					verifyNormalQueue(level(), "new world after handoff");
				}
			}
			check((int) get(PlaceholderWorld.class, null, "renderFailures") == 0, "No render failure budget consumed");
			System.out.println("RetainedLightQueueVerification: " + assertions
				+ " assertions passed; vanilla NPE control, 18 handoffs, pending/late tasks, identity and normal/synthetic/new queues");
		} finally {
			for (int i = 0; i < owners.length; i++) set(owners[i], null, "outgoing", snapshots[i]);
			set(Minecraft.class, null, "instance", previousClient);
			set(PlaceholderWorld.class, null, "installed", installed);
			set(PlaceholderWorld.class, null, "synthetic", synthetic);
			set(PlaceholderWorld.class, null, "level", previousLevel);
			set(PlaceholderWorld.class, null, "renderFailures", failures);
		}
	}

	private static void verifyVanillaFailure() throws Exception {
		ClientLevel level = level();
		ClientPacketListener listener = listener(level);
		queueVanillaTask(listener);
		listener.clearLevel(); // not captured: vanilla semantics, including exceptions, remain intact
		try {
			level.pollLightUpdates();
			throw new AssertionError("Unretained vanilla task should dereference the cleared listener");
		} catch (NullPointerException expected) {
			check(java.util.Arrays.stream(expected.getStackTrace()).anyMatch(frame ->
				frame.getClassName().equals(ClientPacketListener.class.getName())), "NPE must originate in vanilla listener task");
		}
	}

	private static void verifyNormalQueue(ClientLevel level, String label) throws Exception {
		List<Integer> ran = new ArrayList<>();
		for (int i = 0; i < 25; i++) {
			int value = i;
			level.queueLightUpdate(() -> ran.add(value));
		}
		level.pollLightUpdates();
		check(ran.equals(java.util.stream.IntStream.range(0, 10).boxed().toList()), label + ": vanilla budget/FIFO");
		while (!queue(level).isEmpty()) level.pollLightUpdates();
		check(ran.equals(java.util.stream.IntStream.range(0, 25).boxed().toList()), label + ": every task runs once");
		RuntimeException sentinel = new RuntimeException("normal light task");
		level.queueLightUpdate(() -> { throw sentinel; });
		try { level.pollLightUpdates(); throw new AssertionError(label + ": swallowed exception"); }
		catch (RuntimeException actual) { check(actual == sentinel, label + ": original exception preserved"); }
	}

	private static ClientLevel level() throws Exception {
		ClientLevel level = allocate(ClientLevel.class);
		set(ClientLevel.class, level, "lightUpdateQueue", new ArrayDeque<Runnable>());
		// Fabric lifecycle hooks enumerate entities during listener.clearLevel(). This fixture has
		// no entities, but still needs the real empty storage; no entity callbacks can run.
		set(ClientLevel.class, level, "entityStorage", new net.minecraft.world.level.entity.TransientEntitySectionManager<>(
			net.minecraft.world.entity.Entity.class, null));
		if (io.github.bingkkni.noloadingscreen.platform.LoaderServices.modVersion("fabric-lifecycle-events-v1").isPresent()) {
			set(net.minecraft.world.level.Level.class, level, "loadedChunks", new java.util.HashSet<>());
		}
		return level;
	}

	private static ClientPacketListener listener(ClientLevel level) throws Exception {
		ClientPacketListener listener = allocate(ClientPacketListener.class);
		set(ClientPacketListener.class, listener, "level", level);
		set(ClientPacketListener.class, listener, "cacheSlots", new ArrayList<>());
		return listener;
	}

	private static void queueVanillaTask(ClientPacketListener listener) throws Exception {
		var method = ClientPacketListener.class.getDeclaredMethod("queueLightRemoval", ClientboundForgetLevelChunkPacket.class);
		method.setAccessible(true);
		method.invoke(listener, new ClientboundForgetLevelChunkPacket(new ChunkPos(0, 0)));
	}

	private static Object snapshot(ClientLevel level) throws Exception {
		var constructor = Class.forName("io.github.bingkkni.noloadingscreen.OutgoingWorld").getDeclaredConstructors()[0];
		constructor.setAccessible(true);
		return constructor.newInstance(level, null, null);
	}

	@SuppressWarnings("unchecked")
	private static Deque<Runnable> queue(ClientLevel level) throws Exception {
		return (Deque<Runnable>) get(ClientLevel.class, level, "lightUpdateQueue");
	}
	private static <T> T allocate(Class<T> type) throws Exception { return type.cast(UNSAFE.allocateInstance(type)); }
	private static Object get(Class<?> type, Object owner, String name) throws Exception {
		Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner);
	}
	private static void set(Class<?> type, Object owner, String name, Object value) throws Exception {
		Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
	}
	private static Unsafe unsafe() {
		try { return (Unsafe) get(Unsafe.class, null, "theUnsafe"); }
		catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
	}
	private static void check(boolean condition, String message) {
		assertions++;
		if (!condition) throw new AssertionError(message);
	}
}
