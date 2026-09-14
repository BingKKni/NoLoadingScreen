package io.github.bingkkni.noloadingscreen.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.bingkkni.noloadingscreen.LoadingWork;
import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The three moments in the play protocol that decide whether there is a world to look at.
 *
 * <p>{@code handleConfigurationStart} is a server switch on a proxy network: the level is torn down
 * here and does not come back until the new server sends {@code ClientboundLoginPacket}. That gap is
 * the "Reconfiguring" screen, and on a network like Hypixel it is the largest single item in the
 * join — around a second, all of it server-side and none of it something a client mod can shorten.
 * What a client mod can do is stop showing you a menu while it happens.
 *
 * <p>{@code startWaitingForNewLevel} has two branches, and only one of them goes through
 * {@code Gui#setScreen}:
 *
 * <pre>
 * if (this.minecraft.gui.screen() instanceof LevelLoadingScreen loadingScreen) {
 *    loadingScreen.update(this.levelLoadTracker, reason);   // singleplayer: reuse, no setScreen
 * } else {
 *    this.minecraft.setScreenAndShow(new LevelLoadingScreen(...));
 * }
 * </pre>
 *
 * Singleplayer always takes the first branch, because the screen {@code Minecraft#doWorldLoad} put
 * up before the integrated server even started is still mounted. That is why the interception in
 * {@code GuiMixin} never fires for a singleplayer join, and why the hook below exists.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
	private void nls$blockChatPacket(final String message, final CallbackInfo ci) {
		if (NoLoadingScreen.blockOutgoingMessage(false)) ci.cancel();
	}

	@Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
	private void nls$blockCommandPacket(final String command, final CallbackInfo ci) {
		if (NoLoadingScreen.blockOutgoingMessage(true)) ci.cancel();
	}

	/**
	 * The play listener is built the moment the configuration phase ends, so this is the exact point
	 * where "the server is still sending me registries" becomes "the server is building my player".
	 * Worth a checkpoint of its own: those two look identical from the outside and have completely
	 * different causes when they hang.
	 */
	@Inject(
		method = "<init>(Lnet/minecraft/client/Minecraft;Lnet/minecraft/network/Connection;Lnet/minecraft/client/multiplayer/CommonListenerCookie;)V",
		at = @At("RETURN")
	)
	private void nls$configurationFinished(final CallbackInfo ci) {
		if (!PlaceholderWorld.isBuilding()) {
			NoLoadingScreen.onConfigurationFinished();
		}
	}

	@Inject(
		method = "handleConfigurationStart",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;clearClientLevel(Lnet/minecraft/client/gui/screens/Screen;)V")
	)
	private void nls$configurationStarting(final ClientboundStartConfigurationPacket packet, final CallbackInfo ci) {
		// Last moment the real level and player exist; the placeholder inherits their sky and camera.
		NoLoadingScreen.onConfigurationStarting();
	}

	@WrapMethod(method = "handleConfigurationStart")
	private void nls$configurationStarted(final ClientboundStartConfigurationPacket packet, final Operation<Void> original) {
		original.call(packet);
		// Run AFTER all RETURN injections. ViaFabricPlus still needs the live channel in its
		// enableAutoRead tail; adoption replaces the outgoing listener's connection with a dead one.
		NoLoadingScreen.onConfigurationStarted();
	}

	@WrapMethod(method = "handleLogin")
	private void nls$measureLogin(final ClientboundLoginPacket packet, final Operation<Void> original) {
		long timing = net.minecraft.client.Minecraft.getInstance().isSameThread() ? LoadingWork.startTiming() : 0L;
		try {
			original.call(packet);
		} finally {
			LoadingWork.endTiming("login world assembly", timing);
		}
	}

	@Inject(
		method = "handleLogin",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
			shift = At.Shift.AFTER
		)
	)
	private void nls$loginStarting(final ClientboundLoginPacket packet, final CallbackInfo ci) {
		// The real world is about to be installed. Vanilla checks `minecraft.player == null` to
		// decide whether to build a player, so the placeholder has to be gone before it looks.
		NoLoadingScreen.onLoginStart();
	}

	@Inject(method = "handleMovePlayer", at = @At("RETURN"))
	private void nls$followServerPosition(final ClientboundPlayerPositionPacket packet, final CallbackInfo ci) {
		// AFTER vanilla resolves absolute/relative coordinates and acknowledges the teleport.
		// Holding the pre-teleport position causes repeated corrections (including camera resets).
		NoLoadingScreen.onPlayerPositionReceived();
	}

	@Inject(method = "startWaitingForNewLevel", at = @At("RETURN"))
	private void nls$onWaitingForNewLevel(
		final LocalPlayer player, final ClientLevel level, final LevelLoadingScreen.Reason reason, final CallbackInfo ci
	) {
		NoLoadingScreen.onWaitingForNewLevel();
	}
}
