package io.github.bingkkni.noloadingscreen.verification;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.ResourceLocation;
final class SkinTestTexture {
	static ClientAsset.Texture texture(String name) { return new ClientAsset.ResourceTexture(ResourceLocation.fromNamespaceAndPath("noloadingscreen", "test/" + name)); }
}
