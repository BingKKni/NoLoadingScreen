package io.github.bingkkni.noloadingscreen.mixin;

import io.github.bingkkni.noloadingscreen.PlaceholderItems;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerDropMixin {
	@Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
	private void nls$localDrop(ItemStack stack, boolean scatter, CallbackInfoReturnable<ItemEntity> cir) {
		if ((Object) this instanceof LocalPlayer player && PlaceholderWorld.owns(player)) {
			PlaceholderItems.drop(player, stack);
			cir.setReturnValue(null);
		}
	}
}
