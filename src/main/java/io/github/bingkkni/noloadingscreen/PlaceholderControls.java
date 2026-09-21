package io.github.bingkkni.noloadingscreen;

/** Edge-triggered local controls, independent of gameplay ticks and packet sending. */
public final class PlaceholderControls {
	private boolean forwardHeld;
	private boolean jumpHeld;
	private int sprintTicks;
	private int flightTicks;
	private boolean sprinting;
	private boolean flying;

	public void reset(final boolean sprinting, final boolean flying, final boolean forwardHeld, final boolean jumpHeld) {
		this.sprinting = sprinting;
		this.forwardHeld = forwardHeld;
		this.jumpHeld = jumpHeld;
		this.flying = flying;
		this.sprintTicks = this.flightTicks = 0;
	}

	public void tick(
		final boolean forward, final boolean backward, final boolean jump, final boolean shift,
		final boolean sprint, final int sprintWindow, final boolean enabled
	) {
		boolean forwardPressed = forward && !this.forwardHeld;
		boolean jumpPressed = jump && !this.jumpHeld;
		this.forwardHeld = forward;
		this.jumpHeld = jump;
		if (!enabled) {
			// Typing in chat, losing focus, or disabling movement must never arm a double tap.
			this.sprintTicks = this.flightTicks = 0;
			this.sprinting = false;
			return;
		}
		if (this.sprintTicks > 0) this.sprintTicks--;
		if (this.flightTicks > 0) this.flightTicks--;
		if (backward || shift && !this.flying) this.sprintTicks = 0;
		if (forwardPressed && !backward && (!shift || this.flying) && sprintWindow > 0) {
			if (this.sprintTicks > 0) {
				this.sprinting = true;
				this.sprintTicks = 0;
			} else {
				this.sprintTicks = sprintWindow;
			}
		}
		if (jumpPressed) {
			if (this.flightTicks > 0) {
				this.flying = !this.flying;
				this.flightTicks = 0;
			} else {
				this.flightTicks = 7;
			}
		}
		if (!forward || backward || shift && !this.flying) {
			this.sprinting = false;
		} else if (sprint) {
			this.sprinting = true;
		}
	}

	public boolean sprinting() { return this.sprinting; }
	public boolean flying() { return this.flying; }
	public void stopSprinting() { this.sprinting = false; }
	public void setFlying(final boolean flying) { this.flying = flying; this.flightTicks = 0; }
	public void restrictFlight(final boolean mayfly) {
		if (!mayfly) setFlying(false);
	}
}
