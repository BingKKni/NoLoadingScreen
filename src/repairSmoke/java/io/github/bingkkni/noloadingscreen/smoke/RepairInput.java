package io.github.bingkkni.noloadingscreen.smoke;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;

/**
 * Real input delivery for the isolated GPU client. Events go through the game's own GLFW
 * callbacks, so the transformed dispatch chain is exercised exactly as an OS event would be.
 */
public final class RepairInput {
	private static double cursorX;

	private RepairInput() {}

	public static void mouseMove(final Minecraft client, final double dx) {
		cursorX += dx;
		long handle = client.getWindow().handle();
		GLFWCursorPosCallback callback = GLFW.glfwSetCursorPosCallback(handle, null);
		try { callback.invoke(handle, cursorX, 240.0); }
		finally { GLFW.glfwSetCursorPosCallback(handle, callback); }
	}

	public static void middleButton(final Minecraft client, final boolean pressed) {
		long handle = client.getWindow().handle();
		GLFWMouseButtonCallback callback = GLFW.glfwSetMouseButtonCallback(handle, null);
		try { callback.invoke(handle, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, pressed ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, 0); }
		finally { GLFW.glfwSetMouseButtonCallback(handle, callback); }
	}

	public static void forwardKey(final Minecraft client, final boolean pressed) {
		long handle = client.getWindow().handle();
		GLFWKeyCallback callback = GLFW.glfwSetKeyCallback(handle, null);
		try { callback.invoke(handle, GLFW.GLFW_KEY_W, GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_W), pressed ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, 0); }
		finally { GLFW.glfwSetKeyCallback(handle, callback); }
	}
}
