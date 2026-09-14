package io.github.bingkkni.noloadingscreen.compat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PendingProgramsTest {
	@Test void warmedProgramsOutliveShellAndTransferOnce() {
		Object program = new Object();
		Map<Object, Object> shell = new LinkedHashMap<>();
		shell.put("actual-options", program);
		PendingPrograms pending = PendingPrograms.detach(shell);
		assertTrue(shell.isEmpty(), "Deleting shell must no longer delete retained GL programs");
		Map<Object, Object> live = new LinkedHashMap<>();
		assertTrue(pending.transferTo(live));
		assertSame(program, live.get("actual-options"));
		assertFalse(pending.transferTo(new LinkedHashMap<>()));
		pending.close(value -> fail("Transferred program still owned by pending cache"));
		AtomicInteger deleted = new AtomicInteger();
		live.values().forEach(value -> deleted.incrementAndGet());
		assertEquals(1, deleted.get(), "The live renderer remains the sole deleting owner");
	}

	@Test void mismatchDoesNotMergeAndPendingProgramsAreDisposedOnce() {
		Map<Object, Object> shell = new LinkedHashMap<>(Map.of("warm", new Object()));
		PendingPrograms pending = PendingPrograms.detach(shell);
		Map<Object, Object> existing = new LinkedHashMap<>(Map.of("foreign", new Object()));
		assertFalse(pending.transferTo(existing));
		assertEquals(1, existing.size());
		AtomicInteger deleted = new AtomicInteger();
		pending.close(value -> deleted.incrementAndGet());
		pending.close(value -> fail("Repeated cleanup deletes twice"));
		assertEquals(1, deleted.get());
		assertFalse(pending.transferTo(new LinkedHashMap<>()));
	}

	@Test void partialCleanupFailureStillReleasesEveryRemainingProgram() {
		Map<Object, Object> shell = new LinkedHashMap<>();
		for (int i = 0; i < 3; i++) shell.put(i, i);
		PendingPrograms pending = PendingPrograms.detach(shell);
		AtomicInteger deleted = new AtomicInteger();
		assertThrows(IllegalStateException.class, () -> pending.close(value -> {
			deleted.incrementAndGet();
			if (value.equals(0)) throw new IllegalStateException("native deletion failed");
		}));
		assertEquals(3, deleted.get());
		pending.close(value -> fail("Failed cleanup must not retry already released programs"));
	}
}
