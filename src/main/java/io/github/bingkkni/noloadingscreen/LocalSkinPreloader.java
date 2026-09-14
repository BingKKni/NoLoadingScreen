package io.github.bingkkni.noloadingscreen;

import com.mojang.authlib.yggdrasil.ProfileResult;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

/** Client-thread entry points; vanilla owns all texture IO, processing and registration. */
public final class LocalSkinPreloader {
	private static @Nullable UUID profileId;
	// Retain the future across joins and menus: SkinManager's profile cache expires after 15s.
	private static CompletableFuture<Optional<PlayerSkin>> skin = CompletableFuture.completedFuture(Optional.empty());

	private LocalSkinPreloader() {
	}

	public static void preload(
		final UUID playerId,
		final CompletableFuture<@Nullable ProfileResult> profileFuture,
		final SkinManager skinManager,
		final Executor clientExecutor
	) {
		profileId = playerId;
		skin = profileFuture.thenComposeAsync(result -> {
			if (result == null || !playerId.equals(result.profile().id())) {
				return CompletableFuture.<Optional<PlayerSkin>>completedFuture(Optional.empty());
			}
			return skinManager.get(result.profile());
		}, clientExecutor).exceptionally(failure -> {
			NoLoadingScreen.LOGGER.warn("Could not preload the local player skin; keeping vanilla fallback", failure);
			return Optional.empty();
		});
	}

	public static boolean isLocalProfile(final UUID playerId) {
		return playerId.equals(profileId);
	}

	public static @Nullable PlayerSkin get(final UUID playerId) {
		return playerId.equals(profileId) ? skin.getNow(Optional.empty()).orElse(null) : null;
	}
}
