package io.github.bingkkni.noloadingscreen.platform;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** 26.1 releases the chunk gate within vanilla's readiness predicate, not via a callback. */
public final class LevelAccess {
	private LevelAccess() {}
	public static void release(LevelLoadTracker tracker) {
		// WaitingForPlayerChunkMixin releases the gate in the immediately following vanilla tick.
	}
	public static boolean hasChunk(ClientLevel level, ChunkPos pos) { return level.hasChunk(pos.x(), pos.z()); }
	public static boolean hasChunk(ClientLevel level, Vec3 pos) { return hasChunk(level, ChunkPos.containing(BlockPos.containing(pos))); }
}
