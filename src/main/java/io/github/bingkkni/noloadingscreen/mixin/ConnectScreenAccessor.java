package io.github.bingkkni.noloadingscreen.mixin;

import io.netty.channel.ChannelFuture;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ConnectScreen.class)
public interface ConnectScreenAccessor {
	@Accessor("connection") @Nullable Connection nls$connection();
	@Accessor("parent") Screen nls$parent();
	@Accessor("status") Component nls$status();
	@Accessor("aborted") boolean nls$aborted();
	@Accessor("aborted") void nls$setAborted(boolean aborted);
	@Accessor("channelFuture") @Nullable ChannelFuture nls$channelFuture();
	@Accessor("channelFuture") void nls$setChannelFuture(@Nullable ChannelFuture future);

}
