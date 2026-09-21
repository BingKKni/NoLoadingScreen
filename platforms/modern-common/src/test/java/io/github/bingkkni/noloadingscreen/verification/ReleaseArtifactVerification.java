package io.github.bingkkni.noloadingscreen.verification;

import io.github.bingkkni.noloadingscreen.NoLoadingScreen;
import java.net.JarURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.minecraft.SharedConstants;

/** Reject a runtime check that accidentally resolves the engine from recompiled class directories. */
public final class ReleaseArtifactVerification {
    private ReleaseArtifactVerification() {}

    public static void run() throws Exception {
        String supplied = System.getProperty("nls.verify.artifact");
        if (supplied == null) return;
        Path artifact = Path.of(supplied).toRealPath();
        var source = NoLoadingScreen.class.getProtectionDomain().getCodeSource().getLocation();
        var location = source.getProtocol().equals("jar")
            ? ((JarURLConnection) source.openConnection()).getJarFileURL() : source;
        Path loaded = Path.of(location.toURI()).toRealPath();
        if (!Files.isRegularFile(loaded) || Files.mismatch(artifact, loaded) != -1) {
            throw new AssertionError("Engine was not loaded from the unchanged release JAR: " + source);
        }
        String expectedGame = System.getProperty("nls.verify.minecraft");
        if (!SharedConstants.getCurrentVersion().id().equals(expectedGame)) {
            throw new AssertionError("Wrong runtime game version: " + SharedConstants.getCurrentVersion().id());
        }
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact)));
        System.out.println("Verified release runtime: Minecraft " + expectedGame + "; SHA256=" + sha + "; source=" + source);
    }
}
