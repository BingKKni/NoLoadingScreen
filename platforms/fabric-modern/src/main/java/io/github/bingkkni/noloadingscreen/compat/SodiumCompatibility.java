package io.github.bingkkni.noloadingscreen.compat;

/** New Fabric targets retain native Sodium behavior until their private layouts are verified. */
public final class SodiumCompatibility {
    private SodiumCompatibility() {}
    public static boolean supported() { return false; }
}
