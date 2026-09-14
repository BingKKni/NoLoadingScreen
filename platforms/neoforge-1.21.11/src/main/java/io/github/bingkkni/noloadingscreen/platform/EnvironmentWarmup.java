package io.github.bingkkni.noloadingscreen.platform;

/** API-family environment classes resolved without initializing them. */
public final class EnvironmentWarmup {
	private EnvironmentWarmup() {}
	public static String[] types() {
		return new String[]{"net.minecraft.world.attribute.EnvironmentAttributeSystem", "net.minecraft.world.attribute.WeatherAttributes"};
	}
}
