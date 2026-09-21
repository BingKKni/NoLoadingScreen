package io.github.bingkkni.noloadingscreen.smoke;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Event;

/**
 * Real input delivery for the isolated GPU client on 26.3, which reads SDL instead of GLFW
 * callbacks. Events are pushed onto SDL's own queue and picked up by the game's next poll, so the
 * transformed dispatch chain ({@code SDLEventHandler} → {@code Minecraft.execute} → handlers) is
 * exercised exactly as an OS event would exercise it.
 */
public final class RepairInput {
	private static final int SDL_EVENT_KEY_DOWN = 768;
	private static final int SDL_EVENT_KEY_UP = 769;
	private static final int SDL_EVENT_MOUSE_MOTION = 1024;
	private static final int SDL_EVENT_MOUSE_BUTTON_DOWN = 1025;
	private static final int SDL_EVENT_MOUSE_BUTTON_UP = 1026;
	/** SDLK_W: the layout-dependent keycode SDL reports beside the physical scancode. */
	private static final int KEYCODE_W = 'w';
	private static double cursorX;

	private RepairInput() {}

	public static void mouseMove(final Minecraft client, final double dx) {
		cursorX += dx;
		try (SDL_Event event = SDL_Event.calloc()) {
			event.type(SDL_EVENT_MOUSE_MOTION);
			event.motion(motion -> motion.type(SDL_EVENT_MOUSE_MOTION).windowID(windowId(client))
				.x((float) cursorX).y(240.0F).xrel((float) dx).yrel(0.0F));
			push(event);
		}
	}

	public static void middleButton(final Minecraft client, final boolean pressed) {
		int type = pressed ? SDL_EVENT_MOUSE_BUTTON_DOWN : SDL_EVENT_MOUSE_BUTTON_UP;
		try (SDL_Event event = SDL_Event.calloc()) {
			event.type(type);
			event.button(button -> button.type(type).windowID(windowId(client))
				.button((byte) InputConstants.MOUSE_BUTTON_MIDDLE).down(pressed).clicks((byte) 1).x((float) cursorX).y(240.0F));
			push(event);
		}
	}

	public static void forwardKey(final Minecraft client, final boolean pressed) {
		int type = pressed ? SDL_EVENT_KEY_DOWN : SDL_EVENT_KEY_UP;
		try (SDL_Event event = SDL_Event.calloc()) {
			event.type(type);
			event.key(key -> key.type(type).windowID(windowId(client))
				.scancode(InputConstants.KEY_W).key(KEYCODE_W).mod((short) 0).down(pressed).repeat(false));
			push(event);
		}
	}

	private static int windowId(final Minecraft client) {
		return SDLVideo.SDL_GetWindowID(client.getWindow().handle());
	}

	private static void push(final SDL_Event event) {
		if (!SDLEvents.SDL_PushEvent(event)) throw new IllegalStateException("SDL refused the injected event");
	}
}
