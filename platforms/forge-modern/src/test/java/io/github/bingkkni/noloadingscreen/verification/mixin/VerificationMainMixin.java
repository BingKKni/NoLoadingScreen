package io.github.bingkkni.noloadingscreen.verification.mixin;

import io.github.bingkkni.noloadingscreen.verification.ForgeClientVerification;
import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs under the real CLIENT launch target before the game creates a window. */
@Mixin(Main.class)
public abstract class VerificationMainMixin {
    @Inject(method = "main", at = @At("HEAD"))
    private static void nls$verify(String[] args, CallbackInfo ci) throws Exception {
        ForgeClientVerification.main(args);
        System.exit(0);
    }
}
