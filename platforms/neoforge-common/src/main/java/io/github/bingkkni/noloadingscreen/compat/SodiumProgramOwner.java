package io.github.bingkkni.noloadingscreen.compat;

import java.util.Map;

/** The actual Sodium instance owns and deletes every program in this map. */
public interface SodiumProgramOwner {
	Map<Object, Object> nls$programs();
}
