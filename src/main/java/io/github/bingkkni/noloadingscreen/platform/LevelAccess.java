package io.github.bingkkni.noloadingscreen.platform;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import io.github.bingkkni.noloadingscreen.NoLoadingScreen;

/** Build-selected chunk and vanilla readiness access. */
public final class LevelAccess {
	private LevelAccess() {}
	public static void release(LevelLoadTracker tracker) {
		Runnable callback = tracker.getPlayerCompiledSectionCallback();
		if (callback != null) {
			callback.run();
			NoLoadingScreen.onGateReleased();
		}
	}
	public static boolean hasChunk(ClientLevel level, ChunkPos pos) { return level.hasChunk(pos.x(), pos.z()); }
	public static boolean hasChunk(ClientLevel level, Vec3 pos) { return hasChunk(level, ChunkPos.containing(BlockPos.containing(pos))); }
}
