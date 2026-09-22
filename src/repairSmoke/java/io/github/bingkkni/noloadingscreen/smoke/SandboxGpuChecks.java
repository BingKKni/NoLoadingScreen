package io.github.bingkkni.noloadingscreen.smoke;

import io.github.bingkkni.noloadingscreen.*;
import io.github.bingkkni.noloadingscreen.compat.WaveyCapesCompatibility;
import io.github.bingkkni.noloadingscreen.platform.SceneFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Real players, registries, equipment, entities and optional cape physics in an isolated client. */
public final class SandboxGpuChecks {
	private static int checks;
	private SandboxGpuChecks() {}
	public static void run(Minecraft client) throws Exception {
		check(PlaceholderWorld.bind(), "Bind sandbox checks");
		try {
			LocalPlayer player = client.player;
			var initialize = PlaceholderWorld.class.getDeclaredMethod("initializeMovement");
			initialize.setAccessible(true);
			for (GameType mode : GameType.values()) {
				client.gameMode.setLocalMode(mode);
				player.getAbilities().mayfly = player.getAbilities().flying = false; // outgoing server denial
				initialize.invoke(null);
				PlaceholderWorld.tick();
				check(player.gameMode() == mode && !player.getAbilities().flying, "Mode and server flight denial retained: " + mode);
				check(player.noPhysics == (mode == GameType.SPECTATOR), "Only spectator noclip is implicit: " + mode);
				NoLoadingScreenConfig.get().allowFlightAndNoclip = true;
				PlaceholderWorld.tick();
				check(player.getAbilities().mayfly && player.getAbilities().flying && player.noPhysics, "Explicit override: " + mode);
				NoLoadingScreenConfig.get().allowFlightAndNoclip = false;
				PlaceholderWorld.tick();
				check(!player.getAbilities().mayfly && !player.getAbilities().flying, "Turning override off restores denial: " + mode);
			}
			check(PlaceholderCommands.execute("/gamemode creative") && player.isCreative(), "Local gamemode command");
			check(!player.noPhysics, "Creative command grants flight permission, not noclip");
			PlaceholderWorld.setLocalMode(GameType.SURVIVAL);
			player.getInventory().setSelectedSlot(0);
			player.setPos(8.5, 78, 8.5);
			player.setYRot(0); player.setXRot(0); player.setOldPosAndRot(); player.setOnGround(true);
			BlockPos pos = new BlockPos(8, 79, 10);
			var stone = Blocks.STONE.defaultBlockState();
			client.level.setBlock(pos, stone, 3);
			player.getInventory().setItem(0, ItemStack.EMPTY);
			PlaceholderEquipment.update(player);
			var interaction = new PlaceholderInteraction();
			interaction.attack(player);
			check(client.level.getBlockState(pos).is(Blocks.STONE), "Survival does not instantly mine stone");
			interaction.stopBreaking(player);

			// No real server has synchronized tags in this synthetic fixture. Bind just the test
			// block's tool tags for this assertion, restoring them immediately afterwards.
			var holder = Blocks.STONE.builtInRegistryHolder();
			var originalTags = holder.tags().toList();
			var bindTags = net.minecraft.core.Holder.Reference.class.getDeclaredMethod("bindTags", java.util.Collection.class);
			bindTags.setAccessible(true);
			bindTags.invoke(holder, java.util.List.of(BlockTags.MINEABLE_WITH_PICKAXE));
			try {
				ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
				player.getInventory().setItem(0, pick);
				PlaceholderEquipment.update(player);
				float plain = stone.getDestroyProgress(player, client.level, pos);
				pick.enchant(client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY), 5);
				PlaceholderEquipment.update(player);
				float enchanted = stone.getDestroyProgress(player, client.level, pos);
				check(enchanted > plain, "Inventory enchantment components update local mining attributes");
				player.addEffect(new MobEffectInstance(MobEffects.HASTE, 200, 1));
				check(stone.getDestroyProgress(player, client.level, pos) > enchanted, "Haste still accelerates local breaking");
				player.removeEffect(MobEffects.HASTE);
			} finally { bindTags.invoke(holder, originalTags); }

			PlaceholderWorld.setLocalMode(GameType.CREATIVE);
			interaction.reset(); interaction.attack(player);
			check(client.level.getBlockState(pos).isAir(), "Creative mining is instant");
			client.level.setBlock(pos, stone, 3);
			player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 3));
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, .5, 0), Direction.UP, pos, false);
			check(PlaceholderInteraction.placeBlock(player, InteractionHand.MAIN_HAND, hit) && player.getMainHandItem().getCount() == 3,
				"Creative placement does not consume the stack");

			PlaceholderWorld.setLocalMode(GameType.SURVIVAL);
			player.getInventory().clearContent();
			player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
			PlaceholderEquipment.update(player);
			PlaceholderItems.dropSelected(player, false);
			check(player.getMainHandItem().getCount() == 2, "Drop removes one real stack item");
			ItemEntity drop = client.level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(3)).stream()
				.filter(PlaceholderItems::owns).findFirst().orElseThrow();
			for (int i = 0; i < 39; i++) { drop.setPos(player.position()); drop.setDeltaMovement(Vec3.ZERO); PlaceholderItems.tick(player); }
			check(!drop.isRemoved() && player.getMainHandItem().getCount() == 2, "Pickup delay prevents immediate re-collection");
			drop.setPos(player.position()); PlaceholderItems.tick(player);
			check(drop.isRemoved() && player.getMainHandItem().getCount() == 3, "Dropped components/stack are picked up locally");
			check(player.getMainHandItem().getPopTime() == 5, "Pickup starts the vanilla hotbar pop animation");
			for (int time = 4; time >= 0; time--) {
				PlaceholderItems.tick(player);
				check(player.getMainHandItem().getPopTime() == time, "Hotbar pop advances once per local tick: " + time);
			}
			ItemEntity retainedDrop = new ItemEntity(client.level, player.getX(), player.getY(), player.getZ(), new ItemStack(Items.GOLD_INGOT));
			retainedDrop.setId(-10001); retainedDrop.setPickUpDelay(3); client.level.addEntity(retainedDrop);
			PlaceholderItems.tick(player); PlaceholderItems.tick(player);
			check(!retainedDrop.isRemoved(), "Retained finite pickup delay is respected");
			PlaceholderItems.tick(player);
			check(retainedDrop.isRemoved(), "Retained finite pickup delay expires without entity AI ticks");

			var targetPlayer = new net.minecraft.client.player.RemotePlayer(client.level,
				new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "SandboxTarget"));
			targetPlayer.setId(-10000); targetPlayer.setPos(9.5, 78, 9.5); client.level.addEntity(targetPlayer);
			PlaceholderWorld.setLocalMode(GameType.SPECTATOR);
			check(PlaceholderWorld.playerNames().contains("SandboxTarget") && PlaceholderWorld.teleportToPlayer("SandboxTarget"), "Spectator can select and teleport to a retained player");
			check(player.position().equals(targetPlayer.position()), "Spectator teleport uses the captured player position");
			PlaceholderWorld.setLocalMode(GameType.SURVIVAL);
			verifyCombat(client, player, targetPlayer);
			Object beforeSimulation = WaveyCapesCompatibility.capture(player);
			java.util.List<Vec3> beforePoints = capePoints(beforeSimulation);
			for (int i = 0; i < 20; i++) {
				player.setOldPosAndRot(); player.setPos(player.position().add(0, 0, .2));
				PlaceholderVisuals.tick(player, 0, 0, .2);
			}
			var cape = ((CapeState) player.avatarState()).nls$capture(player.position());
			Object simulation = WaveyCapesCompatibility.capture(player);
			if (io.github.bingkkni.noloadingscreen.platform.LoaderServices.modVersion("waveycapes").isPresent()) {
				check(simulation != null, "WaveyCapes simulation exists without player ticks");
				check(!capePoints(simulation).equals(beforePoints), "Walking advances actual WaveyCapes particle positions");
			}
			java.util.List<Vec3> movingPoints = capePoints(simulation);
			CapeContinuity.capture(player);
			LocalPlayer incoming = SceneFactory.createPlayer(client.gameMode, client.level);
			incoming.setPos(4000, 80, 4000); incoming.yBodyRot = player.yBodyRot;
			CapeContinuity.restore(incoming, true);
			var restored = ((CapeState) incoming.avatarState()).nls$capture(incoming.position());
			check(restored.current().distanceTo(cape.current()) < 1e-6 && restored.previous().distanceTo(cape.previous()) < 1e-6, "Login keeps both relative cape positions");
			if (simulation != null) {
				check(WaveyCapesCompatibility.capture(incoming) == simulation, "Login keeps the exact WaveyCapes physics simulation");
				Class.forName("dev.tr7zw.waveycapes.versionless.CapeHolder").getMethod("updateSimulation", int.class).invoke(incoming, 16);
				check(capePoints(simulation).equals(movingPoints), "First native simulation initialization does not reset the cape");
			}
			if (io.github.bingkkni.noloadingscreen.platform.LoaderServices.modVersion("entityculling").isPresent()) {
				Class<?> cullable = Class.forName("dev.tr7zw.entityculling.versionless.access.Cullable");
				cullable.getMethod("setCulled", boolean.class).invoke(player, true);
				check(!(boolean) cullable.getMethod("isCulled").invoke(player), "Retained entities ignore stale occlusion while bound");
				PlaceholderWorld.unbind();
				try { check((boolean) cullable.getMethod("isCulled").invoke(player), "Culling remains enabled outside the disposable render scope"); }
				finally { check(PlaceholderWorld.bind(), "Restore culling fixture binding"); }
				cullable.getMethod("setCulled", boolean.class).invoke(player, false);
			}
			player.setPos(8.5, 78, 8.5); player.setDeltaMovement(Vec3.ZERO);
			PlaceholderWorld.setLocalMode(GameType.CREATIVE);
			initialize.invoke(null);
			player.getInventory().clearContent();
			NoLoadingScreen.LOGGER.info("SandboxGpuChecks PASSED: {} assertions.", checks);
		} finally { NoLoadingScreenConfig.get().allowFlightAndNoclip = false; PlaceholderWorld.unbind(); }
	}
	private static void verifyCombat(Minecraft client, LocalPlayer player, net.minecraft.client.player.RemotePlayer target) throws Exception {
		player.getInventory().clearContent();
		player.setYRot(0);
		for (int i = 0; i < 30; i++) PlaceholderVisuals.tick(player, 0, 0, 0);
		// Real PlayerInfo game modes, not an invented NPC attribute or server query.
		var info = new net.minecraft.client.multiplayer.PlayerInfo(target.getGameProfile(), false);
		var infoField = net.minecraft.client.player.AbstractClientPlayer.class.getDeclaredField("playerInfo");
		infoField.setAccessible(true); infoField.set(target, info);
		var modeField = net.minecraft.client.multiplayer.PlayerInfo.class.getDeclaredField("gameMode");
		modeField.setAccessible(true);
		target.setPos(player.position().add(1, 0, 1));
		target.setHealth(2); target.setOnGround(true); target.setDeltaMovement(Vec3.ZERO);
		for (GameType mode : new GameType[]{GameType.CREATIVE, GameType.SPECTATOR}) {
			modeField.set(info, mode);
			check(!PlaceholderCombat.attack(player, target) && target.getHealth() == 2 && target.hurtTime == 0,
				"Synced NPC mode is not damageable: " + mode);
		}
		modeField.set(info, GameType.SURVIVAL);
		target.getAbilities().invulnerable = true;
		check(!PlaceholderCombat.attack(player, target), "Known invulnerability is respected");
		target.getAbilities().invulnerable = false;
		check(PlaceholderCombat.attack(player, target) && target.getHealth() == 1, "Attack subtracts from last synced health, not max health");
		check(target.getDeltaMovement().z > 0 && target.getDeltaMovement().y > 0, "Unarmoured grounded entity gets knockback");
		check(!PlaceholderCombat.attack(player, target) && target.getHealth() == 1, "Same-tick attacks do not bypass hurt immunity");
		Vec3 before = target.position();
		PlaceholderCombat.tick();
		check(!target.position().equals(before) && target.hurtTime == 9, "Knockback and hurt visuals advance without AI ticks");
		for (int i = 0; i < 30; i++) PlaceholderVisuals.tick(player, 0, 0, 0);
		target.setPos(player.position().add(1, 0, 1)); target.setOnGround(true); target.setDeltaMovement(Vec3.ZERO);
		target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
		check(PlaceholderCombat.attack(player, target) && target.isDeadOrDying(), "Retained living entity can die locally");
		check(target.getDeltaMovement().equals(Vec3.ZERO), "Full knockback resistance is preserved");
		for (int i = 0; i < 20; i++) PlaceholderCombat.tick();
		check(target.isRemoved(), "Death animation expires and removes only the discarded client entity");
		client.hitResult = net.minecraft.world.phys.BlockHitResult.miss(player.getEyePosition().add(0, 10, 0), Direction.UP, player.blockPosition().above(10));
		player.setXRot(-90);
		new PlaceholderInteraction().attack(player);
		for (int i = 0; i < 2; i++) PlaceholderVisuals.tick(player, 0, 0, 0);
		check(io.github.bingkkni.noloadingscreen.platform.PlayerAnimation.swinging(player), "An air attack swings the empty hand");
		player.setXRot(0);
		client.hitResult = null;
	}

	private static java.util.List<Vec3> capePoints(Object simulation) throws Exception {
		if (simulation == null) return java.util.List.of();
		Class<?> api = Class.forName("dev.tr7zw.waveycapes.versionless.sim.BasicSimulation");
		Class<?> pointApi = Class.forName("dev.tr7zw.waveycapes.versionless.util.CapePoint");
		var x = pointApi.getMethod("getLerpX", float.class);
		var y = pointApi.getMethod("getLerpY", float.class);
		var z = pointApi.getMethod("getLerpZ", float.class);
		var result = new java.util.ArrayList<Vec3>();
		for (Object point : (java.util.List<?>) api.getMethod("getPoints").invoke(simulation)) {
			result.add(new Vec3((float) x.invoke(point, 1F), (float) y.invoke(point, 1F), (float) z.invoke(point, 1F)));
		}
		return result;
	}
	private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
