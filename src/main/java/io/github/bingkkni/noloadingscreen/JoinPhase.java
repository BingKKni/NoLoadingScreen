package io.github.bingkkni.noloadingscreen;

/**
 * Which part of the join we are in, so the overlay can say so.
 *
 * <p>Vanilla's two waiting screens carry either no text at all ("Reconfiguring" is one word that
 * does not distinguish "the proxy is picking a server" from "the registries are 4 MB") or text that
 * stops being true the moment this mod removes the wait behind it. Naming the phase is the whole
 * point: a join that stalls should say which side it is stalling on.
 */
public enum JoinPhase {
	NONE("none"),
	PREPARING_RESOURCES("preparingResources"),
	CONNECTING("connecting"),
	/** Singleplayer only: the integrated server is starting up and has not accepted a login yet. */
	SERVER_BOOT("serverBoot"),
	/** Configuration phase — registries, tags and resource packs. Entirely server-paced. */
	CONFIGURING("configuring"),
	/** Configuration is done; waiting for {@code ClientboundLoginPacket}. */
	WAITING_WORLD("waitingWorld"),
	/** The world exists and chunk data is streaming in. */
	RECEIVING_CHUNKS("receivingChunks");

	private final String name;

	JoinPhase(final String name) {
		this.name = name;
	}

	public String translationKey() {
		return "noloadingscreen.phase." + this.name;
	}

	/** Same phase, named short enough to sit in a one-line breakdown in chat. */
	public String shortTranslationKey() {
		return "noloadingscreen.phase." + this.name + ".short";
	}
}
