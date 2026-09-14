package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import java.util.List;
import java.util.concurrent.Executor;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraftforge.common.ForgeConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Forge 26.x can retain more atlas mip levels than an animation frame can hold. */
@Mixin(SpriteLoader.class)
public abstract class ForgeSpriteLoaderMixin {
    @WrapOperation(method = "stitch", at = @At(value = "INVOKE",
        target = "Lnet/minecraftforge/common/ForgeConfig$Client;allowMipmapLowering()Z"))
    private boolean nls$allowSafeAnimationMipmaps(final ForgeConfig.Client config, final Operation<Boolean> original,
        final List<SpriteContents> sprites, final int maxMipmapLevels, final Executor executor) {
        if (original.call(config)) return true;

        for (SpriteContents sprite : sprites) {
            if (!sprite.isAnimated()) continue;
            // Every mip must leave both frame dimensions nonzero, including rectangular frames.
            int maxSafeLevel = 31 - Integer.numberOfLeadingZeros(Math.min(sprite.width(), sprite.height()));
            if (maxMipmapLevels > maxSafeLevel) {
                NoLoadingScreen.LOGGER.warn(
                    "Allowing atlas mipmap lowering for animated sprite {} ({}x{}, requested level {}, safe maximum {})",
                    sprite.name(), sprite.width(), sprite.height(), maxMipmapLevels, maxSafeLevel);
                // Restore vanilla's atlas-wide limit BEFORE stitching/generation/upload. Merely
                // clamping createTexture would leave the upload loop and atlas mip views mismatched.
                // This safety fix also applies with loading-screen replacement disabled.
                return true;
            }
        }
        return false; // Preserve Forge's setting for atlases without undersized animated frames.
    }
}
