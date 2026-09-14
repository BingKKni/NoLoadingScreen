package io.github.bingkkni.noloadingscreen;

/** FML has no Fabric networking-addon singleton to save around the isolated listener. */
final class PlayAddonGuard implements AutoCloseable {
    private PlayAddonGuard() {}
    static PlayAddonGuard acquire() { return new PlayAddonGuard(); }
    @Override public void close() {}
}
