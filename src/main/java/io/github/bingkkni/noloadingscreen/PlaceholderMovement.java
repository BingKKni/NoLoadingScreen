package io.github.bingkkni.noloadingscreen;

/** Client-only motion. Collision queries may read the frozen level, but never tick an entity. */
public final class PlaceholderMovement {
	private static final double MIN_SPEED = 0.003;

	public record Motion(double x, double y, double z) {}

	@FunctionalInterface
	public interface Collision {
		Motion resolve(Motion requested);
	}

	/** Values from the outgoing player's attributes, fluid state and block below their feet. */
	public record Physics(double walkingSpeed, double flyingSpeed, double jumpPower, double gravity,
		double friction, double airDrag, double verticalDrag, boolean inWater,
		double waterSlowdown, double waterAcceleration) {
		public Physics(double walkingSpeed, double flyingSpeed, double jumpPower, double gravity,
			double friction, double airDrag, double verticalDrag) {
			this(walkingSpeed, flyingSpeed, jumpPower, gravity, friction, airDrag, verticalDrag, false, 0.8, 0.02);
		}
	}

	private double x, y, z;
	private double oldX, oldY, oldZ;
	private double velocityX, velocityY, velocityZ;
	private boolean onGround;
	private boolean horizontalCollision;
	private int jumpDelay;

	public void reset(
		final double x, final double y, final double z,
		final double velocityX, final double velocityY, final double velocityZ, final boolean onGround
	) {
		this.x = this.oldX = x;
		this.y = this.oldY = y;
		this.z = this.oldZ = z;
		this.velocityX = velocityX;
		this.velocityY = velocityY;
		this.velocityZ = velocityZ;
		this.onGround = onGround;
		this.horizontalCollision = false;
		this.jumpDelay = 0;
	}

	public void tick(
		final double forward, final double strafe, final double vertical, final float yaw,
		final Physics physics, final boolean flying, final boolean sprinting, final boolean jumpDown,
		final boolean enabled, final double minY, final double maxY, final Collision collision
	) {
		tick(forward, strafe, vertical, yaw, physics, flying, sprinting, jumpDown, enabled, minY, maxY, false, collision);
	}

	public void tick(
		final double forward, final double strafe, final double vertical, final float yaw,
		final Physics physics, final boolean flying, final boolean sprinting, final boolean jumpDown,
		final boolean enabled, final double minY, final double maxY, final boolean noclip, final Collision collision
	) {
		this.oldX = this.x;
		this.oldY = this.y;
		this.oldZ = this.z;
		if (!enabled) {
			this.velocityX = this.velocityY = this.velocityZ = 0.0;
			this.jumpDelay = 0;
			return;
		}
		if (this.jumpDelay > 0) this.jumpDelay--;
		if (!jumpDown) this.jumpDelay = 0;
		if (this.velocityX * this.velocityX + this.velocityZ * this.velocityZ < MIN_SPEED * MIN_SPEED) {
			this.velocityX = this.velocityZ = 0.0;
		}
		if (Math.abs(this.velocityY) < MIN_SPEED) this.velocityY = 0.0;
		double radians = Math.toRadians(yaw);
		double sin = Math.sin(radians);
		double cos = Math.cos(radians);
		boolean groundedAtStart = this.onGround && !flying;
		if (flying) {
			this.velocityY += vertical * physics.flyingSpeed * 3.0;
		} else if (physics.inWater) {
			// LivingEntity's jumpInLiquid/goDownInWater pair applies this each held tick.
			this.velocityY += vertical * 0.04;
		} else if (jumpDown && this.onGround && this.jumpDelay == 0 && physics.jumpPower > 1.0E-5) {
			this.velocityY = Math.max(this.velocityY, physics.jumpPower);
			if (sprinting) {
				this.velocityX -= sin * 0.2;
				this.velocityZ += cos * 0.2;
			}
			this.jumpDelay = 10;
		}

		// KeyboardInput normalizes first, LocalPlayer scales by .98, then Entity.moveRelative.
		double length = Math.hypot(forward, strafe);
		double scale = 0.98 / Math.max(1.0, length);
		double acceleration;
		if (flying) {
			acceleration = physics.flyingSpeed * (sprinting ? 2.0 : 1.0);
		} else if (physics.inWater) {
			acceleration = physics.waterAcceleration;
		} else if (groundedAtStart) {
			acceleration = physics.walkingSpeed * (sprinting ? 1.3 : 1.0);
			if (physics.friction > 0.6) acceleration *= 0.216 / Math.pow(physics.friction, 3);
		} else {
			acceleration = sprinting ? 0.026 : 0.02;
		}
		this.velocityX += (strafe * cos - forward * sin) * scale * acceleration;
		this.velocityZ += (forward * cos + strafe * sin) * scale * acceleration;

		Motion requested = new Motion(this.velocityX, this.velocityY, this.velocityZ);
		// Flight and collision are independent: ordinary creative flight still hits walls.
		// Never call Entity.move's gameplay callbacks for the disposable player.
		Motion resolved = noclip ? requested : collision.resolve(requested);
		double movedY = Math.clamp(this.y + resolved.y, minY, maxY) - this.y;
		this.x += resolved.x;
		this.y += movedY;
		this.z += resolved.z;
		this.onGround = !flying && requested.y < 0.0 && differs(requested.y, movedY);
		this.horizontalCollision = differs(requested.x, resolved.x) || differs(requested.z, resolved.z);
		if (differs(requested.x, resolved.x)) this.velocityX = 0.0;
		if (differs(requested.z, resolved.z)) this.velocityZ = 0.0;
		if (differs(requested.y, movedY)) this.velocityY = 0.0;

		if (physics.inWater && !flying) {
			this.velocityX *= physics.waterSlowdown;
			this.velocityZ *= physics.waterSlowdown;
			this.velocityY *= 0.8;
			if (!sprinting) this.velocityY -= physics.gravity / 16.0;
		} else {
			double drag = physics.airDrag * (groundedAtStart ? physics.friction : 1.0);
			this.velocityX *= drag;
			this.velocityZ *= drag;
			// Vanilla applies walking gravity AFTER movement, and .98 vertical drag, not flight's .6.
			this.velocityY = flying ? this.velocityY * 0.6 : (this.velocityY - physics.gravity) * physics.verticalDrag;
		}
	}

	public boolean onGround() { return this.onGround; }
	public boolean horizontalCollision() { return this.horizontalCollision; }
	public double x(final float partialTick) { return interpolate(this.oldX, this.x, partialTick); }
	public double y(final float partialTick) { return interpolate(this.oldY, this.y, partialTick); }
	public double z(final float partialTick) { return interpolate(this.oldZ, this.z, partialTick); }

	private static boolean differs(final double a, final double b) { return Math.abs(a - b) > 1.0E-7; }
	private static double interpolate(final double previous, final double current, final float partialTick) {
		return previous + (current - previous) * Math.clamp(partialTick, 0.0F, 1.0F);
	}
}
