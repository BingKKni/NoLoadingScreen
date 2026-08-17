package io.github.bingkkni.noloadingscreen.mixin;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the adopted placeholder cut its listener's line to the server.
 *
 * <p>When a server switch is taken over, the placeholder keeps the outgoing world's real
 * {@code ClientPacketListener} — building a new one is not an option, since
 * {@code ClientPacketListener.<init>} is a hooked constructor with global side effects (Fabric API
 * registers the new listener as <em>the</em> client play addon there and throws if one already
 * exists). Reusing it costs nothing and constructs nothing.
 *
 * <p>The catch is that {@code Minecraft#getConnection()} resolves as {@code player.connection}, so
 * while the placeholder is bound for a frame, that listener is reachable — and its connection is the
 * live one, now in the configuration phase, where a play packet is a protocol violation. Swapping in
 * a channel-less {@link Connection} closes that hole: the listener is already unreachable to vanilla
 * by this point (its protocol has been swapped out and nothing else holds it), and anything that
 * does reach it now writes into a queue that is discarded with the object.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public interface ClientCommonPacketListenerImplAccessor {
	@Mutable
	@Accessor("connection")
	void nls$setConnection(Connection connection);
}
