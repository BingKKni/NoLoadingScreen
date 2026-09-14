package io.github.bingkkni.noloadingscreen.verification;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.bingkkni.noloadingscreen.JoinPhase;
import io.github.bingkkni.noloadingscreen.LoadingWork;
import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.util.thread.BlockableEventLoop;

/** Executes vanilla's transformed queues with deterministic deadline expiry, no sleep/socket/GPU. */
final class LoadingWorkVerification {
	private static int assertions;

	static void run() throws ReflectiveOperationException {
		Minecraft minecraft = Minecraft.getInstance();
		set(Minecraft.class, minecraft, "gameThread", Thread.currentThread());
		NoLoadingScreen.onDisconnected();
		verifyPackets(minecraft);
		verifyTasks(minecraft);
		SectionRoutingVerification.run();
		verifyScopeAndViewDistance(minecraft);
		System.out.println("LoadingWorkVerification: " + assertions + " assertions passed (FIFO frame budgets, error handling, client isolation, managed waits and placeholder distance).");
	}

	private static void verifyPackets(final Minecraft minecraft) throws ReflectiveOperationException {
		PacketProcessor processor = new PacketProcessor(Thread.currentThread());
		set(Minecraft.class, minecraft, "packetProcessor", processor);
		set(NoLoadingScreen.class, null, "phase", JoinPhase.WAITING_WORLD);
		Listener listener = new Listener();
		List<Integer> handled = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			int id = i;
			processor.scheduleIfPossible(listener, new WorkPacket(() -> {
				handled.add(id);
				expire(processor, "nls$batchStart");
			}));
		}
		for (int i = 0; i < 5; i++) {
			processor.processQueuedPackets();
			check(handled.size() == i + 1 && handled.get(i) == i, "Slow packets yield after one complete handler, without loss or reordering");
		}
		processor.processQueuedPackets();
		check(handled.size() == 5, "Draining an empty queue does not repeat packets");

		NoLoadingScreenConfig.get().enabled = false;
		for (int i = 0; i < 3; i++) processor.scheduleIfPossible(listener, new WorkPacket(() -> {
			handled.add(5);
			expire(processor, "nls$batchStart");
		}));
		processor.processQueuedPackets();
		check(handled.size() == 8, "Disabled mod drains the entire vanilla queue");
		NoLoadingScreenConfig.get().enabled = true;
		set(NoLoadingScreen.class, null, "phase", JoinPhase.NONE);
		processor.scheduleIfPossible(listener, new WorkPacket(() -> {
			try { set(NoLoadingScreen.class, null, "phase", JoinPhase.RECEIVING_CHUNKS); }
			catch (ReflectiveOperationException e) { throw new AssertionError(e); }
			expire(processor, "nls$batchStart");
		}));
		processor.scheduleIfPossible(listener, new WorkPacket(() -> handled.add(6)));
		processor.processQueuedPackets();
		check(handled.size() == 8, "A login starting inside the drain activates the budget before the following packet");
		processor.processQueuedPackets();
		check(handled.size() == 9, "Deferred post-login packet runs on the next frame");

