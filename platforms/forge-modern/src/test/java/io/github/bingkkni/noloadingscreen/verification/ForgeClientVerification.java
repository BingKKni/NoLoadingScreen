package io.github.bingkkni.noloadingscreen.verification;

import io.github.bingkkni.noloadingscreen.NoLoadingScreen;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;
import org.spongepowered.asm.mixin.MixinEnvironment;

/** Entrypoint in Forge's transformed game module, not the bootstrap class loader. */
public final class ForgeClientVerification {
    public static void main(String[] args) throws Exception {
        if (FMLLoader.getDist() != Dist.CLIENT
                || MixinEnvironment.getCurrentEnvironment().getSide() != MixinEnvironment.Side.CLIENT) {
            throw new AssertionError("Verification requires native CLIENT transformation");
        }
        if (!LoadingModList.getErrors().isEmpty()) {
            throw new AssertionError("Forge discovery failed: " + LoadingModList.getErrors());
        }
        if (LoadingModList.getModFileById(NoLoadingScreen.MOD_ID) == null) {
            throw new AssertionError("Forge did not discover NoLoadingScreen");
        }
        if (ForgeClientVerification.class.getClassLoader()
                != FMLLoader.getGameLayer().findLoader(NoLoadingScreen.MOD_ID)) {
            throw new AssertionError("Verifier is outside the transformed game layer");
        }
        ClientVerification.run();
        var file = LoadingModList.getModFileById(NoLoadingScreen.MOD_ID).getFile();
        var info = new net.minecraft.server.packs.PackLocationInfo("mod:" + NoLoadingScreen.MOD_ID,
            net.minecraft.network.chat.Component.literal("NoLoadingScreen"),
            net.minecraft.server.packs.repository.PackSource.DEFAULT, java.util.Optional.empty());
        var supplier = new net.minecraft.server.packs.PathPackResources.PathResourcesSupplier(file.findResource(""));
        var type = net.minecraft.server.packs.PackType.CLIENT_RESOURCES;
        var metadata = net.minecraft.server.packs.repository.Pack.readPackMetadata(info, supplier,
            net.minecraft.SharedConstants.getCurrentVersion().packVersion(type), type);
        if (metadata == null) throw new AssertionError("Forge cannot read the mod's ResourcePackInfo");
        System.out.println("Forge ResourcePackInfo verification PASSED");
    }
}
