package io.github.bingkkni.noloadingscreen.platform;

/** API-family first-join classes resolved without initializing them. */
public final class EnvironmentWarmup {
	private EnvironmentWarmup() {}
	public static String[] types() {
		return new String[]{"net.minecraft.world.attribute.EnvironmentAttributeSystem", "net.minecraft.world.attribute.WeatherAttributes",
			"net.minecraft.world.item.alchemy.PotionBrewing"};
	}
}
