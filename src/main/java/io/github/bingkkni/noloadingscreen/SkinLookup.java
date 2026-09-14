package io.github.bingkkni.noloadingscreen;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.world.entity.player.PlayerSkin;

/** Readiness of the exact future captured by vanilla's supplier, not a second cache lookup. */
public record SkinLookup(Supplier<PlayerSkin> vanilla, CompletableFuture<Optional<PlayerSkin>> future) implements Supplier<PlayerSkin> {
	@Override
	public PlayerSkin get() {
		return this.vanilla.get();
	}

	public boolean pending() {
		return !this.future.isDone();
	}
}
