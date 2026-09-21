package io.github.bingkkni.noloadingscreen.verification;

/** Package-local regression fixtures shared with the existing Fabric build. */
public final class SharedVerification {
    public static void run() throws Exception {
        SandboxVerification.run();
        RetainedLightQueueVerification.run();
        SkinPreloadVerification.run();
        JoinClassWarmupVerification.run();
        ModernBehaviorVerification.run();
    }
}
