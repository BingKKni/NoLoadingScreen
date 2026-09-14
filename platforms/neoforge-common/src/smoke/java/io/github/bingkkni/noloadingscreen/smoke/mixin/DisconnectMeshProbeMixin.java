package io.github.bingkkni.noloadingscreen.smoke.mixin;

import io.github.bingkkni.noloadingscreen.smoke.DisconnectHandoffVerification;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class DisconnectMeshProbeMixin {
	@Inject(method = "setLevel", at = @At("HEAD"))
	private void nls$observeMeshDetach(final ClientLevel next, final CallbackInfo ci) {
		DisconnectHandoffVerification.detachingLevel(next);
	}
}
