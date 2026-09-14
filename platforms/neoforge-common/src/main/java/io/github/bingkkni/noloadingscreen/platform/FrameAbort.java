package io.github.bingkkni.noloadingscreen.platform;

/** Internal transformed-renderer cleanup contract; only used after an owned frame fails. */
public interface FrameAbort {
	void nls$abortFrame();
}
