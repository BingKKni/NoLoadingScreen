package io.github.bingkkni.noloadingscreen.mixin;

import io.github.bingkkni.noloadingscreen.CapeState;
import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ClientAvatarState.class)
public abstract class ClientAvatarStateMixin implements CapeState {
	@Shadow private double xCloak, yCloak, zCloak, xCloakO, yCloakO, zCloakO;
	@Shadow private float walkDist, walkDistO, bob, bobO;

	@Override public Snapshot nls$capture(Vec3 anchor) {
		return new Snapshot(new Vec3(xCloak, yCloak, zCloak).subtract(anchor),
			new Vec3(xCloakO, yCloakO, zCloakO).subtract(anchor), walkDist, walkDistO, bob, bobO);
	}
	@Override public void nls$restore(Snapshot state, Vec3 anchor, float yawDelta) {
		Vec3 current = state.current().yRot((float) Math.toRadians(-yawDelta)).add(anchor);
		Vec3 previous = state.previous().yRot((float) Math.toRadians(-yawDelta)).add(anchor);
		xCloak = current.x; yCloak = current.y; zCloak = current.z;
		xCloakO = previous.x; yCloakO = previous.y; zCloakO = previous.z;
		walkDist = state.walk(); walkDistO = state.oldWalk(); bob = state.bob(); bobO = state.oldBob();
	}
}
