package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Options.class)
public abstract class OptionsMixin {
	@ModifyReturnValue(method = "getEffectiveRenderDistance", at = @At("RETURN"))
	private int nls$smallSyntheticView(final int original) {
		// An empty private world needs no full-distance section graph or Sodium buffer set.
		// Do not write options or shrink adopted worlds; their already-built terrain is visible.
		return NoLoadingScreenConfig.get().enabled && PlaceholderWorld.syntheticActive() ? Math.min(original, 2) : original;
	}
}
