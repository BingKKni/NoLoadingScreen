package io.github.bingkkni.noloadingscreen.verification;

import com.mojang.blaze3d.platform.Transparency;
import io.github.bingkkni.noloadingscreen.LoadingWaitLoop;
import io.github.bingkkni.noloadingscreen.PlaceholderBlockEffects;
import io.github.bingkkni.noloadingscreen.PlaceholderInteraction;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleResources;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import sun.misc.Unsafe;

/** Real vanilla debris construction, ticking and CPU render extraction; audio is captured, not played. */
final class BlockFeedbackVerification {
	private static int assertions;
	private static TextureAtlasSprite sprite;

	static void prepare(final Minecraft minecraft, final ClientLevel level) throws ReflectiveOperationException {
		set(Minecraft.class, minecraft, "gameThread", Thread.currentThread());
		SpriteContents contents = allocate(SpriteContents.class);
		set(SpriteContents.class, contents, "transparency", Transparency.NONE);
		sprite = allocate(TextureAtlasSprite.class);
		set(TextureAtlasSprite.class, sprite, "contents", contents);
		set(TextureAtlasSprite.class, sprite, "atlasLocation", TextureAtlas.LOCATION_BLOCKS);
		set(TextureAtlasSprite.class, sprite, "u1", 1.0F);
		set(TextureAtlasSprite.class, sprite, "v1", 1.0F);
		BlockStateModel model = new BlockStateModel() {
			@Override public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {}
			@Override public Material.Baked particleMaterial() { return new Material.Baked(sprite, false); }
			@Override public int materialFlags() { return 0; }
		};
		ModelManager models = allocate(ModelManager.class);
		set(ModelManager.class, models, "blockStateModelSet", new BlockStateModelSet(Map.of(), model));
		set(Minecraft.class, minecraft, "modelManager", models);
		BlockColors colors = new BlockColors();
		colors.register(List.of(state -> 0x4080FF), Blocks.OAK_LEAVES);
		set(Minecraft.class, minecraft, "blockColors", colors);
		set(ClientLevel.class, level, "minecraft", minecraft);
		set(Minecraft.class, minecraft, "particleEngine", new ParticleEngine(level, allocate(ParticleResources.class)));
		CapturingSoundManager audio = allocate(CapturingSoundManager.class);
		audio.played = new ArrayList<>();
		audio.active = new HashSet<>();
		set(Minecraft.class, minecraft, "soundManager", audio);
	}

