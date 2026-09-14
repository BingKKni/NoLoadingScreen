package io.github.bingkkni.noloadingscreen.compat;

/** Implemented only on optional Sodium collectors; has no dependency on Sodium classes. */
public interface PendingChunkBuild {
	boolean nls$pending();
}
