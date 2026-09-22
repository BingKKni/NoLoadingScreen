package io.github.bingkkni.noloadingscreen;

import io.github.bingkkni.noloadingscreen.mixin.EntityAccessor;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Local combat on the discarded client snapshot; no AI, loot, server damage or attack packets. */
public final class PlaceholderCombat {
	private static final Map<LivingEntity, Hit> hits = new IdentityHashMap<>();
	private PlaceholderCombat() {}

	private static final class Hit {
		int immunity;
		float lastDamage;
	}

	public static boolean animating(Entity entity) { return hits.containsKey(entity); }
	public static void clear() { hits.clear(); }

	public static boolean attack(LocalPlayer player, Entity target) {
		if (!PlaceholderWorld.owns(player) || player.isSpectator() || target == player
			|| target.level() != player.level() || !(target instanceof LivingEntity living)
			|| !living.isAlive() || !living.isAttackable() || living.isInvulnerable()
			|| target.distanceToSqr(player) > Math.pow(player.entityInteractionRange() + target.getBbWidth(), 2)) return false;
		// Player-shaped NPCs use the last received PlayerInfo game mode. Plugin-only flags
		// (or player health a server never sent) cannot be recovered by a client-only mod.
		if (living instanceof Player other && (other.isCreative() || other.isSpectator() || other.getAbilities().invulnerable)) return false;
		PlaceholderEquipment.update(player);
		float strength = player.getAttackStrengthScale(0.5F);
		float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.2F + strength * strength * 0.8F);
		player.resetAttackStrengthTicker();
		Hit hit = hits.computeIfAbsent(living, ignored -> new Hit());
		if (hit.immunity > 0 && damage <= hit.lastDamage) return false;
		float applied = hit.immunity > 0 ? damage - hit.lastDamage : damage;
		hit.lastDamage = damage;
		hit.immunity = 10;
		var source = player.damageSources().playerAttack(player);
		applied = CombatRules.getDamageAfterAbsorb(living, applied, source, living.getArmorValue(),
			(float) living.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
		var resistance = living.getEffect(MobEffects.RESISTANCE);
		if (resistance != null) applied *= Math.max(0, 1 - (resistance.getAmplifier() + 1) * 0.2F);
		float absorbed = Math.min(living.getAbsorptionAmount(), applied);
		living.setAbsorptionAmount(living.getAbsorptionAmount() - absorbed);
		// DATA_HEALTH_ID is already the final server-synchronised health, not max health.
		// The actual server entity is a different object, including in singleplayer.
		living.setHealth(Math.max(0, living.getHealth() - (applied - absorbed)));
		living.hurtTime = living.hurtDuration = 10;
		double knockback = (0.4 + (player.isSprinting() && strength > 0.9F ? 0.5 : 0)
			+ player.getAttributeValue(Attributes.ATTACK_KNOCKBACK)) * (1 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
		if (knockback > 0) {
			double yaw = player.getYRot() * Math.PI / 180;
			Vec3 previous = living.getDeltaMovement();
			living.setDeltaMovement(previous.x / 2 - Math.sin(yaw) * knockback,
				living.onGround() ? Math.min(0.4, previous.y / 2 + knockback) : previous.y,
				previous.z / 2 + Math.cos(yaw) * knockback);
		}
		return true;
	}

	public static void tick() {
		var iterator = hits.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			LivingEntity entity = entry.getKey();
			if (entity.isRemoved()) { iterator.remove(); continue; }
			entity.setOldPosAndRot();
			if (entry.getValue().immunity > 0) entry.getValue().immunity--;
			if (entity.hurtTime > 0) entity.hurtTime--;
			if (entity.isDeadOrDying() && ++entity.deathTime >= 20) {
				entity.discard();
				iterator.remove();
				continue;
			}
			Vec3 requested = entity.getDeltaMovement().add(0, entity.isNoGravity() ? 0 : -entity.getGravity(), 0);
			Vec3 resolved = ((EntityAccessor) entity).nls$collide(requested);
			entity.setPos(entity.position().add(resolved));
			boolean ground = requested.y < 0 && Math.abs(requested.y - resolved.y) > 1.0E-7;
			entity.setOnGround(ground);
			double friction = ground ? entity.level().getBlockState(entity.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() * 0.91 : 0.91;
			entity.setDeltaMovement(new Vec3(Math.abs(requested.x - resolved.x) > 1.0E-7 ? 0 : requested.x * friction,
				ground ? 0 : requested.y * 0.98, Math.abs(requested.z - resolved.z) > 1.0E-7 ? 0 : requested.z * friction));
			if (entity.isAlive() && entry.getValue().immunity == 0 && (ground || entity.isNoGravity()) && entity.getDeltaMovement().lengthSqr() < 1.0E-5) {
				entity.setDeltaMovement(Vec3.ZERO);
				entity.setOldPosAndRot();
				iterator.remove();
			}
		}
	}
}
