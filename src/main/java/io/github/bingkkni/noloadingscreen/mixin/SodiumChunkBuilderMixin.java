package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder", remap = false)
public abstract class SodiumChunkBuilderMixin {
	@ModifyReturnValue(method = "getThreadCount", at = @At("RETURN"))
	private static int nls$smallVoidWorkerPool(final int original) {
		// The empty synthetic world has no terrain jobs. Real/adopted worlds keep Sodium's
		// configured worker count; no settings are written and no worker is moved to the main thread.
		return NoLoadingScreenConfig.get().enabled && PlaceholderWorld.syntheticActive() ? 1 : original;
	}
}
