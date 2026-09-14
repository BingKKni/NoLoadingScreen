package io.github.bingkkni.noloadingscreen.compat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Render-thread-only single ownership between a warmup shell and one live Sodium renderer. */
final class PendingPrograms {
	private Map<Object, Object> owned;

	private PendingPrograms(Map<Object, Object> owned) { this.owned = owned; }

	static PendingPrograms detach(Map<Object, Object> shell) {
		Map<Object, Object> retained = new LinkedHashMap<>(shell);
		shell.clear();
		return new PendingPrograms(retained);
	}

	boolean transferTo(Map<Object, Object> target) {
		if (owned == null || !target.isEmpty()) return false;
		target.putAll(owned);
		owned = null;
		return true;
	}

	void close(Consumer<Object> delete) {
		Map<Object, Object> releasing = owned;
		owned = null;
		if (releasing == null) return;
		RuntimeException failure = null;
		for (Object program : releasing.values()) {
			try { delete.accept(program); }
			catch (RuntimeException error) {
				if (failure == null) failure = error;
				else if (failure != error) failure.addSuppressed(error);
			}
		}
		if (failure != null) throw failure;
	}
}
