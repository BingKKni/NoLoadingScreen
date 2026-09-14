package io.github.bingkkni.noloadingscreen;

/**
 * NeoForge listener construction has no global play-addon slot. Audited in 21.10.64/21.11.45:
 * ClientCommonPacketListenerImpl copies cookie fields, ClientPacketListener creates per-listener
 * helpers, and a null cookie chatState avoids mutating the HUD. Connection registration occurs
 * only at channelActive. ViaForge 4.3.1/4.3.2 inject handleLogin, not either constructor.
 * The shared caller never invokes connect or handleLogin on this isolated listener.
 */
final class PlayAddonGuard implements AutoCloseable {
	private PlayAddonGuard() {}
	static PlayAddonGuard acquire() { return new PlayAddonGuard(); }
	@Override public void close() {}
}
