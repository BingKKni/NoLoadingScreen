package io.github.bingkkni.noloadingscreen.mixin;

import io.github.bingkkni.noloadingscreen.compat.SodiumProgramOwner;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer", remap = false)
public interface SodiumShaderProgramsMixin extends SodiumProgramOwner {
	@Accessor("programs") Map<Object, Object> nls$programs();
}
