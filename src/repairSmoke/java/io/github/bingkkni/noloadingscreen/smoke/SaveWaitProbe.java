package io.github.bingkkni.noloadingscreen.smoke;

import net.minecraft.client.server.IntegratedServer;

/**
 * Stands in for the integrated server during vanilla's synchronous save wait, which only polls
 * {@link #isShutdown()} after asking the server to halt. Allocated without construction: it owns no
 * thread, level, socket or save, and it ends by itself once the armed time has passed.
 */
public final class SaveWaitProbe extends IntegratedServer {
	private long deadline;

	private SaveWaitProbe() {
		super(null, null, null, null, null, null, null, null); // never invoked; the fixture allocates instances
	}

	public void arm(final long nanos) {
		this.deadline = System.nanoTime() + nanos;
	}

	@Override
	public boolean isShutdown() {
		return System.nanoTime() >= this.deadline;
	}

	@Override
	public void halt(final boolean wait) {
		// Nothing runs here to stop.
	}
}
