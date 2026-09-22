package io.github.bingkkni.noloadingscreen;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.culling.Frustum;
import java.util.function.Consumer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Disposable local feedback. Only newly generated effects move; the outgoing particle engine stays frozen. */
public final class PlaceholderBlockEffects {
	private static final int MAX_SOUNDS = 64;
	private static final Deque<SoundInstance> sounds = new ArrayDeque<>();
	private static @Nullable ClientLevel level;
	private static @Nullable ParticleEngine engine;
	private static @Nullable QuadParticleGroup debris;
	private static @Nullable SoundManager soundManager;
	private static boolean collecting;

	private PlaceholderBlockEffects() {}

	/** Vanilla's held-mining cadence, without entering the packet/prediction path. */
	public static void hit(final ClientLevel world, final BlockPos pos, final BlockState state) {
		if (!prepare(world)) return;
		var type = state.getSoundType();
		track(new SimpleSoundInstance(type.getHitSound(), SoundSource.BLOCKS,
			(type.getVolume() + 1.0F) / 8.0F, type.getPitch() * 0.5F, SoundInstance.createUnseededRandom(), pos));
	}

	/** Called only after a successful client-local block edit, with the removed (not air) state. */
	public static void destroy(final ClientLevel world, final BlockPos pos, final BlockState state) {
		if (!prepare(world)) return;

		// Same position, category, volume and pitch as vanilla's destroy-block level event (2001).
		// Keep the instance so ending a save/transfer cannot carry its sound into the next screen.
		var type = state.getSoundType();
		track(new SimpleSoundInstance(type.getBreakSound(), SoundSource.BLOCKS,
			(type.getVolume() + 1.0F) / 2.0F, type.getPitch() * 0.8F, SoundInstance.createUnseededRandom(), pos));

		// Let vanilla choose shape, density, particle material, tint and initial velocity. Capture
		// only this call's terrain particles, not server/ambient emitters or unrelated mod effects.
		collecting = true;
		try {
			world.addDestroyBlockEffect(pos, state);
		} finally {
			collecting = false;
		}
	}

	/** Plays the normal transition sound and keeps its splash particles on the live local clock. */
	public static void splash(final Entity subject) {
		if (!(subject.level() instanceof ClientLevel world) || !prepare(world)) return;
		var access = (io.github.bingkkni.noloadingscreen.mixin.EntityAccessor) subject;
		collecting = true;
		try {
			access.nls$doWaterSplashEffect();
		} finally {
			collecting = false;
		}
		// ClientLevel only accepts Entity.playSound when its "except" entity is the local player.
		// Retained mobs and remote players therefore need the same splash played locally here.
		if (subject != Minecraft.getInstance().player) {
			var motion = subject.getDeltaMovement();
			float speed = Math.min(1.0F, (float) Math.sqrt(motion.x * motion.x * 0.2
				+ motion.y * motion.y + motion.z * motion.z * 0.2) * 0.2F);
			SoundEvent sound = speed < 0.25F ? access.nls$swimSplashSound() : access.nls$swimHighSpeedSplashSound();
			play(subject, sound, speed, 1.0F + (subject.getRandom().nextFloat() - subject.getRandom().nextFloat()) * 0.4F);
		}
	}

	/** Client-local entity sound; unlike Entity.playSound this also works for retained remote entities. */
	public static void play(final Entity subject, final SoundEvent sound, final float volume, final float pitch) {
		if (!(subject.level() instanceof ClientLevel world) || !prepare(world) || subject.isSilent()) return;
		track(new SimpleSoundInstance(sound, subject.getSoundSource(), volume, pitch,
			SoundInstance.createUnseededRandom(), subject.blockPosition()));
	}

	/** Emits vanilla's death poof before the disposable snapshot entity is removed. */
	public static void death(final LivingEntity subject) {
		if (!(subject.level() instanceof ClientLevel world) || !prepare(world)) return;
		collecting = true;
		try {
			subject.makePoofParticles();
		} finally {
			collecting = false;
		}
	}

	/** ParticleEngine.add's scoped hook; outside local feedback calls the vanilla chain is untouched. */
	public static boolean capture(final ParticleEngine target, final Particle particle) {
		if (!collecting || target != engine || !bound() || !(particle instanceof SingleQuadParticle)) return false;
		if (debris == null) debris = new QuadParticleGroup(target, ParticleRenderType.SINGLE_QUADS);
		debris.add(particle); // vanilla group capacity/reservoir bounds local effect growth
		return true;
	}

	public static void tick() {
		if (!bound()) return;
		if (debris != null) debris.tickParticles();
		// Saving/resource waits do not run Minecraft.tick. Maintain audio only, including completed
		// channel cleanup; outgoing entity/ambient sounds were already stopped during adoption.
		if (LoadingWaitLoop.active()) soundManager.tick(false);
		sounds.removeIf(sound -> !soundManager.isActive(sound));
	}

	public static void extract(final ParticleEngine target, final Camera camera, final Consumer<QuadParticleGroup> output) {
		if (target != engine || !bound()) return;
		if (LoadingWaitLoop.active()) soundManager.updateSource(camera);
		if (debris != null && !debris.isEmpty()) {
			output.accept(debris);
		}
	}

	/** Also called by vanilla particle clearing (world changes and resource-pack reloads). */
	public static void clearFor(final ParticleEngine target) {
		if (target == engine) clear();
	}

	public static void clear() {
		collecting = false;
		debris = null;
		level = null;
		engine = null;
		try {
			if (soundManager != null) for (SoundInstance sound : sounds) soundManager.stop(sound);
		} finally {
			sounds.clear();
			soundManager = null;
		}
	}

	private static void track(final SoundInstance sound) {
		if (sounds.size() >= MAX_SOUNDS) soundManager.stop(sounds.removeFirst());
		sounds.addLast(sound);
		soundManager.play(sound);
	}

	private static boolean prepare(final ClientLevel world) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!minecraft.isSameThread() || minecraft.level != world || !PlaceholderWorld.owns(minecraft.player)) return false;
		if (level != world || engine != minecraft.particleEngine) {
			clear();
			level = world;
			engine = minecraft.particleEngine;
			soundManager = minecraft.getSoundManager();
		}
		return true;
	}

	private static boolean bound() {
		Minecraft minecraft = Minecraft.getInstance();
		return level != null && minecraft.isSameThread() && minecraft.level == level && PlaceholderWorld.owns(minecraft.player);
	}
}