	static void run(final Minecraft minecraft, final LocalPlayer player, final LoadingVisualVerification.EmptyLevel level)
		throws ReflectiveOperationException {
		CapturingSoundManager audio = (CapturingSoundManager) minecraft.getSoundManager();
		ParticleEngine engine = minecraft.particleEngine;
		engine.clearParticles();
		BlockPos pos = new BlockPos(3, 80, 3);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
		check(PlaceholderWorld.bind(), "Feedback uses the owned disposable binding");
		try {
			for (BlockState state : List.of(Blocks.STONE.defaultBlockState(), Blocks.GLASS.defaultBlockState(),
				Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_SLAB.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState())) {
				PlaceholderBlockEffects.clear();
				audio.played.clear();
				level.blocks.put(pos, state);
				check(PlaceholderInteraction.breakBlock(player, hit), "Break succeeds locally for " + state);
				check(audio.played.size() == 1, "Exactly one break sound per successful edit");
				SoundInstance sound = audio.played.getFirst();
				check(sound.getIdentifier().equals(state.getSoundType().getBreakSound().location()) && sound.getSource() == SoundSource.BLOCKS,
					"Vanilla material sound and blocks-volume category are retained");
				// Inspect the base parameters: this fixture does not resolve/download audio assets.
				check((float) get(AbstractSoundInstance.class, sound, "volume") == (state.getSoundType().getVolume() + 1) / 2
					&& (float) get(AbstractSoundInstance.class, sound, "pitch") == state.getSoundType().getPitch() * .8F,
					"Vanilla break volume and pitch are retained");
				check(sound.getX() == 3.5 && sound.getY() == 80.5 && sound.getZ() == 3.5 && !sound.isLooping() && !sound.isRelative(),
					"Break audio is a one-shot positioned at the block centre");
				check(debris().size() == (state.is(Blocks.OAK_SLAB) ? 32 : 64), "Real vanilla shape-dependent debris density");
				Particle particle = debris().peek();
				check(particle instanceof TerrainParticle && get(SingleQuadParticle.class, particle, "sprite") == sprite,
					"Debris uses vanilla TerrainParticle and the block model's particle sprite");
				check((float) get(Particle.class, particle, "gravity") == 1 && particle.getLifetime() >= 4 && particle.getLifetime() <= 40,
					"Vanilla debris gravity and finite lifetime are retained");
				if (state.is(Blocks.OAK_LEAVES)) {
					check(Math.abs((float) get(SingleQuadParticle.class, particle, "rCol") - .6F * 64 / 255) < .00001F,
						"Tinted block debris uses the vanilla block-colour provider");
				}
				check(pending(engine).isEmpty(), "New local debris is not parked in the frozen global pending queue");
				check(!PlaceholderInteraction.breakBlock(player, hit) && audio.played.size() == 1, "Breaking air produces no duplicate feedback");
			}

			PlaceholderBlockEffects.clear();
			audio.played.clear();
			BlockState wetSlab = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
			level.blocks.put(pos, wetSlab);
			check(PlaceholderInteraction.breakBlock(player, hit) && level.getFluidState(pos).is(Fluids.WATER) && debris().size() == 32,
				"Waterlogged break preserves water while feedback uses the removed solid state");
			PlaceholderBlockEffects.clear();
			level.blocks.put(pos, Blocks.BARRIER.defaultBlockState());
			PlaceholderWorld.setLocalMode(net.minecraft.world.level.GameType.CREATIVE);
			check(PlaceholderInteraction.breakBlock(player, hit) && group() == null, "Blocks suppressing terrain particles retain vanilla suppression");
			PlaceholderWorld.setLocalMode(net.minecraft.world.level.GameType.SURVIVAL);

			int soundsBefore = audio.played.size();
			level.blocks.put(pos, Blocks.STONE.defaultBlockState());
			level.rejectEdits = true;
			check(!PlaceholderInteraction.breakBlock(player, hit), "Rejected client edits do not pretend to break");
			level.rejectEdits = false;
			level.chunksMissing = true;
			check(!PlaceholderInteraction.breakBlock(player, hit), "Unloaded blocks cannot create feedback");
			level.chunksMissing = false;
			check(!PlaceholderInteraction.breakBlock(player, BlockHitResult.miss(Vec3.ZERO, Direction.UP, pos)), "Missed attacks create no feedback");
			check(!PlaceholderInteraction.breakBlock(player, new BlockHitResult(Vec3.ZERO, Direction.UP, new BlockPos(0, 1000, 0), false)),
				"Out-of-height attacks create no feedback");
			check(!PlaceholderInteraction.breakBlock(allocate(LocalPlayer.class), hit), "Another player's actions cannot enter the local effect scope");
			check(audio.played.size() == soundsBefore && group() == null, "All invalid edits are silent and particle-free");

			verifyAnimationAndExtraction(minecraft, player, level, pos, hit, audio);
			verifyFailureAndCleanup(minecraft, player, level, pos, hit, audio);
			// Leave effects alive for the existing inventory/login teardown regression to clean.
			level.blocks.put(pos, Blocks.STONE.defaultBlockState());
			check(PlaceholderInteraction.breakBlock(player, hit) && !debris().isEmpty(), "Seed effects for the actual login/uninstall test");
		} finally {
			level.rejectEdits = false;
			level.chunksMissing = false;
			PlaceholderWorld.unbind();
		}
		int count = audio.played.size();
		check(!PlaceholderInteraction.breakBlock(player, hit) && audio.played.size() == count, "Unbound edits cannot play sounds");
		System.out.println("BlockFeedbackVerification: " + assertions + " assertions passed (real vanilla debris, material audio, local clocks, frozen old particles, extraction and cleanup).");
	}

