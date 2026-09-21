package io.github.bingkkni.noloadingscreen.platform;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.services.ProfileResult;
import org.jspecify.annotations.Nullable;

/** authlib 10 (Minecraft 26.3) keeps the profile lookup result in its services package. */
public final class ProfileLookup {
	private ProfileLookup() {}

	public static @Nullable GameProfile profile(final @Nullable Object result) {
		return result instanceof ProfileResult lookup ? lookup.profile() : null;
	}

	public static Object result(final GameProfile profile) {
		return new ProfileResult(profile);
	}
}
