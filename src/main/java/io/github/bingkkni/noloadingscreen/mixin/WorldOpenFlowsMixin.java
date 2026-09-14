package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.bingkkni.noloadingscreen.LoadingWaitLoop;
import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldOpenFlows.class)
public abstract class WorldOpenFlowsMixin {
	@Inject(method = "openWorldLoadLevelStem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/Minecraft;setScreenAndShow(Lnet/minecraft/client/gui/screens/Screen;)V", shift = At.Shift.AFTER))
	private void nls$preparingResources(final CallbackInfo ci) {
		NoLoadingScreen.onPreparingResources();
	}

	@WrapOperation(method = "loadWorldDataBlocking", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/Minecraft;managedBlock(Ljava/util/function/BooleanSupplier;)V"))
	private void nls$interactiveResourceWait(final Minecraft minecraft, final BooleanSupplier done, final Operation<Void> original) {
		if (!NoLoadingScreen.preparingResources()) {
			original.call(minecraft, done);
			return;
		}
		LoadingWaitLoop.begin();
		try {
			// Preserve managedBlock's completion-executor pumping, exceptions and task ordering.
			original.call(minecraft, (BooleanSupplier) () -> {
				if (done.getAsBoolean()) return true;
				LoadingWaitLoop.frameIfDue(minecraft);
				return done.getAsBoolean();
			});
		} finally {
			LoadingWaitLoop.end();
		}
	}
}
