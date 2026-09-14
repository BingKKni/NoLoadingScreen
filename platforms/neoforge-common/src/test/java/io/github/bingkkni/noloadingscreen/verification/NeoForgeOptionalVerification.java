package io.github.bingkkni.noloadingscreen.verification;

/** Optional artifact behavior checks share the same actual-collector fixtures as Fabric. */
public final class NeoForgeOptionalVerification {
	public static void run() throws ReflectiveOperationException {
		SodiumQueueVerification.run();
		System.out.println("Sodium actual collector FIFO/deferred/full-frame completion checks passed");
	}
}
