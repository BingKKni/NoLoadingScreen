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
import net.minecraft.client.multiplayer.ClientPacketListener;
import sun.misc.Unsafe;


/** Shared execution against the actual optional Sodium collectors and transformed queue policy. */
final class SodiumQueueVerification {
	static void run() throws ReflectiveOperationException {
		Class<?> manager = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager");
		Method condition = Arrays.stream(manager.getDeclaredMethods())
			.filter(m -> m.getName().contains("nls$leaveLoadingBuildsOnWorkers") && m.getParameterCount() == 4
				&& m.getParameterTypes()[2] == Operation.class && m.getParameterTypes()[3] == boolean.class).findFirst().orElseThrow();
		condition.setAccessible(true);
		Object instance = unsafe().allocateInstance(manager);
		Field deferred = manager.getDeclaredField("nls$deferredCollectors");
		deferred.setAccessible(true);
		List<PendingChunkBuild> retained = new ArrayList<>();
		deferred.set(instance, retained);
		List<Object> waited = new ArrayList<>();
		Operation<Void> await = args -> { waited.add(args[0]); complete((PendingChunkBuild) args[0]); return null; };
		PendingChunkBuild normal = batch();
		LoadingWork.reset();
		condition.invoke(instance, normal, null, await, false);
		check(waited.equals(List.of(normal)), "Normal Sodium gameplay retains its completion policy");
		waited.clear();
		LoadingWork.worldArriving();
		PendingChunkBuild loading = batch();
		condition.invoke(instance, loading, null, await, false);
		check(waited.isEmpty() && retained.equals(List.of(loading)), "Loading retains pending collectors without waiting");
		PendingChunkBuild fullFrame = batch();
		condition.invoke(instance, fullFrame, null, await, true);
		check(waited.equals(List.of(loading, fullFrame)) && retained.isEmpty(),
			"Switching to Flawless Frames waits BOTH outstanding old batches and new work");
		waited.clear();
		PendingChunkBuild completed = batch();
		condition.invoke(instance, completed, null, await, false);
		complete(completed);
		PendingChunkBuild unfinished = batch();
		condition.invoke(instance, unfinished, null, await, false);
		check(retained.equals(List.of(unfinished)), "Worker-completed batches are released without accumulating across frames");
		LoadingWork.reset();
		PendingChunkBuild resumed = batch();
		condition.invoke(instance, resumed, null, await, false);
		check(waited.equals(List.of(unfinished, resumed)) && retained.isEmpty(), "Normal gameplay drains any still-pending loading batches");
		verifyCollectorReadiness();
	}
	private static PendingChunkBuild batch() throws ReflectiveOperationException {
		Class<?> type = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobCollector");
		PendingChunkBuild collector = (PendingChunkBuild) type.getConstructor(java.util.function.Consumer.class)
			.newInstance((java.util.function.Consumer<Object>) result -> {});
		Field submitted = type.getDeclaredField("submitted");
		submitted.setAccessible(true);
		submitted.set(collector, new ArrayList<>(List.of(new Object())));
		return collector;
	}

	private static void complete(final PendingChunkBuild batch) {
		try {
			Field semaphore = batch.getClass().getDeclaredField("semaphore");
			semaphore.setAccessible(true);
			((java.util.concurrent.Semaphore) semaphore.get(batch)).release();
		} catch (ReflectiveOperationException e) { throw new AssertionError(e); }
	}

	private static void verifyCollectorReadiness() throws ReflectiveOperationException {
		Class<?> type = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobCollector");
		PendingChunkBuild collector = (PendingChunkBuild) type.getConstructor(java.util.function.Consumer.class)
			.newInstance((java.util.function.Consumer<Object>) result -> {});
		check(!collector.nls$pending(), "Actual empty Sodium collector is already complete");
		Field submitted = type.getDeclaredField("submitted");
		Field semaphore = type.getDeclaredField("semaphore");
		submitted.setAccessible(true);
		semaphore.setAccessible(true);
		submitted.set(collector, new ArrayList<>(List.of(new Object())));
		check(collector.nls$pending(), "Actual collector detects work without blocking");
		((java.util.concurrent.Semaphore) semaphore.get(collector)).release();
		check(!collector.nls$pending(), "Worker completion is observed without consuming completion permits");
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
