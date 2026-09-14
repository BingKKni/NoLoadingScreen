package io.github.bingkkni.noloadingscreen.mixin;

import io.github.bingkkni.noloadingscreen.platform.ClientUi;
import io.github.bingkkni.noloadingscreen.platform.LoaderServices;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.yggdrasil.ProfileResult;
import io.github.bingkkni.noloadingscreen.DisconnectedWorldView;
import io.github.bingkkni.noloadingscreen.LocalSkinPreloader;
import io.github.bingkkni.noloadingscreen.JoinClassWarmup;
import io.github.bingkkni.noloadingscreen.LoadingWaitLoop;
import io.github.bingkkni.noloadingscreen.LoadingWork;
import io.github.bingkkni.noloadingscreen.PlaceholderRegistries;
import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import io.github.bingkkni.noloadingscreen.SavingWorldView;
import io.github.bingkkni.noloadingscreen.compat.SodiumShaderWarmup;
import java.util.Optional;
import net.minecraft.world.level.gamerules.GameRules;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.Connection;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.DeltaTracker;
import io.github.bingkkni.noloadingscreen.platform.WaitFrame;

/** Minecraft 26.1 lifecycle hooks; renderFrame owns no live ticks or packet drains. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Redirect(method = "setScreenAndShow", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;renderFrame(Z)V"))
	private void nls$skipForcedFrame(final Minecraft minecraft, final boolean advanceGameTime) {
		Screen screen = ClientUi.screen(minecraft);
		if (NoLoadingScreenConfig.get().enabled && ((screen == null && (minecraft.level != null || PlaceholderWorld.active()))
			|| screen instanceof LevelLoadingScreen || screen instanceof ServerReconfigScreen)) return;
		WaitFrame.draw(minecraft, advanceGameTime);
	}
	@Shadow @Final private CompletableFuture<@Nullable ProfileResult> profileFuture;
	@Shadow private @Nullable Connection pendingConnection;
	@Unique private boolean nls$drainingTasks;
	@Unique private long nls$taskStart;
	@Unique private int nls$tasksProcessed;

	/** Resources are ready, even when quick play skips the title screen. Never join the profile future. */
	@Inject(method = "onGameLoadFinished", at = @At("HEAD"))
	private void nls$preloadSkin(final CallbackInfo ci) {
		Minecraft minecraft = (Minecraft) (Object) this;
		JoinClassWarmup.prepare();
		LocalSkinPreloader.preload(minecraft.getUser().getProfileId(), this.profileFuture, minecraft.getSkinManager(), minecraft);
		SodiumShaderWarmup.prepare();
		PlaceholderRegistries.preload();
	}

	@Shadow
	private void updateLevelInEngines(final ClientLevel level) {
		throw new AssertionError();
	}

	/**
	 * The one call that would make adopting the outgoing world expensive.
	 *
	 * <p>{@code updateLevelInEngines(null)} detaches {@code levelRenderer}, which releases every
	 * built section; re-attaching afterwards would re-mesh the entire world, which is precisely the
	 * cost this mod exists to remove. Leaving the engines pointed at the level being torn down keeps
	 * it on screen for free — {@code minecraft.level} and {@code player} are still nulled by the rest
	 * of the teardown, so ticks, packet handlers and other mods see exactly what vanilla shows them.
	 *
	 * <p>Only the sound stop is kept from the skipped call: the old world's ambience should not carry
	 * on into the next server. {@code setCameraEntity(null)} is not wanted (the camera stays on the
	 * adopted player) and {@code pendingConnection} is already null on this path.
	 */
	@Redirect(
		method = "clearClientLevel",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;updateLevelInEngines(Lnet/minecraft/client/multiplayer/ClientLevel;)V")
	)
	private void nls$keepEnginesForAdoption(final Minecraft minecraft, final ClientLevel level) {
		if (NoLoadingScreen.shouldKeepEnginesForAdoption()) {
			minecraft.getSoundManager().stop();
			return;
		}

		this.updateLevelInEngines(level);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void nls$tick(final CallbackInfo ci) {
		// A render that threw would leave the fields bound; a tick must never see them. Whatever
		// depth is left over here is a leak by definition — no frame is in progress at a tick head.
		PlaceholderWorld.releaseAll();
		NoLoadingScreen.tickPlaceholder();
	}

	@WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runAllTasks()V"))
	private void nls$measureClientTasks(final Minecraft minecraft, final Operation<Void> original) {
		long timing = LoadingWork.startTiming();
		boolean outer = !this.nls$drainingTasks;
		if (outer) {
			this.nls$drainingTasks = true;
			this.nls$taskStart = System.nanoTime();
			this.nls$tasksProcessed = 0;
		}
		try {
			original.call(minecraft);
		} finally {
			if (outer) this.nls$drainingTasks = false;
			LoadingWork.endTiming("client tasks", timing);
		}
	}

	@ModifyReturnValue(method = "shouldRun", at = @At("RETURN"))
	private boolean nls$yieldLoadingTasks(final boolean original) {
		if (!original || !this.nls$drainingTasks) return original;
		// Only the ordinary frame drain is budgeted. BlockableEventLoop's managedBlock bypasses
		// shouldRun while a task needs synchronous completion, including resource/save waits.
		if (this.nls$tasksProcessed > 0 && LoadingWork.active() && System.nanoTime() - this.nls$taskStart >= 4_000_000L) return false;
		this.nls$tasksProcessed++;
		return true;
	}

	@WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;tick()V"))
	private void nls$measureClientTick(final Minecraft minecraft, final Operation<Void> original) {
		long timing = LoadingWork.startTiming();
		try { original.call(minecraft); } finally { LoadingWork.endTiming("client tick", timing); }
	}

	@Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
	private void nls$pauseLoading(final boolean suppressPauseMenu, final CallbackInfo ci) {
		if (NoLoadingScreen.openLoadingPauseScreen()) {
			ci.cancel();
		}
	}

	@Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
	private void nls$handlePlaceholderKeybinds(final CallbackInfo ci) {
		if (PlaceholderWorld.active()) {
			// Vanilla assumes a non-null live player here. Preserve only local UI controls and drain
			// every gameplay click so nothing fires when the real world arrives.
			PlaceholderWorld.handleSafeKeybinds();
			ci.cancel();
		}
	}

	@Redirect(
		method = "runTick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;handleAccumulatedMovement()V")
	)
	private void nls$mouseLook(final MouseHandler mouseHandler) {
		// turnPlayer() is a no-op without a player, and it is the whole point of the placeholder.
		if (PlaceholderWorld.bind()) {
			try {
				mouseHandler.handleAccumulatedMovement();
			} finally {
				PlaceholderWorld.unbind();
			}
			return;
		}

		mouseHandler.handleAccumulatedMovement();
	}
	@Redirect(
		method = "doWorldLoad",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;isReady()Z")
	)
	private boolean nls$dontFreezeWhileServerBoots(final IntegratedServer server) {
		// Forge rejects early handshakes. Keep its readiness gate and pump local wait frames instead.
		return (NoLoadingScreenConfig.get().enabled && LoaderServices.allowsEarlyLocalConnection()) || server.isReady();
	}

	@WrapMethod(method = "doWorldLoad")
	private void nls$bootWaitClock(final LevelStorageAccess access, final PackRepository packs, final WorldStem stem,
		final Optional<GameRules> rules, final boolean newWorld, final Operation<Void> original) {
		if (!NoLoadingScreenConfig.get().enabled) {
			original.call(access, packs, stem, rules, newWorld);
			return;
		}
		LoadingWaitLoop.begin();
		try {
			original.call(access, packs, stem, rules, newWorld);
		} finally {
			LoadingWaitLoop.end();
		}
	}

	@Redirect(method = "doWorldLoad", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;renderFrame(Z)V"))
	private void nls$bootWaitFrame(final Minecraft minecraft, final boolean advanceGameTime) {
		if (LoadingWaitLoop.active() && PlaceholderWorld.active()) LoadingWaitLoop.frame(minecraft);
		else WaitFrame.draw(minecraft, advanceGameTime);
	}

	@WrapOperation(method = "doWorldLoad", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/Minecraft;disconnectWithProgressScreen()V"))
	private void nls$keepPreparedScene(final Minecraft minecraft, final Operation<Void> original) {
		// This is an empty-session cleanup, NOT a real disconnect. Repeating it after resource
		// preparation destroys the existing scene/camera and forces a one-frame progress screen.
		if (!(NoLoadingScreen.preparingResources() && PlaceholderWorld.active() && minecraft.level == null
			&& minecraft.player == null && minecraft.getSingleplayerServer() == null && this.pendingConnection == null)) {
			original.call(minecraft);
		}
	}

	@WrapOperation(method = "doWorldLoad", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
	private void nls$singleplayerLoadStarted(final Minecraft gui, final Screen screen, final Operation<Void> original,
		final LevelStorageAccess access, final PackRepository packs, final WorldStem stem,
		final Optional<GameRules> rules, final boolean newWorld) {
		// Capture vanilla's tracker BEFORE hiding its screen, and keep the same early scene.
		NoLoadingScreen.onSingleplayerLoadStart(stem.registries().compositeAccess(), (LevelLoadingScreen) screen);
		original.call(gui, screen);
	}

	@Inject(method = "clearClientLevel", at = @At("HEAD"))
	private void nls$clearLevelStart(final Screen screen, final CallbackInfo ci) {
		NoLoadingScreen.onLevelTornDown();
		NoLoadingScreen.markTimeline("开始拆除旧世界 (clearClientLevel)");
	}

	@Inject(method = "clearClientLevel", at = @At("RETURN"))
	private void nls$clearLevelEnd(final Screen screen, final CallbackInfo ci) {
		NoLoadingScreen.markTimeline("旧世界拆除完毕 <- 这段是客户端自己的开销");
	}

	@Inject(method = "setLevel", at = @At("HEAD"))
	private void nls$setLevelStart(final ClientLevel level, final CallbackInfo ci) {
		// Hand the render engines back before vanilla points them at the real world.
		NoLoadingScreen.onLevelTornDown();
		NoLoadingScreen.markTimeline("开始装配新世界 (setLevel；此前也可能包含客户端拆除和登录处理)");
	}

	@Inject(method = "setLevel", at = @At("RETURN"))
	private void nls$setLevelEnd(final ClientLevel level, final CallbackInfo ci) {
		NoLoadingScreen.markTimeline("新世界装配完毕 <- 这段是客户端自己的开销");
	}
}
