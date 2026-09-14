package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.bingkkni.noloadingscreen.DisconnectedWorldView;
import io.github.bingkkni.noloadingscreen.LocalSkinPreloader;
import io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import io.github.bingkkni.noloadingscreen.SkinLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@Shadow protected abstract @Nullable PlayerInfo getPlayerInfo();

	@ModifyReturnValue(method = "getSkin", at = @At("RETURN"))
	private PlayerSkin nls$bridgeLocalSkin(final PlayerSkin original) {
		Minecraft minecraft = Minecraft.getInstance();
		AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
		if (!NoLoadingScreenConfig.get().enabled || player != minecraft.player || PlaceholderWorld.active()
			|| original != DefaultPlayerSkin.get(player.getUUID())) return original;
		PlayerSkin cached = LocalSkinPreloader.get(player.getUUID());
		if (cached == null) return original;
		PlayerInfo info = this.getPlayerInfo();
		if (info == null) return cached;
		// Vanilla has already started its lookup. Observe that exact supplier's future, even
		// after SkinManager's cache expires; never start a new request just to check readiness.
		return ((PlayerInfoAccessor) info).nls$skinLookup() instanceof SkinLookup lookup && lookup.pending() ? cached : original;
	}

	@ModifyReturnValue(method = "gameMode", at = @At("RETURN"))
	private @Nullable GameType nls$offlineSurvivalMode(final @Nullable GameType original) {
		// Player.gameMode() reads cached PlayerInfo, not MultiPlayerGameMode. Override only the
		// owned offline player; never mutate the listener's shared PlayerInfo/seen-player cache.
		return DisconnectedWorldView.active() && (Object) this instanceof LocalPlayer player && PlaceholderWorld.owns(player)
			? Minecraft.getInstance().gameMode.getPlayerMode() : original;
	}

	@Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
	private void nls$placeholderSkin(final CallbackInfoReturnable<PlayerSkin> cir) {
		PlayerSkin skin = PlaceholderWorld.skinFor((AbstractClientPlayer) (Object) this);
		if (skin != null) {
			cir.setReturnValue(skin);
		}
	}
}
