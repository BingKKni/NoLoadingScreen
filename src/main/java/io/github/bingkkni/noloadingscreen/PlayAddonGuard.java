package io.github.bingkkni.noloadingscreen;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.jspecify.annotations.Nullable;

/**
 * Makes constructing a {@code ClientPacketListener} leave no trace in Fabric API.
 *
 * <p>{@code ClientPacketListener.<init>} is not an inert constructor. Fabric API's networking module
 * injects into it and registers the new listener as <em>the</em> client play addon:
 *
 * <pre>
 * java.lang.IllegalStateException
 *     at ClientNetworkingImpl.setClientPlayAddon(ClientNetworkingImpl.java:126)
 *     at ClientPacketListener.handler$...$fabric-networking-api-v1$initAddon
 *     at ClientPacketListener.&lt;init&gt;
 * </pre>
 *
 * <p>That is why the placeholder never builds one when a play session already exists — it adopts the
 * outgoing one instead. Singleplayer world load is the one case with no listener to adopt <em>and</em>
 * no session to collide with, so building one there succeeds. But it still leaves the addon slot
 * pointing at an object that is about to be thrown away, and the real listener built minutes later
 * would then hit the same {@code IllegalStateException} — mid-join, unrecoverably.
 *
 * <p>So the slot is saved and put back around the construction. If Fabric's networking module is not
 * installed there is nothing to guard and {@link #acquire()} succeeds trivially; if it is installed
 * but its internals have moved, {@link #acquire()} returns null and the caller builds nothing rather
 * than corrupting state it cannot restore.
 */
final class PlayAddonGuard implements AutoCloseable {
	private static final String IMPL = "net.fabricmc.fabric.impl.networking.client.ClientNetworkingImpl";
	private static final String ADDON_TYPE = "ClientPlayNetworkAddon";
	private static final String CONFIG_ADDON_TYPE = "ClientConfigurationNetworkAddon";

	/** Logged once: a repeated warning every join would be worse than the problem. */
	private static boolean warned;

	private final @Nullable Field field;
	private final @Nullable Object saved;

	private PlayAddonGuard(final @Nullable Field field, final @Nullable Object saved) {
		this.field = field;
		this.saved = saved;
	}

	/** Null when the addon slot exists but cannot be secured, meaning: do not construct. */
	static @Nullable PlayAddonGuard acquire() {
		Class<?> impl;
		try {
			impl = Class.forName(IMPL, false, PlayAddonGuard.class.getClassLoader());
		} catch (ClassNotFoundException e) {
			// No Fabric networking module, nothing to collide with.
			return new PlayAddonGuard(null, null);
		} catch (Throwable t) {
			return warnAndRefuse(t);
		}

		try {
			Field playSlot = null;
			Field configSlot = null;
			for (Field candidate : impl.getDeclaredFields()) {
				if (!Modifier.isStatic(candidate.getModifiers())) continue;
				String type = candidate.getType().getSimpleName();
				if (type.equals(ADDON_TYPE)) playSlot = candidate;
				else if (type.equals(CONFIG_ADDON_TYPE)) configSlot = candidate;
			}
			if (playSlot != null && configSlot != null) {
				playSlot.setAccessible(true);
				configSlot.setAccessible(true);
				// Fabric rejects PLAY construction while CONFIGURATION is owned too. Never
				// clear either live slot to make a cosmetic listener fit.
				if (playSlot.get(null) != null || configSlot.get(null) != null) return null;
				return new PlayAddonGuard(playSlot, null);
			}
		} catch (Throwable t) {
			return warnAndRefuse(t);
		}

		return warnAndRefuse(null);
	}

	private static @Nullable PlayAddonGuard warnAndRefuse(final @Nullable Throwable cause) {
		if (!warned) {
			warned = true;
			NoLoadingScreen.LOGGER.warn(
				"Fabric's client play addon slot could not be secured, so the placeholder world will not be built"
					+ " for singleplayer joins. Everything else still works; report this with your Fabric API version.",
				cause
			);
		}
		return null;
	}

	@Override
	public void close() {
		if (this.field == null) {
			return;
		}
		try {
			this.field.set(null, this.saved);
		} catch (Throwable t) {
			NoLoadingScreen.LOGGER.error("Could not restore Fabric's client play addon slot", t);
		}
	}
}
