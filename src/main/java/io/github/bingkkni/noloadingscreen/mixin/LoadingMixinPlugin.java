package io.github.bingkkni.noloadingscreen.mixin;

import java.util.List;
import java.util.Set;
import io.github.bingkkni.noloadingscreen.compat.SodiumCompatibility;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Private renderer layouts must be verified before enabling a version-specific compatibility hook. */
public final class LoadingMixinPlugin implements IMixinConfigPlugin {
	@Override public void onLoad(final String mixinPackage) {}
	@Override public String getRefMapperConfig() { return null; }
	@Override public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {}
	@Override public List<String> getMixins() { return null; }
	@Override public void preApply(final String target, final ClassNode node, final String mixin, final IMixinInfo info) {}
	@Override public void postApply(final String target, final ClassNode node, final String mixin, final IMixinInfo info) {}

	@Override
	public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
		return !mixinClassName.startsWith("io.github.bingkkni.noloadingscreen.mixin.Sodium") || SodiumCompatibility.supported();
	}
}
