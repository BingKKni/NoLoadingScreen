package io.github.bingkkni.noloadingscreen;

/** Client loading policy shared by packet scheduling, chunk builders and slow-stage diagnostics. */
public final class LoadingWork {
	public static final long PACKET_BUDGET_NS = 8_000_000L;
	private static final long SETTLE_NS = 5_000_000_000L;
	private static long settleUntil;
	private static int slowReports;
	private static long resourceGeneration;

	public static long resourceGeneration() {
		return resourceGeneration;
	}

	public static void resourcesReloaded() {
		resourceGeneration++;
	}

	private LoadingWork() {}

	public static boolean active() {
		return NoLoadingScreenConfig.get().enabled && !DisconnectedWorldView.active() && !SavingWorldView.saving()
			&& (NoLoadingScreen.isLoading() || settleUntil != 0L && System.nanoTime() - settleUntil < 0L);
	}

	public static void worldArriving() {
		settleUntil = System.nanoTime() + SETTLE_NS;
		slowReports = 0;
	}

	public static void reset() {
		settleUntil = 0L;
		slowReports = 0;
	}

	public static long startTiming() {
		return active() ? System.nanoTime() : 0L;
	}

	public static void endTiming(final String stage, final long start) {
		if (start == 0L || !NoLoadingScreenConfig.get().enabled) return;
		long elapsed = System.nanoTime() - start;
		if (elapsed >= 100_000_000L && slowReports < 12) {
			slowReports++;
			NoLoadingScreen.LOGGER.warn("[loading-work] {} took {} ms (phase={}); includes mod hooks and JVM/GPU waits",
				stage, elapsed / 1_000_000L, NoLoadingScreen.phase());
		}
	}
}
