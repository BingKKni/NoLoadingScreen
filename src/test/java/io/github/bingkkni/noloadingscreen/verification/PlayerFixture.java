package io.github.bingkkni.noloadingscreen.verification;

import net.minecraft.client.player.LocalPlayer;

/**
 * A LocalPlayer subclass fixtures allocate without construction. Only the superclass constructor
 * shape is API-family specific, so it is the one thing kept here; the constructor never runs.
 */
public abstract class PlayerFixture extends LocalPlayer {
	protected PlayerFixture() {
		super(null, null, null, null, null, null, false, null);
	}
}
