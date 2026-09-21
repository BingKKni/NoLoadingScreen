package io.github.bingkkni.noloadingscreen.platform;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import org.jspecify.annotations.Nullable;

/** The launcher's profile lookup result; authlib moved the type between API families. */
public final class ProfileLookup {
	private ProfileLookup() {}

	/** The profile carried by {@code Minecraft#profileFuture}'s value, or null when it has none. */
	public static @Nullable GameProfile profile(final @Nullable Object result) {
		return result instanceof ProfileResult lookup ? lookup.profile() : null;
	}

	/** The same value the launcher lookup would have produced; fixtures use it in place of a network call. */
	public static Object result(final GameProfile profile) {
		return new ProfileResult(profile);
	}
}
