package io.github.bingkkni.noloadingscreen.mixin;

import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Player.class)
public interface PlayerAccessor {
	@Invoker("updatePlayerPose") void nls$updatePlayerPose();
	@Invoker("canPlayerFitWithinBlocksAndEntitiesWhen") boolean nls$canFit(Pose pose);
	@Invoker("maybeBackOffFromEdge") Vec3 nls$backOffFromEdge(Vec3 movement, MoverType type);
}
