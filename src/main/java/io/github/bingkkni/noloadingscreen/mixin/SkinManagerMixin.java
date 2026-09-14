package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.authlib.GameProfile;
import io.github.bingkkni.noloadingscreen.LocalSkinPreloader;
import io.github.bingkkni.noloadingscreen.SkinLookup;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SkinManager.class)
public abstract class SkinManagerMixin {
	@WrapOperation(method = "createLookup", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/resources/SkinManager;get(Lcom/mojang/authlib/GameProfile;)Ljava/util/concurrent/CompletableFuture;"))
	private CompletableFuture<Optional<PlayerSkin>> nls$observeSkinFuture(final SkinManager manager, final GameProfile profile,
		final Operation<CompletableFuture<Optional<PlayerSkin>>> original,
		@Share("nls$skinFuture") final LocalRef<CompletableFuture<Optional<PlayerSkin>>> captured) {
		CompletableFuture<Optional<PlayerSkin>> future = original.call(manager, profile);
		if (LocalSkinPreloader.isLocalProfile(profile.id())) captured.set(future);
		return future;
	}

	@ModifyReturnValue(method = "createLookup", at = @At("RETURN"))
	private Supplier<PlayerSkin> nls$trackLocalLookup(final Supplier<PlayerSkin> original,
		@Share("nls$skinFuture") final LocalRef<CompletableFuture<Optional<PlayerSkin>>> captured) {
		return captured.get() == null ? original : new SkinLookup(original, captured.get());
	}
}
