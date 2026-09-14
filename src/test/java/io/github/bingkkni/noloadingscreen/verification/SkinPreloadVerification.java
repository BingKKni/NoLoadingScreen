package io.github.bingkkni.noloadingscreen.verification;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.yggdrasil.ProfileResult;
import io.github.bingkkni.noloadingscreen.LocalSkinPreloader;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** Deterministic futures and a fake texture loader: no HTTP, files, sleeps or GPU. */
final class SkinPreloadVerification {
	private static final UUID PLAYER_ID = new UUID(0, 1);
	private static final UUID OTHER_ID = new UUID(0, 2);
	private static int assertions;

	static void run() {
		check(LocalSkinPreloader.get(PLAYER_ID) == null, "No skin before startup");
		verifyAsyncPipeline();
		verifyFallbacks();
		verifyReplacement();
		System.out.println("SkinPreloadVerification: " + assertions + " assertions passed (async profile/texture readiness, fallback, retention and account isolation).");
	}

	private static void verifyAsyncPipeline() {
		CompletableFuture<ProfileResult> profile = new CompletableFuture<>();
		CompletableFuture<Optional<PlayerSkin>> texture = new CompletableFuture<>();
		TestSkinManager manager = new TestSkinManager(texture);
		ArrayDeque<Runnable> clientTasks = new ArrayDeque<>();
		LocalSkinPreloader.preload(PLAYER_ID, profile, manager, clientTasks::add);
		check(LocalSkinPreloader.get(PLAYER_ID) == null && manager.requests == 0 && clientTasks.isEmpty(),
			"An unresolved profile cannot block or start texture loading");
		Property textures = new Property("textures", "test-texture-metadata");
		GameProfile completeProfile = new GameProfile(PLAYER_ID, "SkinTest",
			new PropertyMap(ImmutableMultimap.of("textures", textures)));
		profile.complete(new ProfileResult(completeProfile));
		check(manager.requests == 0 && clientTasks.size() == 1, "Profile completion schedules skin lookup on the client executor");
		clientTasks.removeFirst().run();
		check(manager.requests == 1 && manager.profile == completeProfile, "Pass the full original profile, including texture properties");
		check(LocalSkinPreloader.get(PLAYER_ID) == null, "Texture registration can still be pending after profile completion");
		PlayerSkin readySkin = skin("preloaded", PlayerModelType.SLIM);
		texture.complete(Optional.of(readySkin));
		for (int i = 0; i < 100; i++) {
			check(LocalSkinPreloader.get(PLAYER_ID) == readySkin, "Retain the same completed skin without another cache lookup");
		}
		check(manager.requests == 1, "Rendering does not poll SkinManager or start duplicate requests");
		check(LocalSkinPreloader.get(OTHER_ID) == null, "Never return a skin for another account");
	}

	private static void verifyFallbacks() {
		TestSkinManager manager = new TestSkinManager(CompletableFuture.completedFuture(Optional.empty()));
		LocalSkinPreloader.preload(PLAYER_ID, CompletableFuture.completedFuture(null), manager, Runnable::run);
		check(LocalSkinPreloader.get(PLAYER_ID) == null && manager.requests == 0, "Missing/offline profile leaves vanilla fallback intact");
		LocalSkinPreloader.preload(PLAYER_ID, CompletableFuture.failedFuture(new IllegalStateException("Expected profile failure")), manager, Runnable::run);
		check(LocalSkinPreloader.get(PLAYER_ID) == null && manager.requests == 0, "Profile failure cannot escape to rendering");
		CompletableFuture<ProfileResult> cancelled = new CompletableFuture<>();
		LocalSkinPreloader.preload(PLAYER_ID, cancelled, manager, Runnable::run);
		cancelled.cancel(false);
		check(LocalSkinPreloader.get(PLAYER_ID) == null, "Cancelled profile cannot escape to rendering");
		preload(PLAYER_ID, CompletableFuture.completedFuture(Optional.empty()));
		check(LocalSkinPreloader.get(PLAYER_ID) == null, "Vanilla's empty texture result uses vanilla fallback");
		preload(PLAYER_ID, CompletableFuture.failedFuture(new IllegalStateException("Expected texture failure")));
		check(LocalSkinPreloader.get(PLAYER_ID) == null, "Failed texture future cannot escape to rendering");
		LocalSkinPreloader.preload(PLAYER_ID, CompletableFuture.completedFuture(new ProfileResult(new GameProfile(OTHER_ID, "Other"))), manager, Runnable::run);
		check(LocalSkinPreloader.get(PLAYER_ID) == null && manager.requests == 0, "Mismatched resolved profile must not load or expose another skin");
	}

	private static void verifyReplacement() {
		CompletableFuture<Optional<PlayerSkin>> oldTexture = new CompletableFuture<>();
		preload(PLAYER_ID, oldTexture);
		PlayerSkin replacement = skin("replacement", PlayerModelType.WIDE);
		preload(OTHER_ID, CompletableFuture.completedFuture(Optional.of(replacement)));
		oldTexture.complete(Optional.of(skin("old", PlayerModelType.SLIM)));
		check(LocalSkinPreloader.get(OTHER_ID) == replacement && LocalSkinPreloader.get(PLAYER_ID) == null,
			"Late completion from an old account cannot overwrite the current lookup");
	}

	static void preload(final UUID playerId, final CompletableFuture<Optional<PlayerSkin>> texture) {
		LocalSkinPreloader.preload(playerId, CompletableFuture.completedFuture(new ProfileResult(new GameProfile(playerId, "SkinTest"))),
			new TestSkinManager(texture), Runnable::run);
	}

	static PlayerSkin skin(final String name, final PlayerModelType model) {
		return new PlayerSkin(texture(name), texture(name + "_cape"), texture(name + "_elytra"), model, false);
	}

	private static ClientAsset.Texture texture(final String name) {
		return SkinTestTexture.texture(name);
	}

	static final class TestSkinManager extends SkinManager {
		CompletableFuture<Optional<PlayerSkin>> texture;
		int requests;
		private GameProfile profile;

		TestSkinManager(final CompletableFuture<Optional<PlayerSkin>> texture) {
			super(Path.of("."), null, null, Runnable::run);
			this.texture = texture;
		}

		@Override
		public CompletableFuture<Optional<PlayerSkin>> get(final GameProfile profile) {
			this.requests++;
			this.profile = profile;
			return this.texture;
		}
	}

	private static void check(final boolean condition, final String message) {
		assertions++;
		if (!condition) throw new AssertionError(message);
	}
}
