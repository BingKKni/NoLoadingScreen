package io.github.bingkkni.noloadingscreen.platform;

/** API-family first-join classes resolved without initializing them. */
public final class EnvironmentWarmup {
	private EnvironmentWarmup() {}
	public static String[] types() {
		return new String[]{"net.minecraft.client.renderer.DimensionSpecialEffects", "net.minecraft.world.level.biome.Biome",
			"net.minecraft.world.item.alchemy.PotionBrewing"};
	}
}
