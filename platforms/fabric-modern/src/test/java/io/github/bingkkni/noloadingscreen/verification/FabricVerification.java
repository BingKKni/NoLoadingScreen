package io.github.bingkkni.noloadingscreen.verification;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public final class FabricVerification implements PreLaunchEntrypoint {
    @Override public void onPreLaunch() {
        try {
            io.github.bingkkni.noloadingscreen.ClientVerification.run();
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }
}
