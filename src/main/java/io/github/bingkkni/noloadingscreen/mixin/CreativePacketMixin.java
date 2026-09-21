package io.github.bingkkni.noloadingscreen.mixin;

import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep vanilla's creative UI, but never enter its packet/prediction wrapper in our scene. */
@Mixin(MultiPlayerGameMode.class)
public abstract class CreativePacketMixin {
	@Inject(method = {"handleCreativeModeItemAdd", "handleCreativeModeItemDrop"}, at = @At("HEAD"), cancellable = true)
	private void nls$localCreative(final CallbackInfo ci) {
		if (PlaceholderWorld.owns(Minecraft.getInstance().player)) ci.cancel();
	}
}
