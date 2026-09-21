package io.github.bingkkni.noloadingscreen;

import io.github.bingkkni.noloadingscreen.compat.SodiumCompatibility;

/** Resolve first-join types on the startup thread, without class initializers or world instances. */
public final class JoinClassWarmup {
	private static boolean attempted;
	private static final String[] VANILLA = {
		"net.minecraft.network.Connection",
		"net.minecraft.client.multiplayer.ClientPacketListener",
		"net.minecraft.client.multiplayer.ClientLevel",
		"net.minecraft.client.multiplayer.ClientChunkCache",
		"net.minecraft.client.multiplayer.MultiPlayerGameMode",
		"net.minecraft.client.player.LocalPlayer",
		"net.minecraft.world.level.chunk.PalettedContainerFactory",
		"net.minecraft.world.level.chunk.PalettedContainer",
		"net.minecraft.world.level.chunk.LevelChunkSection",
		"net.minecraft.world.level.chunk.LevelChunk",
		"net.minecraft.world.level.chunk.EmptyLevelChunk",
		"net.minecraft.world.level.entity.EntitySectionStorage",
		"net.minecraft.world.level.entity.TransientEntitySectionManager",
		"net.minecraft.network.syncher.SynchedEntityData",
	};
	private static final String[] VANILLA_TAIL = {
		"net.minecraft.stats.RecipeBookSettings",
		"net.minecraft.client.ClientRecipeBook",
		"net.minecraft.world.level.border.WorldBorder",
		"it.unimi.dsi.fastutil.longs.LongAVLTreeSet",
		"it.unimi.dsi.fastutil.longs.LongSets"
	};
	private static final String[] SODIUM = {
		"net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager",
		"net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext",
		"net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderCache",
		io.github.bingkkni.noloadingscreen.platform.LoaderServices.sodiumFluidRenderer(),
		"net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.SortTriggering",
		"net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.DirectTriggers",
		"net.caffeinemc.mods.sodium.client.services.FluidRendererFactory"
	};

	private JoinClassWarmup() {}

	public static void prepare() {
		if (attempted || !NoLoadingScreenConfig.get().enabled) return;
		attempted = true;
		long start = System.nanoTime();
		resolveAll(VANILLA);
		resolveAll(io.github.bingkkni.noloadingscreen.platform.EnvironmentWarmup.types());
		resolveAll(VANILLA_TAIL);
		if (SodiumCompatibility.supported()) resolveAll(SODIUM);
		NoLoadingScreen.LOGGER.info("Prepared first-join class definitions in {} ms before world entry", (System.nanoTime() - start) / 1_000_000L);
	}

	private static void resolveAll(final String[] names) {
		for (String name : names) {
			try {
				resolve(name);
			} catch (ClassNotFoundException | LinkageError failure) {
				NoLoadingScreen.LOGGER.warn("Could not prepare first-join type {}; leaving normal first use intact", name, failure);
			}
		}
	}

	static void resolve(final String name) throws ClassNotFoundException {
		Class<?> type = Class.forName(name, false, JoinClassWarmup.class.getClassLoader());
		resolveSignatures(type);
		for (Class<?> nested : type.getDeclaredClasses()) resolveSignatures(nested);
	}

	private static void resolveSignatures(final Class<?> type) {
		// Signatures resolve related packet/entity types and their Mixins without executing code.
		type.getDeclaredConstructors();
		type.getDeclaredMethods();
		type.getDeclaredFields();
	}
}
