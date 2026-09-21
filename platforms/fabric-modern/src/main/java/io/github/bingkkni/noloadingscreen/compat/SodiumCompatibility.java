package io.github.bingkkni.noloadingscreen.compat;

/**
 * Default for a Fabric target without its own whitelist: Sodium keeps its native scheduling until
 * that target's private layouts have passed {@code verifyCompatibility}. Verified targets place
 * their exact build in {@code platforms/<target>/src/main/java}, which wins over this file.
 */
public final class SodiumCompatibility {
    private SodiumCompatibility() {}
    public static boolean supported() { return false; }
}
