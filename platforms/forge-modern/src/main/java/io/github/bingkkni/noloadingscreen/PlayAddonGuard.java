package io.github.bingkkni.noloadingscreen;

/**
 * Forge has no Fabric networking addon slots to save or restore. This compile-time
 * replacement only removes that Fabric-specific guard; it does not assert that
 * constructing a Forge packet listener is free of other loader side effects.
 */
final class PlayAddonGuard implements AutoCloseable {
    private PlayAddonGuard() {}

    static PlayAddonGuard acquire() {
        return new PlayAddonGuard();
    }

    @Override
    public void close() {}
}
