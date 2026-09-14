package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.bingkkni.noloadingscreen.LoadingWork;
import java.util.Queue;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PacketProcessor.class)
public abstract class PacketProcessorMixin {
	@Unique private boolean nls$clientDrain;
	@Unique private long nls$batchStart;
	@Unique private int nls$processed;

	@WrapMethod(method = "processQueuedPackets")
	private void nls$loadingPacketBudget(final Operation<Void> original) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.packetProcessor() != (Object) this || !minecraft.isSameThread() || this.nls$clientDrain) {
			original.call();
			return;
		}
		this.nls$clientDrain = true;
		this.nls$batchStart = System.nanoTime();
		this.nls$processed = 0;
		long timing = LoadingWork.startTiming();
		try {
			original.call();
		} finally {
			this.nls$clientDrain = false;
			LoadingWork.endTiming("client packet drain", timing);
		}
	}

	@WrapOperation(method = "processQueuedPackets", at = @At(value = "INVOKE", target = "Ljava/util/Queue;isEmpty()Z"))
	private boolean nls$yieldBetweenPackets(final Queue<?> queue, final Operation<Boolean> original) {
		if (original.call(queue)) return true;
		if (!this.nls$clientDrain) return false;
		// Stop BEFORE polling the next packet. Vanilla retains the queue, order, error handling
		// and thread ownership; at least one complete handler runs even after a long JVM pause.
		if (this.nls$processed > 0 && LoadingWork.active() && System.nanoTime() - this.nls$batchStart >= LoadingWork.PACKET_BUDGET_NS) return true;
		this.nls$processed++;
		return false;
	}
}
