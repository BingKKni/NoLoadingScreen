package io.github.bingkkni.noloadingscreen;

import io.github.bingkkni.noloadingscreen.mixin.EntityAccessor;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Disposable drops: no Player.drop, server callbacks, inventory packets or remote entity ticks. */
public final class PlaceholderItems {
	private static final Map<ItemEntity, Integer> items = new IdentityHashMap<>();
	private static int nextId = -1;
	private PlaceholderItems() {}

	public static boolean owns(Entity entity) { return items.containsKey(entity); }
	public static void clear() {
		for (ItemEntity item : items.keySet()) item.discard();
		items.clear();
	}

	public static void dropSelected(LocalPlayer player, boolean wholeStack) {
		if (!PlaceholderWorld.owns(player) || player.isSpectator()) return;
		ItemStack held = player.getMainHandItem();
		if (!held.isEmpty()) drop(player, held.split(wholeStack ? held.getCount() : 1));
	}

	public static void drop(LocalPlayer player, ItemStack stack) {
		if (!PlaceholderWorld.owns(player) || stack.isEmpty()) return;
		ClientLevel level = (ClientLevel) player.level();
		ItemEntity item = new ItemEntity(level, player.getX(), player.getEyeY() - 0.3, player.getZ(), stack.copy());
		while (level.getEntity(nextId) != null) nextId--;
		item.setId(nextId--);
		item.setDeltaMovement(player.getLookAngle().scale(0.3).add(0, 0.1, 0));
		item.setOldPosAndRot();
		level.addEntity(item);
		items.put(item, 40);
	}

	public static void tick(LocalPlayer player) {
		if (!PlaceholderWorld.owns(player)) return;
		var iterator = items.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			ItemEntity item = entry.getKey();
			if (item.isRemoved() || item.getItem().isEmpty()) { iterator.remove(); continue; }
			if (((io.github.bingkkni.noloadingscreen.mixin.ItemEntityAccessor) item).nls$age() >= 6000) {
				item.discard(); iterator.remove(); continue;
			}
			entry.setValue(Math.max(0, entry.getValue() - 1));
			item.setOldPosAndRot();
			Vec3 requested = item.getDeltaMovement().add(0, -0.04, 0);
			Vec3 resolved = ((EntityAccessor) item).nls$collide(requested);
			item.setPos(item.position().add(resolved));
			boolean ground = requested.y < 0 && Math.abs(requested.y - resolved.y) > 1.0E-7;
			item.setOnGround(ground);
			item.setDeltaMovement(new Vec3(Math.abs(requested.x - resolved.x) > 1.0E-7 ? 0 : requested.x * (ground ? .58 : .98),
				ground ? 0 : requested.y * .98, Math.abs(requested.z - resolved.z) > 1.0E-7 ? 0 : requested.z * (ground ? .58 : .98)));
			item.tickCount++;
			var animation = (io.github.bingkkni.noloadingscreen.mixin.ItemEntityAccessor) item;
			animation.nls$age(animation.nls$age() + 1);
		}
		if (player.isSpectator()) return;
		for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(1, .5, 1))) {
			var pickup = (io.github.bingkkni.noloadingscreen.mixin.ItemEntityAccessor) item;
			int delay = pickup.nls$pickupDelay();
			// Retained server drops must not keep a finite pickup delay forever. Preserve the
			// vanilla 32767 'never pick up' sentinel without ticking the rest of the old entity.
			if (!owns(item) && delay > 0 && delay < 32767) pickup.nls$pickupDelay(delay - 1);
			if (item.isRemoved() || items.getOrDefault(item, 0) > 0 || item.hasPickUpDelay()) continue;
			ItemStack stack = item.getItem().copy();
			player.getInventory().add(stack);
			if (stack.getCount() < item.getItem().getCount()) ((ClientLevel) player.level()).playLocalSound(item,
				net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, .2F, 1F);
			if (stack.isEmpty()) item.discard();
			else item.setItem(stack);
		}
	}
}
