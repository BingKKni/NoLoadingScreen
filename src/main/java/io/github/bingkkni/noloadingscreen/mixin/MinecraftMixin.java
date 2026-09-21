package io.github.bingkkni.noloadingscreen.mixin;

import io.github.bingkkni.noloadingscreen.platform.ClientUi;
import io.github.bingkkni.noloadingscreen.platform.FrameSurface;
import io.github.bingkkni.noloadingscreen.platform.LoaderServices;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.Connection;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.gamerules.GameRules;
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

/**
 * Where the placeholder world is wired into the client loop, plus the one change that makes a
 * singleplayer join interactive at all.
 *
 * <h2>The binding strategy</h2>
 *
 * <p>{@code minecraft.level} / {@code player} / {@code gameMode} are handed the placeholder only
 * around the calls that need a camera to point at — the render frame and mouse look — and taken
 * back immediately after. {@code Minecraft#tick} therefore still runs with the null level vanilla
 * would have there, which is what keeps every packet handler, every {@code ClientTickEvents}
 * listener and every other mod on the vanilla code path for the whole wait.
 *
 * <p>The one thing in {@code tick()} that assumes otherwise is {@code handleKeybinds}: vanilla only
 * reaches it when no screen is mounted, and outside a placeholder join "no screen" implies "there is
 * a player". Every branch of it dereferences {@code this.player} and half of them send packets, so
 * it is skipped outright rather than fed a decoy.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	/** The launcher profile result type moved packages between API families; see ProfileLookup. */
	@Shadow @Final private CompletableFuture<?> profileFuture;
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
	 * <p>{@code updateLevelInEngines(null)} detaches {@code levelExtractor}, which releases every
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

	@Inject(method = "toggleFriendsScreen", at = @At("HEAD"), cancellable = true)
	private void nls$blockNestedJoinUi(final CallbackInfoReturnable<Boolean> cir) {
		// A friend invite can start another join from inside the synchronous save/resource stack.
		if (PlaceholderWorld.active()) cir.setReturnValue(true);
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

	/**
	 * The whole render frame, with the placeholder handed over for its duration.
	 *
	 * <p>Wrapping rather than injecting at HEAD and RETURN buys two things. The binding is released
	 * on the way out even if the frame throws, and — more importantly — a frame that <em>does</em>
	 * throw while the placeholder is up no longer takes the game with it. This is a cosmetic feature
	 * standing in front of a join; the worst it should ever be able to do is put the vanilla loading
	 * screen back. The exception is logged in full, and only swallowed for frames this mod changed.
	 * The swapchain image is then handed back by {@link FrameSurface}, without which the window
	 * would stay frozen on its last image for the rest of the session.
	 */
	@WrapMethod(method = "renderFrame")
	private void nls$renderFrame(final boolean advanceGameTime, final Operation<Void> original) {
		long timing = LoadingWork.startTiming();
		boolean bound = PlaceholderWorld.bind();
		try {
			// This also covers direct resource/save wait frames, not just runTick's final frame.
			original.call(advanceGameTime || bound && (SavingWorldView.visible() || DisconnectedWorldView.visible()));
		} catch (Throwable t) {
			if (!bound) throw t;
			PlaceholderWorld.onRenderFailed(t);
			if (!FrameSurface.handBack(Minecraft.getInstance())) {
				throw t;
			}
		} finally {
			if (bound) PlaceholderWorld.unbind();
			LoadingWork.endTiming("render frame", timing);
		}
	}

	/**
	 * {@code setScreenAndShow} forces a frame so a newly mounted screen appears at once. When the
	 * screen in question is one this mod just dropped, that forced frame is the only thing that
	 * still paints black — {@code advanceGameTime} is false there, so the level pass is skipped
	 * whatever is in {@code minecraft.level}. Skipping it removes the one-frame flicker; the normal
	 * loop draws the next frame a few milliseconds later.
	 *
	 * <p>{@code require = 0}: worth one frame of black, not worth a failed launch if another mod got
	 * to this call first.
	 */
	@Redirect(
		method = "setScreenAndShow",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;renderFrame(Z)V"),
		require = 0
	)
	private void nls$skipForcedFrame(final Minecraft minecraft, final boolean advanceGameTime) {
		NoLoadingScreenConfig config = NoLoadingScreenConfig.get();
		if (config.enabled) {
			Screen screen = ClientUi.screen(minecraft);
			// Already dropped by this mod: there is nothing new to show.
			if (screen == null && (minecraft.level != null || PlaceholderWorld.active())) {
				return;
			}
			// About to be replaced by the placeholder, a few statements from now.
			if (screen instanceof LevelLoadingScreen || screen instanceof ServerReconfigScreen) {
				return;
			}
		}

		minecraft.renderFrame(advanceGameTime);
	}

	/**
	 * {@code doWorldLoad} spins the integrated server up and then sits in
	 * {@code while (!server.isReady() || overlay != null)}, hand-rendering frames with
	 * {@code advanceGameTime = false}. Nothing ticks in there: not input, not the camera, not the
	 * connection. That is the frozen second at the start of a singleplayer join, and no amount of
	 * drawing something nicer can help while the loop owns the thread.
	 *
	 * <p>On loaders that allow early local connections, the memory channel can open immediately
	 * and the handshake queues until the server starts ticking. Forge instead rejects these
	 * handshakes on the network thread until its startup login gate opens. It must retain the real
	 * readiness check; {@link LoadingWaitLoop} keeps the placeholder interactive during that wait.
	 * The {@code overlay != null} condition is always left intact: resource reloads must finish.
	 */
	@Redirect(
		method = "doWorldLoad",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;isReady()Z")
	)
	private boolean nls$dontFreezeWhileServerBoots(final IntegratedServer server) {
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
		else minecraft.renderFrame(advanceGameTime);
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
		target = "Lnet/minecraft/client/gui/Gui;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
	private void nls$singleplayerLoadStarted(final Gui gui, final Screen screen, final Operation<Void> original,
		final LevelStorageAccess access, final PackRepository packs, final WorldStem stem,
		final Optional<GameRules> rules, final boolean newWorld) {
		// Capture vanilla's tracker BEFORE hiding its screen, and keep the same early scene.
		NoLoadingScreen.onSingleplayerLoadStart(stem.registries().compositeAccess(), (LevelLoadingScreen) screen);
		original.call(gui, screen);
	}

	@Inject(method = "clearClientLevel", at = @At("HEAD"))
	private void nls$clearLevelStart(final Screen screen, final CallbackInfo ci) {
		NoLoadingScreen.onLevelTornDown();
		NoLoadingScreen.markTimeline("Client world teardown started");
	}

	@Inject(method = "clearClientLevel", at = @At("RETURN"))
	private void nls$clearLevelEnd(final Screen screen, final CallbackInfo ci) {
		NoLoadingScreen.markTimeline("Client world teardown completed");
	}

	@WrapMethod(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V")
	private void nls$disconnect(final Screen screen, final boolean keepResourcePacks, final boolean stopSound, final Operation<Void> original) {
		boolean keep = DisconnectedWorldView.begin(screen, keepResourcePacks);
		NoLoadingScreen.onDisconnected(keep);
		if (!keep) SavingWorldView.capture();
		boolean completed = false;
		try {
			original.call(screen, keepResourcePacks, stopSound);
			completed = true;
		} finally {
			SavingWorldView.finish();
			if (keep) DisconnectedWorldView.finish(completed);
		}
	}

	@Inject(
		method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V")
	)
	private void nls$showSavingWorld(final Screen screen, final boolean keepResourcePacks, final boolean stopSound, final CallbackInfo ci) {
		SavingWorldView.install();
	}

	/**
	 * A captured scene is shown a few statements after vanilla writes null over this field. Other
	 * mods scrub whatever level they still find there at that write (ModernFix's world-leak
	 * mitigation nulls its chunk storage and light engine), which would remove the floor and the
	 * lighting from the scene about to go up. The HUD reset is the last vanilla reader before that
	 * write, so the field is detached right after it; the retained level is released by the scene.
	 */
	@WrapOperation(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;onDisconnected()V"))
	private void nls$detachRetainedScene(final Hud hud, final Operation<Void> original) {
		original.call(hud);
		if (SavingWorldView.pending() || DisconnectedWorldView.active()) ((Minecraft) (Object) this).level = null;
	}

	@Redirect(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;renderFrame(Z)V"))
	private void nls$interactiveSaveFrame(final Minecraft minecraft, final boolean advanceGameTime) {
		if (SavingWorldView.visible()) LoadingWaitLoop.frame(minecraft);
		else minecraft.renderFrame(advanceGameTime);
	}

	@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreenAndShow(Lnet/minecraft/client/gui/screens/Screen;)V"))
	private void nls$savingFinished(final Screen screen, final boolean keepResourcePacks, final boolean stopSound, final CallbackInfo ci) {
		SavingWorldView.finish();
		DisconnectedWorldView.install();
	}

	@WrapOperation(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;resetData()V"))
	private void nls$keepDisconnectedCamera(final GameRenderer renderer, final Operation<Void> original) {
		if (!DisconnectedWorldView.active()) original.call(renderer);
	}

	@WrapOperation(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/Minecraft;updateLevelInEngines(Lnet/minecraft/client/multiplayer/ClientLevel;Z)V"))
	private void nls$keepDisconnectedEngines(final Minecraft minecraft, final ClientLevel level, final boolean stopSound,
		final Operation<Void> original) {
		if (!DisconnectedWorldView.visible()) {
			original.call(minecraft, level, stopSound);
			return;
		}
		// Like configuration adoption, retain built meshes, but never retain a connection owner.
		if (stopSound) minecraft.getSoundManager().stop();
		this.pendingConnection = null;
		minecraft.updateTitle();
	}

	@Inject(method = "setLevel", at = @At("HEAD"))
	private void nls$setLevelStart(final ClientLevel level, final CallbackInfo ci) {
		// Hand the render engines back before vanilla points them at the real world.
		NoLoadingScreen.onLevelTornDown();
		NoLoadingScreen.markTimeline("Client world assembly started");
	}

	@Inject(method = "setLevel", at = @At("RETURN"))
	private void nls$setLevelEnd(final ClientLevel level, final CallbackInfo ci) {
		NoLoadingScreen.markTimeline("Client world assembly completed");
	}
}
