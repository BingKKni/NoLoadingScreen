package io.github.bingkkni.noloadingscreen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class NoLoadingScreenConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("noloadingscreen.json");

	private static NoLoadingScreenConfig instance;

	/** Master switch. When off the mod behaves exactly like vanilla. */
	public boolean enabled = true;

	/** Let WASD drift the camera around inside the placeholder. Purely local; nothing is sent. */
	public boolean placeholderFreeMove = true;

	/** Keep drawing that same progress bar, chunk map and status line after the screen is gone. */
	public boolean loadingOverlay = true;

	/**
	 * Put the join time and its phase breakdown in chat. Both are written to the log either way, so
	 * this only decides whether they are also on screen.
	 */
	public boolean showJoinTime = false;

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
