package io.github.bingkkni.noloadingscreen;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import io.github.bingkkni.noloadingscreen.platform.InventoryAccess;
import io.github.bingkkni.noloadingscreen.InventoryClick;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Local edits to a disposable client world, never MultiPlayerGameMode's packet/prediction path. */
public final class PlaceholderInteraction {
	private int useDelay;

	public void reset() { this.useDelay = 0; }
	public void tick() { if (this.useDelay > 0) this.useDelay--; }

	public void attack(final LocalPlayer player) {
		if (!PlaceholderWorld.owns(player)) return;
		// The one-argument LocalPlayer.swing sends a packet; LivingEntity's two-argument overload
		// only updates animation on a ClientLevel.
		player.swing(InteractionHand.MAIN_HAND, false);
		HitResult target = player.pick(player.blockInteractionRange(), 1.0F, false);
		if (target instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			breakBlock(player, hit);
		}
	}

	public static boolean breakBlock(final LocalPlayer player, final BlockHitResult hit) {
		if (!PlaceholderWorld.owns(player) || hit.getType() != HitResult.Type.BLOCK) return false;
		ClientLevel level = (ClientLevel) player.level();
		BlockPos pos = hit.getBlockPos();
		if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)) return false;
		var state = level.getBlockState(pos);
		// No loot, durability or entity attacks. Client updates invalidate retained meshes,
		// including Sodium's normal hooks. Failed/air edits must not produce phantom feedback.
		if (state.isAir() || !level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL | Block.UPDATE_IMMEDIATE)) return false;
		PlaceholderBlockEffects.destroy(level, pos, state);
		return true;
	}

	public void use(final LocalPlayer player) {
		if (this.useDelay > 0 || !PlaceholderWorld.owns(player)) return;
		this.useDelay = 4; // vanilla held-use cadence; one attempt per tick even with queued clicks
		HitResult target = player.pick(player.blockInteractionRange(), 1.0F, false);
		if (target instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			for (InteractionHand hand : InteractionHand.values()) {
				if (placeBlock(player, hand, hit)) {
					player.swing(hand, false);
					return;
				}
			}
		}
	}

	public static boolean placeBlock(final LocalPlayer player, final InteractionHand hand, final BlockHitResult hit) {
		if (!PlaceholderWorld.owns(player) || hit.getType() != HitResult.Type.BLOCK) return false;
		ItemStack stack = player.getItemInHand(hand);
		if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) return false;
		ClientLevel level = (ClientLevel) player.level();
		BlockPlaceContext context = new BlockPlaceContext(new UseOnContext(player, hand, hit));
		if (!level.hasChunkAt(hit.getBlockPos()) || !level.hasChunkAt(context.getClickedPos())
			|| level.isOutsideBuildHeight(context.getClickedPos())) return false;
		int before = stack.getCount();
		// Calling place directly skips use-on-block (chests/buttons/etc.) and use-item (food,
		// buckets/projectiles) entirely. Vanilla still supplies orientation, support, collision,
		// replacement, waterlogging and multiblock placement. ClientLevel never persists to disk.
		if (!item.place(context).consumesAction()) return false;
		// Survival consumption even if the outgoing server's cached GameType was creative.
		stack.setCount(before - 1);
		return true;
	}

	/** Creative-style pick, including all block-entity data available in this client snapshot. */
	public static boolean pickBlock(final LocalPlayer player) {
		if (!PlaceholderWorld.owns(player)) return false;
		HitResult target = player.pick(player.blockInteractionRange(), 1.0F, false);
		return target instanceof BlockHitResult hit && pickBlock(player, hit);
	}

	public static boolean pickBlock(final LocalPlayer player, final BlockHitResult hit) {
		if (!PlaceholderWorld.owns(player) || hit.getType() != HitResult.Type.BLOCK) return false;
		ClientLevel level = (ClientLevel) player.level();
		BlockPos pos = hit.getBlockPos();
		if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)) return false;
		var state = level.getBlockState(pos);
		if (state.isAir()) return false;
		ItemStack picked = state.getCloneItemStack(level, pos, true);
		if (picked.isEmpty() || !picked.isItemEnabled(level.enabledFeatures())) return false;
		var blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
		// Do not silently turn an as-yet-unavailable head/container into its data-less variant.
		if (state.hasBlockEntity() && blockEntity == null) return false;
		if (blockEntity != null) {
			// Vanilla's server pick helper takes a ServerLevel. Use its public serialization and
			// component APIs on our ClientLevel instead of sending a pick/query packet. PROFILE
			// lives in components, so copying just the raw NBT would lose player-head textures.
			try (var reporter = new ProblemReporter.ScopedCollector(blockEntity.problemPath(), NoLoadingScreen.LOGGER)) {
				TagValueOutput data = TagValueOutput.createWithContext(reporter, level.registryAccess());
				blockEntity.saveCustomOnly(data);
				blockEntity.removeComponentsFromTag(data);
				BlockItem.setBlockEntityData(picked, blockEntity.getType(), data);
				picked.applyComponents(blockEntity.collectComponents());
			}
		}
		Inventory inventory = player.getInventory();
		int existing = inventory.findSlotMatchingItem(picked); // compares components, not just item id
		if (Inventory.isHotbarSlot(existing)) inventory.setSelectedSlot(existing);
		else if (existing >= 0) inventory.pickSlot(existing);
		else inventory.addAndPickItem(picked);
		return true;
	}

	public static void selectSlot(final LocalPlayer player, final int slot) {
		if (PlaceholderWorld.owns(player) && slot >= 0 && slot < 9) player.getInventory().setSelectedSlot(slot);
	}

	public static void swapOffhand(final LocalPlayer player) {
		if (!PlaceholderWorld.owns(player)) return;
		ItemStack main = player.getMainHandItem();
		player.setItemInHand(InteractionHand.MAIN_HAND, player.getOffhandItem());
		player.setItemInHand(InteractionHand.OFF_HAND, main);
	}

	/** Vanilla inventory manipulation without its network wrapper. Deliberately no crafting/drop. */
	public static void clickSlot(final LocalPlayer player, final int slot, final int button, final InventoryClick input) {
		if (!PlaceholderWorld.owns(player)) return;
		// LocalPlayer.drop/creative drop and result-slot callbacks can send packets. Never enter
		// those branches. Quick-craft's negative start/end markers are local state transitions.
		if (input == InventoryClick.THROW || input == InventoryClick.CLONE) return;
		if (slot < 0 && input != InventoryClick.QUICK_CRAFT) return;
		if (slot >= 0 && (slot < 5 || slot >= player.inventoryMenu.slots.size())) return;
		// SWAP overflow can call Player.drop (which invokes LocalPlayer.swing). Refuse that one
		// case rather than generating packets or silently discarding the displaced stack.
		if (input == InventoryClick.SWAP) {
			if (slot < 0 || !((button >= 0 && button < 9) || button == 40)) return;
			var target = player.inventoryMenu.slots.get(slot);
			ItemStack source = player.getInventory().getItem(button);
			if (!target.getItem().isEmpty() && source.getCount() > target.getMaxStackSize(source)) return;
		}
		InventoryAccess.click(player, slot, button, input);
	}
}
