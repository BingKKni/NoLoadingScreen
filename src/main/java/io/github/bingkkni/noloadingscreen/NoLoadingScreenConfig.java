package io.github.bingkkni.noloadingscreen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import io.github.bingkkni.noloadingscreen.platform.LoaderServices;

public final class NoLoadingScreenConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = LoaderServices.configDirectory().resolve("noloadingscreen.json");

	private static NoLoadingScreenConfig instance;

	/** Master switch. When off the mod behaves exactly like vanilla. */
	public boolean enabled = true;

	/** Let WASD drift the camera around inside the placeholder. Purely local; nothing is sent. */
	public boolean placeholderFreeMove = true;

	/** Keep the progress bar and status text after the screen is gone, without the chunk rectangle. */
	public boolean loadingOverlay = true;

	/** Explicit local-only override; never changes the real server player's abilities. */
	public boolean allowFlightAndNoclip = false;
	public boolean retainWorldOnKick = false;
	/** 3..60 seconds, or 0 for unlimited. Applies only to multiplayer joins. */
	public int multiplayerWaitSeconds = 30;

	public int waitSeconds() {
		return multiplayerWaitSeconds == 0 ? 0 : Math.clamp(multiplayerWaitSeconds, 3, 60);
	}

	public static NoLoadingScreenConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static NoLoadingScreenConfig load() {
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
				NoLoadingScreenConfig loaded = GSON.fromJson(reader, NoLoadingScreenConfig.class);
				if (loaded != null) {
					loaded.multiplayerWaitSeconds = loaded.waitSeconds();
					return loaded;
				}
			} catch (IOException | RuntimeException e) {
				NoLoadingScreen.LOGGER.warn("Could not read {}, falling back to defaults", PATH, e);
			}
		}

		NoLoadingScreenConfig fresh = new NoLoadingScreenConfig();
		fresh.save();
		return fresh;
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			NoLoadingScreen.LOGGER.error("Could not write {}", PATH, e);
		}
	}

}