	private static void verifyAnimationAndExtraction(final Minecraft minecraft, final LocalPlayer player,
		final LoadingVisualVerification.EmptyLevel level, final BlockPos pos, final BlockHitResult hit, final CapturingSoundManager audio)
		throws ReflectiveOperationException {
		ParticleEngine engine = minecraft.particleEngine;
		engine.clearParticles();
		TerrainParticle ambient = new TerrainParticle(level, 0, 80, 0, 0, 0, 0, Blocks.STONE.defaultBlockState(), pos);
		engine.add(ambient);
		check(pending(engine).contains(ambient), "Unscoped particle additions still enter vanilla's queue");
		engine.tick(); // install the old-world particle before our local ticks; never called by the feature
		level.blocks.put(pos, Blocks.STONE.defaultBlockState());
		PlaceholderInteraction.breakBlock(player, hit);
		Particle local = debris().peek();
		for (Particle p : debris()) p.setLifetime(4);
		int audioTicks = audio.ticks;
		set(net.minecraft.client.gui.Gui.class, minecraft.gui, "screen", allocate(net.minecraft.client.gui.screens.ChatScreen.class));
		try {
			PlaceholderWorld.tick();
		} finally {
			set(net.minecraft.client.gui.Gui.class, minecraft.gui, "screen", null);
		}
		check((int) get(Particle.class, local, "age") == 1 && (int) get(Particle.class, ambient, "age") == 0,
			"Local 20 Hz tick advances new debris even in menus, not the outgoing particle engine");
		check(audio.ticks == audioTicks, "Normal transfer loop does not double-tick audio");
		check(!get(Particle.class, local, "y").equals(get(Particle.class, local, "yo")), "Debris actually moves instead of freezing at its origin");

		LoadingWaitLoop.begin();
		ParticlesRenderState rendered = new ParticlesRenderState();
		try {
			PlaceholderWorld.tick();
			check(audio.ticks == audioTicks + 1 && (int) get(Particle.class, local, "age") == 2,
				"Synchronous saving maintains audio channels and local particles without Minecraft.tick");
			DeltaTracker.Timer clock = (DeltaTracker.Timer) LoadingWaitLoop.renderTime(DeltaTracker.ZERO);
			clock.advanceGameTime((long) get(DeltaTracker.Timer.class, clock, "lastMs") + 75);
			PlaceholderWorld.prepareRender(DeltaTracker.ZERO);
			check(PlaceholderWorld.localPartialTick(1) == .5F, "Debris receives the independent save clock, not the frozen-world alpha");
			Frustum frustum = new Frustum(new Matrix4f(), new Matrix4f()) {
				@Override public boolean pointInFrustum(double x, double y, double z) { return true; }
			};
			// Missing chunks use vanilla's full-bright particle fallback; no light engine/GPU needed.
			level.chunksMissing = true;
			Camera camera = minecraft.gameRenderer.mainCamera();
			// Debris is appended at the extractor's call site, after the engine's own extraction, so
			// an optimizer returning early from an empty engine cannot drop it.
			java.lang.reflect.Method extract = java.util.Arrays.stream(net.minecraft.client.renderer.extract.LevelExtractor.class.getDeclaredMethods())
				.filter(m -> m.getName().contains("nls$extractLocalDebris") && m.getParameterCount() == 6
					&& m.getParameterTypes()[5] == com.llamalad7.mixinextras.injector.wrapoperation.Operation.class).findFirst().orElseThrow();
			extract.setAccessible(true);
			java.util.List<Object> forwarded = new ArrayList<>();
			com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original = args -> {
				forwarded.addAll(java.util.Arrays.asList(args));
				((ParticleEngine) args[0]).extract((ParticlesRenderState) args[1], (Frustum) args[2], (Camera) args[3], (float) args[4]);
				return null;
			};
			extract.invoke(allocate(net.minecraft.client.renderer.extract.LevelExtractor.class), engine, rendered, frustum, camera, 1.0F, original);
			check(forwarded.equals(java.util.List.of(engine, rendered, frustum, camera, 1.0F)), "Vanilla particle extraction keeps its exact receiver and arguments");
			check(rendered.particles.size() == 2 && audio.camera == camera, "Actual extraction appends local debris and updates the save-time audio listener");
			QuadParticleRenderState quads = (QuadParticleRenderState) rendered.particles.getLast();
			Map<?, ?> layers = (Map<?, ?>) get(QuadParticleRenderState.class, quads, "particles");
			Object storage = layers.values().iterator().next();
			float[] values = (float[]) get(storage.getClass(), storage, "floatValues");
			float expectedX = (float) (Mth.lerp(.5, (double) get(Particle.class, local, "xo"), (double) get(Particle.class, local, "x")) - camera.position().x);
			check(Math.abs(values[0] - expectedX) < .0001F && (int) get(QuadParticleRenderState.class, quads, "particleCount") == 64,
				"Real extracted vertices interpolate debris at live alpha and retain every fragment");
			check((int) get(Particle.class, local, "age") == 2, "Rendering never advances particle simulation");
			rendered.reset();
			check(quads.isEmpty(), "Normal frame reset clears the appended render state, preventing duplicate trails");
		} finally {
			rendered.reset();
			level.chunksMissing = false;
			LoadingWaitLoop.end();
		}
		for (int i = 0; i < 4; i++) PlaceholderWorld.tick();
		check(group().isEmpty() && !local.isAlive() && ambient.isAlive() && (int) get(Particle.class, ambient, "age") == 0,
			"Debris ages out and is removed while pre-existing particles remain frozen");
	}

