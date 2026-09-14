package io.github.bingkkni.noloadingscreen.platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/** Restores values AND depth, keeping the live stack's identity and capacity intact. */
public final class MatrixStackSnapshot {
	private final List<Matrix4f> levels;
	public MatrixStackSnapshot(Matrix4fStack stack) {
		try {
			Matrix4fStack copy = (Matrix4fStack) stack.clone();
			List<Matrix4f> values = new ArrayList<>();
			while (true) {
				values.add(new Matrix4f(copy));
				try { copy.popMatrix(); } catch (IllegalStateException empty) { break; }
			}
			Collections.reverse(values);
			levels = List.copyOf(values);
		} catch (CloneNotSupportedException impossible) { throw new AssertionError(impossible); }
	}
	public void restore(Matrix4fStack stack) {
		stack.clear().set(levels.getFirst());
		for (int i = 1; i < levels.size(); i++) stack.pushMatrix().set(levels.get(i));
	}
}
