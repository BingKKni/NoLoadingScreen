package io.github.bingkkni.noloadingscreen.verification;

import io.github.bingkkni.noloadingscreen.JoinClassWarmup;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class JoinClassWarmupVerification {
	static void run() throws ReflectiveOperationException {
		Method resolve = JoinClassWarmup.class.getDeclaredMethod("resolve", String.class);
		resolve.setAccessible(true);
		resolve.invoke(null, Root.class.getName());
		if (Effects.initializations != 0 || Effects.constructions != 0) {
			throw new AssertionError("Resolving types must not execute class initializers or constructors");
		}
		resolve.invoke(null, Root.class.getName());
		if (Effects.initializations != 0) throw new AssertionError("Repeated type preparation must remain inert");
		try {
			resolve.invoke(null, "nls.test.MissingType");
			throw new AssertionError("Expected missing type");
		} catch (InvocationTargetException expected) {
			if (!(expected.getCause() instanceof ClassNotFoundException)) throw expected;
		}
		System.out.println("JoinClassWarmupVerification passed: no class initialization, no construction, repeated lookup and missing-type fallback.");
	}

	private static final class Effects {
		private static int initializations;
		private static int constructions;
	}

	private static final class Root {
		static { Effects.initializations++; }
		private Root() { Effects.constructions++; }
		private Related related;
		private Related method(final Related value) { return value; }
		private static final class Nested {
			static { Effects.initializations++; }
			private Related related;
		}
	}

	private static final class Related {
		static { Effects.initializations++; }
	}
}
