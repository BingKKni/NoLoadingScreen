package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.bingkkni.noloadingscreen.LoadingWork;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public abstract class PacketDispatchMixin {
	@WrapOperation(method = "handle", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"))
	private void nls$measureIndividualPacket(final Packet<?> packet, final PacketListener listener, final Operation<Void> original) {
		Minecraft minecraft = Minecraft.getInstance();
		long start = listener.flow() == PacketFlow.CLIENTBOUND && minecraft != null && minecraft.isSameThread() ? LoadingWork.startTiming() : 0L;
		try {
			original.call(packet, listener);
		} finally {
			// Log the handler type only, never chat, profile data, server addresses or packet contents.
			if (start != 0L && System.nanoTime() - start >= 100_000_000L) {
				LoadingWork.endTiming("packet " + packet.getClass().getSimpleName(), start);
			}
		}
	}
}
