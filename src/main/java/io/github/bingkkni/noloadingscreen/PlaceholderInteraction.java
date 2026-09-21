package io.github.bingkkni.noloadingscreen;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import io.github.bingkkni.noloadingscreen.platform.InventoryAccess;
import io.github.bingkkni.noloadingscreen.platform.PlayerAnimation;
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
	private int breakDelay;
	private BlockPos breaking;
	private net.minecraft.world.level.block.state.BlockState breakingState;
	private ItemStack breakingTool;
	private float breakProgress;

	public void reset() { this.useDelay = this.breakDelay = 0; this.breaking = null; this.breakingState = null; this.breakingTool = null; this.breakProgress = 0; }
	public void tick() { if (this.useDelay > 0) this.useDelay--; if (this.breakDelay > 0) this.breakDelay--; }

	public void stopBreaking(final LocalPlayer player) {
		if (breaking != null) ((ClientLevel) player.level()).destroyBlockProgress(player.getId(), breaking, -1);
		breaking = null;
		breakingState = null;
		breakingTool = null;
		breakProgress = 0;
	}

	public void continueAttack(final LocalPlayer player) {
		if (!PlaceholderWorld.owns(player) || player.isSpectator() || breakDelay > 0) return;
		if (net.minecraft.client.Minecraft.getInstance().hitResult instanceof net.minecraft.world.phys.EntityHitResult) {
			stopBreaking(player);
			return;
		}
		HitResult target = player.pick(player.blockInteractionRange(), 1.0F, false);
		if (!(target instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) { stopBreaking(player); return; }
		ClientLevel level = (ClientLevel) player.level();
		BlockPos pos = hit.getBlockPos();
		var state = level.getBlockState(pos);
		if (state.isAir() || player.blockActionRestricted(level, pos, PlaceholderWorld.localMode())) { stopBreaking(player); return; }
		if (!pos.equals(breaking) || state != breakingState || !ItemStack.matches(breakingTool, player.getMainHandItem())) {
			stopBreaking(player);
			breaking = pos.immutable();
			breakingState = state;
			breakingTool = player.getMainHandItem().copy();
		}
		PlaceholderEquipment.update(player);
		PlayerAnimation.swingAttack(player);
		breakProgress += player.isCreative() ? 1 : state.getDestroyProgress(player, level, pos);
		if (breakProgress >= 1) {
			breakBlock(player, hit);
			stopBreaking(player);
			breakDelay = 5;
		} else level.destroyBlockProgress(player.getId(), pos, (int) (breakProgress * 10) - 1);
	}

	public void attack(final LocalPlayer player) {
		if (!PlaceholderWorld.owns(player) || player.isSpectator()) return;
		// Snapshot entities remain entities, not blocks. Give local hit feedback without attack packets.
		var target = net.minecraft.client.Minecraft.getInstance().hitResult;
		if (target instanceof net.minecraft.world.phys.EntityHitResult hit) {
			PlayerAnimation.swingAttack(player);
			if (hit.getEntity() instanceof net.minecraft.world.entity.LivingEntity living) {
				PlaceholderVisuals.hit(living);
			}
			return;
		}
		if (player.isCreative()) breakDelay = 0; // fresh presses are instant; held mining keeps its cadence
		continueAttack(player);
	}

	public static boolean breakBlock(final LocalPlayer player, final BlockHitResult hit) {
		if (!PlaceholderWorld.owns(player) || player.isSpectator() || hit.getType() != HitResult.Type.BLOCK) return false;
		ClientLevel level = (ClientLevel) player.level();
		BlockPos pos = hit.getBlockPos();
		if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)
			|| player.blockActionRestricted(level, pos, PlaceholderWorld.localMode())) return false;
		var state = level.getBlockState(pos);
		if (!player.isCreative() && state.getDestroySpeed(level, pos) < 0) return false;
		// No server loot/durability callbacks. Client updates invalidate retained meshes,
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
					PlayerAnimation.swingUse(player, hand);
					return;
				}
			}
		}
	}

	public static boolean placeBlock(final LocalPlayer player, final InteractionHand hand, final BlockHitResult hit) {
		if (!PlaceholderWorld.owns(player) || player.isSpectator() || hit.getType() != HitResult.Type.BLOCK) return false;
		ItemStack stack = player.getItemInHand(hand);
		if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) return false;
		ClientLevel level = (ClientLevel) player.level();
		if (!player.getAbilities().mayBuild && !stack.canPlaceOnBlockInAdventureMode(
			new net.minecraft.world.level.block.state.pattern.BlockInWorld(level, hit.getBlockPos(), false))) return false;
		BlockPlaceContext context = new BlockPlaceContext(new UseOnContext(player, hand, hit));
		if (!level.isLoaded(hit.getBlockPos()) || !level.isLoaded(context.getClickedPos())
			|| level.isOutsideBuildHeight(context.getClickedPos())) return false;
		int before = stack.getCount();
		// Calling place directly skips use-on-block (chests/buttons/etc.) and use-item (food,
		// buckets/projectiles) entirely. Vanilla still supplies orientation, support, collision,
		// replacement, waterlogging and multiblock placement. ClientLevel never persists to disk.
		if (!item.place(context).consumesAction()) return false;
		stack.setCount(player.isCreative() ? before : before - 1);
		return true;
	}

	/** Creative-style pick, including all block-entity data available in this client snapshot. */
	public static boolean pickBlock(final LocalPlayer player) {
		if (!PlaceholderWorld.owns(player) || player.isSpectator()) return false;
		HitResult target = player.pick(player.blockInteractionRange(), 1.0F, false);
		return target instanceof BlockHitResult hit && pickBlock(player, hit);
	}

	public static boolean pickBlock(final LocalPlayer player, final BlockHitResult hit) {
		if (!PlaceholderWorld.owns(player) || player.isSpectator() || hit.getType() != HitResult.Type.BLOCK) return false;
		ClientLevel level = (ClientLevel) player.level();
		BlockPos pos = hit.getBlockPos();
		if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) return false;
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
		else if (player.isCreative()) inventory.addAndPickItem(picked);
		else return false;
		return true;
	}

	public static void selectSlot(final LocalPlayer player, final int slot) {
		if (PlaceholderWorld.owns(player) && !player.isSpectator() && slot >= 0 && slot < 9) player.getInventory().setSelectedSlot(slot);
	}

	public static void swapOffhand(final LocalPlayer player) {
		if (!PlaceholderWorld.owns(player) || player.isSpectator()) return;
		ItemStack main = player.getMainHandItem();
		player.setItemInHand(InteractionHand.MAIN_HAND, player.getOffhandItem());
		player.setItemInHand(InteractionHand.OFF_HAND, main);
	}

	/** Vanilla stack manipulation, with local drop branches and no crafting/server-container callbacks. */
	public static void clickSlot(final LocalPlayer player, final int slot, final int button, final InventoryClick input) {
		if (!PlaceholderWorld.owns(player) || player.isSpectator()) return;
		if (input == InventoryClick.THROW && slot >= 5 && slot < player.inventoryMenu.slots.size()) {
			var target = player.inventoryMenu.slots.get(slot);
			if (target.mayPickup(player)) PlaceholderItems.drop(player, target.remove(button == 0 ? 1 : target.getItem().getCount()));
			return;
		}
		if (input == InventoryClick.PICKUP && slot == -999) {
			ItemStack carried = player.inventoryMenu.getCarried();
			PlaceholderItems.drop(player, carried.split(button == 0 ? carried.getCount() : 1));
			return;
		}
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
