package io.github.bingkkni.noloadingscreen.verification;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
final class SkinTestTexture {
	static ClientAsset.Texture texture(String name) { return new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath("noloadingscreen", "test/" + name)); }
}