		processor.scheduleIfPossible(listener, new WorkPacket(() -> { throw new IllegalStateException("packet test"); }));
		processor.processQueuedPackets();
		check(listener.errors == 1, "Vanilla listener error handling is retained");
		listener.accepting = false;
		processor.scheduleIfPossible(listener, new WorkPacket(() -> { throw new AssertionError("Disconnected packet executed"); }));
		processor.processQueuedPackets();
		listener.accepting = true;
		PacketProcessor server = new PacketProcessor(Thread.currentThread());
		AtomicInteger serverPackets = new AtomicInteger();
		for (int i = 0; i < 3; i++) server.scheduleIfPossible(listener, new WorkPacket(() -> {
			serverPackets.incrementAndGet();
			expire(server, "nls$batchStart");
		}));
		server.processQueuedPackets();
		check(serverPackets.get() == 3, "Other processors, including integrated-server processors, are not budgeted");
		processor.close();
		processor.processQueuedPackets();
		NoLoadingScreen.onDisconnected();
	}

	private static void verifyTasks(final Minecraft minecraft) throws ReflectiveOperationException {
		set(BlockableEventLoop.class, minecraft, "pendingRunnables", new java.util.concurrent.ConcurrentLinkedQueue<Runnable>());
		Method drain = Arrays.stream(Minecraft.class.getDeclaredMethods()).filter(m -> m.getName().contains("nls$measureClientTasks")).findFirst().orElseThrow();
		drain.setAccessible(true);
		Method runAll = BlockableEventLoop.class.getDeclaredMethod("runAllTasks");
		runAll.setAccessible(true);
		Operation<Void> original = args -> {
			try { runAll.invoke(minecraft); }
			catch (ReflectiveOperationException e) { throw new AssertionError(e); }
			return null;
		};
		set(NoLoadingScreen.class, null, "phase", JoinPhase.RECEIVING_CHUNKS);
		AtomicInteger completed = new AtomicInteger();
		for (int i = 0; i < 3; i++) minecraft.schedule(() -> {
			completed.incrementAndGet();
			expire(minecraft, "nls$taskStart");
		});
		for (int i = 0; i < 3; i++) {
			drain.invoke(minecraft, minecraft, original);
			check(completed.get() == i + 1, "Ordinary loading tasks are split across frames");
		}
		// Synchronous completion waits must keep pumping even within a budgeted outer task.
		minecraft.schedule(() -> {
			expire(minecraft, "nls$taskStart");
			minecraft.schedule(completed::incrementAndGet);
			minecraft.managedBlock(() -> completed.get() == 4);
		});
		drain.invoke(minecraft, minecraft, original);
		check(completed.get() == 4, "Nested managedBlock bypasses frame budgeting and completes");
		minecraft.schedule(completed::incrementAndGet);
		runAll.invoke(minecraft);
		check(completed.get() == 5, "Unscoped resource/save drains retain original behavior");
		try {
			drain.invoke(minecraft, minecraft, (Operation<Void>) args -> { throw new IllegalStateException("task failure"); });
			throw new AssertionError("Expected task error");
		} catch (java.lang.reflect.InvocationTargetException expected) {
			check(expected.getCause() instanceof IllegalStateException, "Task errors propagate unchanged");
		}
		Field scoped = Minecraft.class.getDeclaredField("nls$drainingTasks");
		scoped.setAccessible(true);
		check(!scoped.getBoolean(minecraft), "Task budgeting scope is cleared even on exceptions");
		NoLoadingScreen.onDisconnected();
	}


	private static void verifyScopeAndViewDistance(final Minecraft minecraft) throws ReflectiveOperationException {
		check(!LoadingWork.active(), "Idle client is not a loading workload");
		LoadingWork.worldArriving();
		check(LoadingWork.active(), "Budget remains active after the early ready gate opens");
		set(LoadingWork.class, null, "settleUntil", System.nanoTime() - 1);
		check(!LoadingWork.active(), "Post-join budget expires without affecting normal gameplay");
		LoadingWork.worldArriving();
		NoLoadingScreen.onDisconnected();
		check(!LoadingWork.active(), "Disconnect clears post-join budgeting");

		Options options = minecraft.options;
		set(Options.class, options, "renderDistance", new OptionInstance<Integer>("test", OptionInstance.noTooltip(),
			(caption, value) -> caption, new OptionInstance.IntRange(2, 32), 16, value -> {}));
		options.setServerRenderDistance(32);
		check(options.getEffectiveRenderDistance() == 16, "Normal render distance unchanged");
		set(PlaceholderWorld.class, null, "installed", true);
		set(PlaceholderWorld.class, null, "synthetic", true);
		check(options.getEffectiveRenderDistance() == 2 && options.renderDistance().get() == 16,
			"Synthetic view limits allocations without changing the saved preference");
		NoLoadingScreenConfig.get().enabled = false;
		check(options.getEffectiveRenderDistance() == 16, "Disabled mod does not constrain the renderer");
		NoLoadingScreenConfig.get().enabled = true;
		set(PlaceholderWorld.class, null, "synthetic", false);
		check(options.getEffectiveRenderDistance() == 16, "Adopted worlds retain their real render distance");
		set(PlaceholderWorld.class, null, "installed", false);
	}

	private static void expire(final Object target, final String name) {
		try { set(target instanceof Minecraft ? Minecraft.class : PacketProcessor.class, target, name, System.nanoTime() - 1_000_000_000L); }
		catch (ReflectiveOperationException e) { throw new AssertionError(e); }
	}

	private static void set(final Class<?> type, final Object instance, final String name, final Object value) throws ReflectiveOperationException {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		field.set(instance, value);
	}

	private static void check(final boolean condition, final String message) {
		assertions++;
		if (!condition) throw new AssertionError(message);
	}

	private record WorkPacket(Runnable action) implements Packet<Listener> {
		@Override public PacketType<? extends Packet<Listener>> type() { return null; }
		@Override public void handle(final Listener listener) { this.action.run(); }
	}

	private static final class Listener implements PacketListener {
		private boolean accepting = true;
		private int errors;
		@Override public PacketFlow flow() { return PacketFlow.CLIENTBOUND; }
		@Override public ConnectionProtocol protocol() { return ConnectionProtocol.PLAY; }
		@Override public void onDisconnect(final DisconnectionDetails details) {}
		@Override public boolean isAcceptingMessages() { return this.accepting; }
		@Override public void onPacketError(final Packet packet, final Exception failure) { this.errors++; }
	}
}
