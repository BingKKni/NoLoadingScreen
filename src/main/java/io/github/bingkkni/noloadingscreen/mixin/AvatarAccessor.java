package io.github.bingkkni.noloadingscreen.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Avatar.class)
public interface AvatarAccessor {
	@Accessor("DATA_PLAYER_MODE_CUSTOMISATION")
	static EntityDataAccessor<Byte> nls$modelCustomisation() {
		throw new AssertionError();
	}
}
