package io.github.bingkkni.noloadingscreen.verification;

import io.github.bingkkni.noloadingscreen.compat.PendingChunkBuild;
import io.github.bingkkni.noloadingscreen.compat.SodiumCompatibility;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Optional-mod checks for 26.x loaders share the actual-collector fixtures used by the Fabric build. */
public final class OptionalModVerification {
    private static final String SODIUM = "net.caffeinemc.mods.sodium.client.";

    private OptionalModVerification() {}

    /** Requires the supplied Sodium to be the exact whitelisted build and every private hook to have applied. */
    public static void runSodium() throws ReflectiveOperationException {
        if (!SodiumCompatibility.supported()) throw new AssertionError("Sodium artifact must match the tested whitelist");
        Map<String, List<String>> hooks = Map.of(
            SODIUM + "render.SodiumWorldRenderer", List.of("nls$measureRendererInitialization", "nls$rememberFreshRenderer", "nls$coalesceFirstReload"),
            SODIUM + "render.chunk.RenderSectionManager", List.of("nls$measureRendererTeardown", "nls$leaveLoadingBuildsOnWorkers"),
            SODIUM + "render.chunk.compile.executor.ChunkBuilder", List.of("nls$smallVoidWorkerPool"),
            SODIUM + "render.chunk.compile.executor.ChunkJobCollector", List.of("nls$pending"));
        ClassLoader loader = OptionalModVerification.class.getClassLoader();
        for (var target : hooks.entrySet()) {
            Class<?> type = Class.forName(target.getKey(), false, loader);
            for (String hook : target.getValue()) {
                if (Arrays.stream(type.getDeclaredMethods()).map(Method::getName).noneMatch(name -> name.contains(hook)))
                    throw new AssertionError(target.getKey() + " missing " + hook);
                System.out.println("Verified optional hook: " + target.getKey() + ":" + hook);
            }
        }
        Class<?> collector = Class.forName(SODIUM + "render.chunk.compile.executor.ChunkJobCollector", false, loader);
        if (!PendingChunkBuild.class.isAssignableFrom(collector)) throw new AssertionError("Sodium collector lost the non-blocking bridge");
        SodiumQueueVerification.run();
        System.out.println("Sodium actual collector FIFO/deferred/full-frame completion checks passed");
    }
}
