package io.github.bingkkni.noloadingscreen.mixin;

import io.github.bingkkni.noloadingscreen.compat.SodiumShaderWarmup;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** RRLS can announce game-load completion before shaders exist. Use actual reload completion. */
@Mixin(ReloadableResourceManager.class)
public abstract class ResourceReloadMixin {
	@Unique private long nls$reloadGeneration;

	@Inject(method = "createReload", at = @At("RETURN"))
	private void nls$prepareAfterResources(CallbackInfoReturnable<ReloadInstance> cir) {
		long generation = ++nls$reloadGeneration;
		// No join/wait and no work on failed or superseded reloads. Pipeline creation is GPU work
		// and must run on the client thread after all reload listeners have applied their data.
		cir.getReturnValue().done().thenRunAsync(() -> {
			if (generation == nls$reloadGeneration) SodiumShaderWarmup.prepare();
		}, Minecraft.getInstance());
	}
}
