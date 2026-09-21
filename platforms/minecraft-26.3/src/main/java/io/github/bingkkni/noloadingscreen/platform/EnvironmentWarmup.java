package io.github.bingkkni.noloadingscreen.platform;

/** API-family first-join classes resolved without initializing them. */
public final class EnvironmentWarmup {
	private EnvironmentWarmup() {}
	public static String[] types() {
		// 26.3 dropped PotionBrewing from the client; nothing else in the first-join set moved.
		return new String[]{"net.minecraft.world.attribute.EnvironmentAttributeSystem", "net.minecraft.world.attribute.WeatherAttributes"};
	}
}
