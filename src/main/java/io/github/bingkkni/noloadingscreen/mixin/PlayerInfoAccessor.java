package io.github.bingkkni.noloadingscreen.mixin;

import java.util.function.Supplier;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerInfo.class)
public interface PlayerInfoAccessor {
	@Accessor("skinLookup") @Nullable Supplier<PlayerSkin> nls$skinLookup();
}
