package io.github.bingkkni.noloadingscreen.verification;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.bingkkni.noloadingscreen.LoadingWork;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

final class SectionRoutingVerification {
	static void run() throws ReflectiveOperationException {
		Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
		unsafeField.setAccessible(true);
		var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
		var renderer = (net.minecraft.client.renderer.LevelRenderer) unsafe.allocateInstance(net.minecraft.client.renderer.LevelRenderer.class);
		var section = (TestSection) unsafe.allocateInstance(TestSection.class);
		Method route = Arrays.stream(renderer.getClass().getDeclaredMethods())
			.filter(m -> m.getName().contains("nls$compileLoadingTerrainAsync")).findFirst().orElseThrow();
		route.setAccessible(true);
		AtomicInteger synchronous = new AtomicInteger();
		Operation<Void> original = args -> { synchronous.incrementAndGet(); return null; };
		LoadingWork.worldArriving();
		route.invoke(renderer, null, section, null, original);
		require(section.asynchronous == 1 && synchronous.get() == 0, "Loading routes synchronous section requests to vanilla's asynchronous builder");
		LoadingWork.reset();
		route.invoke(renderer, null, section, null, original);
		require(section.asynchronous == 1 && synchronous.get() == 1, "Normal gameplay delegates its synchronous operation unchanged");
	}

	private static final class TestSection extends net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection {
		private int asynchronous;
		private TestSection(final net.minecraft.client.renderer.chunk.SectionRenderDispatcher dispatcher) { dispatcher.super(0, 0L); }
		@Override public void rebuildSectionAsync(final net.minecraft.client.renderer.chunk.RenderRegionCache region) { this.asynchronous++; }
	}

	private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
