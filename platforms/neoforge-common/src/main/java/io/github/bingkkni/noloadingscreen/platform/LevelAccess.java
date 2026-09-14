package io.github.bingkkni.noloadingscreen.platform;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** Mapped 1.21 uses a readiness predicate instead of 26.2's compiled-section callback. */
public final class LevelAccess {
	private LevelAccess() {}
	public static void release(LevelLoadTracker tracker) {
		// WaitingForPlayerChunkMixin owns the decision inside vanilla's immediately following tick.
	}
	public static boolean hasChunk(ClientLevel level, ChunkPos pos) { return level.hasChunk(pos.x, pos.z); }
	public static boolean hasChunk(ClientLevel level, Vec3 pos) { return hasChunk(level, new ChunkPos(BlockPos.containing(pos))); }
}
