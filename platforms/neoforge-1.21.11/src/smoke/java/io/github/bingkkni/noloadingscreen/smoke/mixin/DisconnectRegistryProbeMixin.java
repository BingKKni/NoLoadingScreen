package io.github.bingkkni.noloadingscreen.smoke.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.bingkkni.noloadingscreen.smoke.DisconnectHandoffVerification;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Observe, never replace, vanilla's saved registry-reversion decision and actual call. */
@Mixin(Minecraft.class)
public abstract class DisconnectRegistryProbeMixin {
	@WrapOperation(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At(value = "INVOKE", target =
		"Lnet/neoforged/neoforge/registries/RegistryManager;revertToFrozen()V"))
	private void nls$observeRegistryRevert(final Operation<Void> original) {
		DisconnectHandoffVerification.revertingRegistries();
		original.call();
	}
}
