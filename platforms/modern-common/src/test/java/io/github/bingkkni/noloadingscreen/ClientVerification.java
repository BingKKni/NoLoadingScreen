package io.github.bingkkni.noloadingscreen;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

/** Runs inside the loader's CLIENT transformer, never on an untransformed game classpath. */
public final class ClientVerification {
    public static void run() throws Exception {
        ClassLoader loader = ClientVerification.class.getClassLoader();
        String configName = System.getProperty("nls.mixinConfig", "noloadingscreen.neoforge.mixins.json");
        Set<String> targets = new LinkedHashSet<>();
        int mixins = 0;
        boolean sodium = io.github.bingkkni.noloadingscreen.platform.LoaderServices.modVersion("sodium").isPresent();
        if (Boolean.getBoolean("nls.verify.compatibility") && !sodium) throw new AssertionError("Compatibility run requires the supplied Sodium artifact");
        try (var resource = loader.getResourceAsStream(configName)) {
            if (resource == null) throw new AssertionError("Missing " + configName);
            var config = JsonParser.parseReader(new InputStreamReader(resource, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var item : config.getAsJsonArray("client")) {
                String name = item.getAsString();
                if (name.startsWith("Sodium") && !sodium) continue;
                String path = config.get("package").getAsString().replace('.', '/') + '/' + name + ".class";
                ClassNode node = new ClassNode();
                try (var bytes = loader.getResourceAsStream(path)) {
                    if (bytes == null) throw new AssertionError("Missing mixin " + path);
                    new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE);
                }
                boolean found = false;
                for (AnnotationNode annotation : node.invisibleAnnotations == null ? java.util.List.<AnnotationNode>of() : node.invisibleAnnotations) {
                    if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
                    found = true;
                    for (int i = 0; i < annotation.values.size(); i += 2) {
                        String key = (String) annotation.values.get(i);
                        if (key.equals("value")) {
                            for (Object target : (java.util.List<?>) annotation.values.get(i + 1)) targets.add(((Type) target).getClassName());
                        } else if (key.equals("targets")) {
                            for (Object target : (java.util.List<?>) annotation.values.get(i + 1)) targets.add((String) target);
                        }
                    }
                }
                if (!found) throw new AssertionError("No @Mixin target on " + path);
                mixins++;
            }
        }
        if (mixins < 30 || targets.isEmpty()) throw new AssertionError("Incomplete Mixin inventory");
        for (String target : targets) {
            Class<?> transformed = Class.forName(target, false, loader);
            boolean hook = java.util.Arrays.stream(transformed.getDeclaredMethods()).anyMatch(method -> method.getName().contains("nls$"));
            if (!hook) throw new AssertionError("No transformed hook in " + target);
            System.out.println("Verified CLIENT target: " + target);
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        if (sodium) io.github.bingkkni.noloadingscreen.verification.OptionalModVerification.runSodium();
        PlaceholderMovementTest.main(new String[0]);
        io.github.bingkkni.noloadingscreen.verification.SharedVerification.run();
        System.out.println("Modern CLIENT verification PASSED: " + mixins + " strict Mixins, " + targets.size() + " targets, movement/skin/warmup fixtures"
            + (sodium ? ", Sodium " + io.github.bingkkni.noloadingscreen.platform.LoaderServices.modVersion("sodium").orElseThrow() + " private hooks" : ""));
    }
}
