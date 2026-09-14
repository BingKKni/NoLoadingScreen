package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.bingkkni.noloadingscreen.LoadingWork;
import io.github.bingkkni.noloadingscreen.compat.PendingChunkBuild;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Optional, version-checked Sodium path; completed results still use Sodium's own queue. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public abstract class SodiumRenderSectionManagerMixin {
	@Unique private final List<PendingChunkBuild> nls$deferredCollectors = new ArrayList<>();

	@WrapMethod(method = "destroy")
	private void nls$measureRendererTeardown(final Operation<Void> original) {
		long timing = LoadingWork.startTiming();
		try { original.call(); } finally { LoadingWork.endTiming("Sodium renderer teardown", timing); }
	}

	@WrapOperation(method = "updateChunks", at = @At(value = "INVOKE",
		target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/executor/ChunkJobCollector;awaitCompletion(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/executor/ChunkBuilder;)V"))
	private void nls$leaveLoadingBuildsOnWorkers(@Coerce final Object collector, @Coerce final Object builder,
		final Operation<Void> original, @Local(argsOnly = true) final boolean updateImmediately) {
		this.nls$deferredCollectors.removeIf(batch -> !batch.nls$pending());
		if (updateImmediately || !LoadingWork.active()) {
			// A later full-frame capture must also wait for batches deferred by earlier frames.
			var iterator = this.nls$deferredCollectors.iterator();
			while (iterator.hasNext()) {
				original.call(iterator.next(), builder);
				iterator.remove();
			}
			original.call(collector, builder);
		} else {
			PendingChunkBuild batch = (PendingChunkBuild) collector;
			if (batch.nls$pending() && !this.nls$deferredCollectors.contains(batch)) this.nls$deferredCollectors.add(batch);
		}
	}
}
