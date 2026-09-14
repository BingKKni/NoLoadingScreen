package io.github.bingkkni.noloadingscreen.compat;

/** Keep native third-party rendering until this loader's private Sodium layouts are verified. */
public final class SodiumCompatibility {
    private SodiumCompatibility() {}
    public static boolean supported() { return false; }
}