	private static void verifyFailureAndCleanup(final Minecraft minecraft, final LocalPlayer player,
		final LoadingVisualVerification.EmptyLevel level, final BlockPos pos, final BlockHitResult hit, final CapturingSoundManager audio)
		throws ReflectiveOperationException {
		level.blocks.put(pos, Blocks.STONE.defaultBlockState());
		PlaceholderInteraction.breakBlock(player, hit);
		SoundInstance owned = audio.played.getLast();
		new ParticleEngine(level, allocate(ParticleResources.class)).clearParticles();
		check(group() != null && audio.isActive(owned), "Clearing an unrelated particle engine cannot delete local effects");
		minecraft.particleEngine.clearParticles();
		assertCleared();
		check(!audio.isActive(owned), "Resource reload/world clearing stops owned sounds as well as debris");

		ModelManager models = minecraft.getModelManager();
		set(Minecraft.class, minecraft, "modelManager", null);
		level.blocks.put(pos, Blocks.STONE.defaultBlockState());
		try {
			PlaceholderInteraction.breakBlock(player, hit);
			throw new AssertionError("Injected particle-resource failure must propagate to the existing cosmetic fallback boundary");
		} catch (NullPointerException expected) {
			check(!(boolean) get(PlaceholderBlockEffects.class, null, "collecting"), "Failed vanilla particle construction always releases the capture scope");
		} finally {
			set(Minecraft.class, minecraft, "modelManager", models);
			PlaceholderBlockEffects.clear();
		}
		PlaceholderWorld.setLocalMode(net.minecraft.world.level.GameType.CREATIVE);
		for (int i = 0; i < 70; i++) {
			level.blocks.put(pos, Blocks.BARRIER.defaultBlockState()); // sound-only, no unnecessary particles
			PlaceholderInteraction.breakBlock(player, hit);
		}
		check(audio.active.size() == 64, "Long held attacks bound owned sound instances and stop the oldest voices");
		PlaceholderBlockEffects.clear();
		check(audio.active.isEmpty(), "Ending the effect scope stops every remaining owned voice");
		PlaceholderWorld.setLocalMode(net.minecraft.world.level.GameType.SURVIVAL);
	}

	static void seedSavingFeedback(final LocalPlayer player, final ClientLevel level) {
		BlockPos pos = new BlockPos(3, 80, 3);
		level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
		check(PlaceholderInteraction.breakBlock(player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)),
			"Actual saving binding supports break feedback before final teardown");
	}

	static void assertCleared() throws ReflectiveOperationException {
		check(get(PlaceholderBlockEffects.class, null, "level") == null && get(PlaceholderBlockEffects.class, null, "engine") == null
			&& get(PlaceholderBlockEffects.class, null, "soundManager") == null && group() == null
			&& ((Queue<?>) get(PlaceholderBlockEffects.class, null, "sounds")).isEmpty()
			&& !(boolean) get(PlaceholderBlockEffects.class, null, "collecting"),
			"Teardown releases effects, pending sounds, capture scope and all outgoing level/engine references");
	}

	private static QuadParticleGroup group() throws ReflectiveOperationException {
		return (QuadParticleGroup) get(PlaceholderBlockEffects.class, null, "debris");
	}

	@SuppressWarnings("unchecked")
	private static Queue<Particle> debris() throws ReflectiveOperationException {
		return (Queue<Particle>) get(ParticleGroup.class, group(), "particles");
	}

	@SuppressWarnings("unchecked")
	private static Queue<Particle> pending(final ParticleEngine engine) throws ReflectiveOperationException {
		return (Queue<Particle>) get(ParticleEngine.class, engine, "particlesToAdd");
	}

	private static final class CapturingSoundManager extends SoundManager {
		List<SoundInstance> played;
		Set<SoundInstance> active;
		int ticks;
		Camera camera;
		private CapturingSoundManager() { super(null); }
		@Override public SoundEngine.PlayResult play(SoundInstance sound) { this.played.add(sound); this.active.add(sound); return SoundEngine.PlayResult.STARTED; }
		@Override public void stop(SoundInstance sound) { this.active.remove(sound); }
		@Override public boolean isActive(SoundInstance sound) { return this.active.contains(sound); }
		@Override public void tick(boolean paused) { this.ticks++; }
		@Override public void updateSource(Camera camera) { this.camera = camera; }
	}

	private static Object get(final Class<?> type, final Object target, final String name) throws ReflectiveOperationException {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	private static void set(final Class<?> type, final Object target, final String name, final Object value) throws ReflectiveOperationException {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static <T> T allocate(final Class<T> type) throws ReflectiveOperationException {
		Field field = Unsafe.class.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
	}

	private static void check(final boolean condition, final String message) {
		assertions++;
		if (!condition) throw new AssertionError(message);
	}
}
