package io.github.bingkkni.noloadingscreen.verification;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.bingkkni.noloadingscreen.PlaceholderInteraction;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Actual vanilla cloning, component/NBT serialization and inventory selection; no server or skin lookup. */
final class PickBlockVerification {
	private static int assertions;

	static void run(final Minecraft minecraft, final LocalPlayer player, final LoadingVisualVerification.EmptyLevel level)
		throws ReflectiveOperationException {
		Inventory inventory = player.getInventory();
		List<ItemStack> savedItems = List.copyOf(inventory.getNonEquipmentItems());
		int selected = inventory.getSelectedSlot();
		var savedBlocks = new HashMap<>(level.blocks);
		var savedEntities = level.blockEntities;
		Object savedRegistries = get(Level.class, level, "registryAccess");
		Vec3 position = player.position();
		float yaw = player.getYRot(), pitch = player.getXRot();
		KeyMapping previousPick = minecraft.options.keyPickItem;
		KeyMapping[] previousMappings = minecraft.options.keyMappings;
		KeyMapping pick = new KeyMapping("nls.test.pickBlock", 76, KeyMapping.Category.MISC);
		KeyMapping[] mappings = Arrays.copyOf(previousMappings, previousMappings.length + 1);
		mappings[mappings.length - 1] = pick;
		set(Options.class, minecraft.options, "keyPickItem", pick);
		set(Options.class, minecraft.options, "keyMappings", mappings);
		set(Level.class, level, "registryAccess", RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
		level.blockEntities = new HashMap<>();
		Items.PLAYER_HEAD.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
		BlockPos pos = new BlockPos(3, 81, 3);
		BlockHitResult hit = hit(pos);
		try {
			check(PlaceholderWorld.bind(), "Pick tests bind the owned local scene");
			try {
				clear(inventory);
				level.blocks.clear();
				level.blocks.put(pos, Blocks.STONE.defaultBlockState());
				check(PlaceholderInteraction.pickBlock(player, hit) && player.getMainHandItem().is(Items.STONE),
					"Survival placeholder can obtain a block without creative abilities or a network controller");
				ItemStack first = player.getMainHandItem();
				first.setCount(17);
				check(PlaceholderInteraction.pickBlock(player, hit) && player.getMainHandItem() == first && first.getCount() == 17,
					"Repeated pick selects the existing matching stack instead of duplicating or shrinking it");
				clear(inventory);
				ItemStack stored = new ItemStack(Items.STONE, 9);
				inventory.setItem(12, stored);
				check(PlaceholderInteraction.pickBlock(player, hit) && player.getMainHandItem() == stored && inventory.getItem(12).isEmpty(),
					"Vanilla pickSlot brings a main-inventory match to the hotbar without cloning it");
				for (int slot = 0; slot < 36; slot++) inventory.setItem(slot, new ItemStack(Items.DIAMOND, 1));
				check(PlaceholderInteraction.pickBlock(player, hit) && player.getMainHandItem().is(Items.STONE)
					&& inventory.getNonEquipmentItems().stream().filter(stack -> stack.is(Items.DIAMOND)).count() == 35,
					"A full inventory follows vanilla creative replacement, with no drop or packet");

				clear(inventory);
				ItemStack headA = head("HeadA", "texture-a");
				ItemStack headB = head("HeadB", "texture-b");
				TestSkull skull = skull(level, pos, Blocks.PLAYER_HEAD.defaultBlockState(), headA);
				CompoundTag before = skull.saveCustomOnly(level.registryAccess());
				check(PlaceholderInteraction.pickBlock(player, hit), "Player head can be picked with all available data");
				ItemStack picked = player.getMainHandItem();
				int headSlot = inventory.getSelectedSlot();
				check(picked.is(Items.PLAYER_HEAD) && picked.get(DataComponents.PROFILE).equals(headA.get(DataComponents.PROFILE)),
					"Picked head retains its actual profile rather than becoming Steve");
				check(picked.get(DataComponents.PROFILE).partialProfile().properties().equals(headA.get(DataComponents.PROFILE).partialProfile().properties()),
					"Signed texture properties survive intact without another account/skin request");
				check(picked.get(DataComponents.CUSTOM_NAME).equals(headA.get(DataComponents.CUSTOM_NAME))
					&& picked.get(DataComponents.NOTE_BLOCK_SOUND).equals(headA.get(DataComponents.NOTE_BLOCK_SOUND))
					&& picked.get(DataComponents.CUSTOM_DATA).equals(headA.get(DataComponents.CUSTOM_DATA)),
					"Native implicit and extra components keep names, note-block sound and custom data");
				CompoundTag data = picked.get(DataComponents.BLOCK_ENTITY_DATA).copyTagWithoutId();
				check(data.getString("extra_payload").orElseThrow().equals("retained-nbt") && !data.contains("profile")
					&& !data.contains("x") && !data.contains("y") && !data.contains("z"),
					"Residual block-entity NBT is copied, while vanilla component extraction removes duplicate profile/position metadata");
				check(skull.saveCustomOnly(level.registryAccess()).equals(before), "Picking does not mutate the source block entity");
				skull(level, pos, Blocks.PLAYER_HEAD.defaultBlockState(), headB);
				check(PlaceholderInteraction.pickBlock(player, hit) && inventory.getSelectedSlot() != headSlot
					&& player.getMainHandItem().get(DataComponents.PROFILE).equals(headB.get(DataComponents.PROFILE)),
					"Different profiles are not mistaken for the same item by hotbar matching");
				skull(level, pos, Blocks.PLAYER_WALL_HEAD.defaultBlockState(), headA);
				check(PlaceholderInteraction.pickBlock(player, hit) && inventory.getSelectedSlot() == headSlot && player.getMainHandItem() == picked,
					"Wall-head cloning yields the same textured head item and reuses the matching slot");
				level.blockEntities.remove(pos);
				check(!PlaceholderInteraction.pickBlock(player, hit) && player.getMainHandItem() == picked,
					"Missing client block-entity data cannot silently produce a default head");
				level.blocks.put(pos, Blocks.AIR.defaultBlockState());
				check(!PlaceholderInteraction.pickBlock(player, hit), "Air cannot create an item");
				level.blocks.put(pos, Blocks.STONE.defaultBlockState());
				level.chunksMissing = true;
				check(!PlaceholderInteraction.pickBlock(player, hit), "Unloaded chunks cannot be picked");
				level.chunksMissing = false;
				check(!PlaceholderInteraction.pickBlock(player, hit(new BlockPos(0, 1000, 0))), "Out-of-height picks are rejected");
				check(!PlaceholderInteraction.pickBlock(player, BlockHitResult.miss(Vec3.ZERO, Direction.UP, pos)), "A missed ray cannot pick a block");
				clear(inventory);
				skull(level, pos, Blocks.PLAYER_HEAD.defaultBlockState(), headA);
				player.setPos(3.5, 80, .5);
				player.setYRot(0);
				player.setXRot(10); // heads are shorter than a full block
				player.setOldPosAndRot();
			} finally { PlaceholderWorld.unbind(); }

			check(!PlaceholderInteraction.pickBlock(player, hit), "Unbound/live gameplay cannot enter local picking");
			KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(76));
			PlaceholderWorld.handleSafeKeybinds();
			check(player.getMainHandItem().is(Items.PLAYER_HEAD) && player.getMainHandItem().get(DataComponents.PROFILE) != null,
				"Actual rebound pick key performs a fresh raycast and preserves the head profile");
			check(minecraft.level == null && minecraft.player == null && minecraft.gameMode == null && !pick.consumeClick(),
				"Pick key consumes its click and releases all placeholder fields");
			KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(76));
			PlaceholderWorld.discardQueuedClicks();
			check(!pick.consumeClick(), "Handoff drains pending pick clicks along with other local input");
		} finally {
			PlaceholderWorld.releaseAll();
			for (int slot = 0; slot < savedItems.size(); slot++) inventory.setItem(slot, savedItems.get(slot));
			inventory.setSelectedSlot(selected);
			level.blocks.clear();
			level.blocks.putAll(savedBlocks);
			level.blockEntities = savedEntities;
			level.chunksMissing = false;
			set(Level.class, level, "registryAccess", savedRegistries);
			player.setPos(position);
			player.setYRot(yaw);
			player.setXRot(pitch);
			player.setOldPosAndRot();
			while (pick.consumeClick()) {}
			set(Options.class, minecraft.options, "keyPickItem", previousPick);
			set(Options.class, minecraft.options, "keyMappings", previousMappings);
		}
		System.out.println("PickBlockVerification: " + assertions + " assertions passed (native clone/NBT/components, textured heads, inventory matching, key routing and local-only guards).");
	}

	private static ItemStack head(final String name, final String texture) {
		GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name,
			new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", texture, "signature-" + texture))));
		ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
		stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("Named head").withStyle(ChatFormatting.GOLD));
		stack.set(DataComponents.NOTE_BLOCK_SOUND, Identifier.withDefaultNamespace("block.note_block.bell"));
		CompoundTag custom = new CompoundTag();
		custom.putString("custom_component", "preserved");
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
		return stack;
	}

	private static TestSkull skull(final LoadingVisualVerification.EmptyLevel level, final BlockPos pos, final BlockState state, final ItemStack stack) {
		level.blocks.put(pos, state);
		TestSkull skull = new TestSkull(pos, state);
		skull.applyComponentsFromItemStack(stack);
		level.blockEntities.put(pos, skull);
		return skull;
	}

	private static final class TestSkull extends SkullBlockEntity {
		TestSkull(BlockPos pos, BlockState state) { super(pos, state); }
		@Override protected void saveAdditional(ValueOutput output) {
			super.saveAdditional(output);
			output.putString("extra_payload", "retained-nbt");
		}
	}
	private static void clear(final Inventory inventory) {
		for (int slot = 0; slot < 36; slot++) inventory.setItem(slot, ItemStack.EMPTY);
		inventory.setSelectedSlot(0);
	}
	private static BlockHitResult hit(final BlockPos pos) { return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false); }
	private static void set(final Class<?> type, final Object target, final String name, final Object value) throws ReflectiveOperationException {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
	private static Object get(final Class<?> type, final Object target, final String name) throws ReflectiveOperationException {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}
	private static void check(final boolean condition, final String message) {
		assertions++;
		if (!condition) throw new AssertionError(message);
	}
}
