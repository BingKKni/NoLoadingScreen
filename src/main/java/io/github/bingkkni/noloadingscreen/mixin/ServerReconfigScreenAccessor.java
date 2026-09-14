package io.github.bingkkni.noloadingscreen.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerReconfigScreen.class)
public interface ServerReconfigScreenAccessor {
	@Accessor("connection")
	Connection nls$connection();

	@Accessor("disconnectButton")
	Button nls$disconnectButton();
}
