package io.github.bingkkni.noloadingscreen.mixin;

import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Two fields needed to hand this screen back to the player after it has been driven off-screen.
 *
 * <p>The screen cannot simply be re-mounted: {@code init()} appends to a {@code LinearLayout} held in
 * a field rather than rebuilding it, so a second mount stacks a second title and a second button on
 * top of the first. (Vanilla has the same problem on a window resize; it just never happens during a
 * configuration phase.) A fresh instance avoids that, and needs the connection the old one was
 * driving.
 *
 * <p>{@code delayTicker} then has to be carried across, for two reasons: a fresh screen would make
 * the player wait the full 600 ticks again after they have already been waiting, and the vanilla
 * check is {@code delayTicker == 600} — an exact equality that a counter restored past it would
 * never satisfy, leaving the Disconnect button dead forever.
 */
@Mixin(ServerReconfigScreen.class)
public interface ServerReconfigScreenAccessor {
	@Accessor("connection")
	Connection nls$connection();

	@Accessor("delayTicker")
	int nls$delayTicker();

	@Accessor("delayTicker")
	void nls$setDelayTicker(int delayTicker);
}
