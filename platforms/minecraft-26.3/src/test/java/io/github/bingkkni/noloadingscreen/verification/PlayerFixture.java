package io.github.bingkkni.noloadingscreen.verification;

import net.minecraft.client.player.LocalPlayer;

/** 26.3's LocalPlayer constructor also takes the totem-activation state. Never runs. */
public abstract class PlayerFixture extends LocalPlayer {
	protected PlayerFixture() {
		super(null, null, null, null, null, null, false, null, null);
	}
}
