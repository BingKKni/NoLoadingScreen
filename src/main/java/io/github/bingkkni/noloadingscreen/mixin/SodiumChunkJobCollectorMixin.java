package io.github.bingkkni.noloadingscreen.mixin;

import io.github.bingkkni.noloadingscreen.compat.PendingChunkBuild;
import java.util.List;
import java.util.concurrent.Semaphore;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobCollector", remap = false)
public abstract class SodiumChunkJobCollectorMixin implements PendingChunkBuild {
	@Shadow @Final private Semaphore semaphore;
	@Shadow @Final private List<?> submitted;

	@Override
	public boolean nls$pending() {
		return this.semaphore.availablePermits() < this.submitted.size();
	}
}
