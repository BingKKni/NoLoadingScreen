package io.github.bingkkni.noloadingscreen.mixin;

import io.github.bingkkni.noloadingscreen.RetainedLightQueue;
import java.util.Deque;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin implements RetainedLightQueue {
	@Shadow @Final private Deque<Runnable> lightUpdateQueue;
	@Unique private boolean nls$retiredLightQueue;

	@Override
	public void nls$retireLightQueue() {
		// These deferred packet tasks capture the retired play listener, whose level is cleared.
		// Keep existing light-engine data and meshes; only discard work for the departed session.
		this.nls$retiredLightQueue = true;
		this.lightUpdateQueue.clear();
	}

	@Inject(method = "queueLightUpdate", at = @At("HEAD"), cancellable = true)
	private void nls$discardRetiredLightTask(final Runnable task, final CallbackInfo ci) {
		if (this.nls$retiredLightQueue) ci.cancel();
	}
}
