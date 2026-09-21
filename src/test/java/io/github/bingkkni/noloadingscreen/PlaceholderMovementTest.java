package io.github.bingkkni.noloadingscreen;

public final class PlaceholderMovementTest {
	private static int assertions;
	private static final PlaceholderMovement.Physics NORMAL = new PlaceholderMovement.Physics(0.1, 0.05, 0.42, 0.08, 0.6, 0.91, 0.98);

	public static void main(final String[] args) {
		testSpeedsAndInterpolation();
		testAttributesAndDamping();
		testSprintJumpHandoff();
		testGroundAndWalls();
		testHeldJump();
		testFlight();
		testControls();
		testFlyingHandoff();
		System.out.println("PlaceholderMovementTest: " + assertions + " assertions passed");
	}

	private static PlaceholderMovement grounded() {
		PlaceholderMovement m = new PlaceholderMovement();
		m.reset(0, 0, 0, 0, -0.0784, 0, true);
		return m;
	}

	private static PlaceholderMovement.Collision floor(final PlaceholderMovement m) {
		return d -> new PlaceholderMovement.Motion(d.x(), Math.max(-m.y(1), d.y()), d.z());
	}

	private static void walk(final PlaceholderMovement m, final boolean sprint, final boolean jump) {
		m.tick(1, 0, 0, 0, NORMAL, false, sprint, jump, true, -1000, 1000, floor(m));
	}

	private static void testSpeedsAndInterpolation() {
		PlaceholderMovement m = grounded();
		walk(m, false, false);
		close(0.098, m.z(1), "ground acceleration");
		close(0.049, m.z(.5F), "interpolated movement");
		for (int i = 0; i < 500; i++) walk(m, false, false);
		double before = m.z(1);
		walk(m, false, false);
		close(0.098 / (1 - 0.546), m.z(1) - before, "walking terminal speed");
		close(before, m.z(0), "tick boundary continuity");
		for (int i = 0; i < 500; i++) walk(m, true, false);
		before = m.z(1);
		walk(m, true, false);
		close(0.098 * 1.3 / (1 - 0.546), m.z(1) - before, "sprinting terminal speed");
		m = grounded();
		m.tick(1, 1, 0, 90, NORMAL, false, false, false, true, -1000, 1000, floor(m));
		close(0.098, Math.hypot(m.x(1), m.z(1)), "26.2 normalizes keyboard diagonals BEFORE .98 scaling");
		check(m.x(1) < 0 && m.z(1) > 0, "yaw rotates input");
		before = m.z(1);
		m.tick(1, 0, 0, 0, NORMAL, false, true, true, false, -1000, 1000, floor(m));
		close(before, m.z(1), "disabled movement ignores all input");
	}

	private static void testAttributesAndDamping() {
		PlaceholderMovement m = grounded();
		PlaceholderMovement.Physics boosted = new PlaceholderMovement.Physics(.2, .1, .42, .04, .6, .91, .98);
		m.tick(1, 0, 0, 0, boosted, false, false, true, true, -1000, 1000, floor(m));
		close(.196, m.z(1), "server movement attribute is retained");
		close(.42, m.y(1), "speed attribute does not multiply jump strength");
		m.tick(0, 0, 0, 0, boosted, false, false, false, true, -1000, 1000, floor(m));
		close(.42 + (.42 - .04) * .98, m.y(1), "server gravity attribute is retained");
		m = grounded();
		for (int i = 0; i < 500; i++) walk(m, false, false);
		double before = m.z(1);
		m.tick(0, 0, 0, 0, NORMAL, false, false, false, true, -1000, 1000, floor(m));
		close(.098 / (1 - .546) * .546, m.z(1) - before, "key release damps momentum");
		for (int i = 0; i < 100; i++) m.tick(0, 0, 0, 0, NORMAL, false, false, false, true, -1000, 1000, floor(m));
		before = m.z(1);
		m.tick(0, 0, 0, 0, NORMAL, false, false, false, true, -1000, 1000, floor(m));
		close(before, m.z(1), "small velocities settle");
		m.tick(1, 0, 0, 0, NORMAL, false, true, true, false, -1000, 1000, floor(m));
		m.tick(0, 0, 0, 0, NORMAL, false, false, false, true, -1000, 1000, floor(m));
		close(before, m.z(1), "reenabling does not restore discarded momentum");
		m = grounded();
		for (int i = 0; i < 300; i++) m.tick(1, 0, 0, 0, boosted, true, false, false, true, -1000, 1000, floor(m));
		before = m.z(1);
		m.tick(1, 0, 0, 0, boosted, true, false, false, true, -1000, 1000, floor(m));
		close(.098 / .09, m.z(1) - before, "server flight speed retained");
	}

	private static void testSprintJumpHandoff() {
		PlaceholderMovement m = new PlaceholderMovement();
		m.reset(0, 20, 0, 0, 0.25, 0.35, false);
		walk(m, true, true);
		close(0.35 + 0.026 * .98, m.z(1), "adopted sprint jump keeps momentum and AIR acceleration");
		close(20.25, m.y(1), "adopted jump is not restarted or stopped");
		double before = m.z(1);
		walk(m, true, true);
		close((.35 + .026 * .98) * .91 + .026 * .98, m.z(1) - before, "air drag is .91, not .546");
		close(20.25 + (.25 - .08) * .98, m.y(1), "gravity follows movement with .98 vertical drag");
		m = grounded();
		walk(m, true, true);
		close(.42, m.y(1), "vanilla ground jump height");
		close(.2 + .1274, m.z(1), "sprint jump includes vanilla forward impulse");
	}

	private static void testGroundAndWalls() {
		PlaceholderMovement m = grounded();
		for (int i = 0; i < 200; i++) walk(m, false, false);
		close(0, m.y(1), "standing does not fall through terrain");
		check(m.onGround(), "standing ground contact");
		m = grounded();
		final PlaceholderMovement wallMotion = m;
		PlaceholderMovement.Collision wall = d -> new PlaceholderMovement.Motion(d.x(), Math.max(-wallMotion.y(1), d.y()), Math.min(.5 - wallMotion.z(1), d.z()));
		for (int i = 0; i < 50; i++) m.tick(1, 0, 0, 0, NORMAL, false, true, false, true, -1000, 1000, wall);
		close(.5, m.z(1), "walking cannot pass through wall");
		check(m.horizontalCollision(), "wall hit cancels sprint");
		m.tick(-1, 0, 0, 0, NORMAL, false, false, false, true, -1000, 1000, wall);
		check(m.z(1) < .5, "blocked velocity cleared so movement away works immediately");
	}

	private static void testHeldJump() {
		PlaceholderMovement m = grounded();
		int takeoffs = 0;
		double highest = 0;
		for (int i = 0; i < 100; i++) {
			double y = m.y(1);
			walk(m, true, true);
			if (y == 0 && m.y(1) > 0) takeoffs++;
			highest = Math.max(highest, m.y(1));
			check(m.y(1) >= 0, "jump lands on terrain");
		}
		check(takeoffs > 3, "held Space repeats jumps upon landing");
		check(highest > 1.2 && highest < 1.3, "held Space cannot fly or jump again at apex");
	}

	private static void testFlight() {
		PlaceholderMovement up = grounded(), diagonal = grounded();
		PlaceholderMovement.Collision forbidden = d -> d; // ordinary ability flight queries collision
		for (int i = 0; i < 300; i++) {
			up.tick(0, 0, 1, 0, NORMAL, true, false, false, true, -1000, 1000, forbidden);
			diagonal.tick(1, 0, 1, 0, NORMAL, true, true, false, true, -1000, 1000, forbidden);
		}
		close(up.y(1), diagonal.y(1), "horizontal input independent of ascent");
		double before = diagonal.z(1), y = diagonal.y(1);
		diagonal.tick(1, 0, 1, 0, NORMAL, true, true, false, true, -1000, 1000, forbidden);
		close(.098 / .09, diagonal.z(1) - before, "sprint flight terminal speed");
		close(.375, diagonal.y(1) - y, "flight vertical damping remains .6");
		PlaceholderMovement m = grounded();
		m.reset(0, 1, 0, 0, .2, 0, false);
		m.tick(0, 0, 1, 0, NORMAL, true, false, false, true, 0, 1, forbidden);
		close(1, m.y(1), "upper bound");
		m.tick(0, 0, -1, 0, NORMAL, true, false, false, true, 0, 1, forbidden);
		close(.85, m.y(1), "bound clears outward velocity");
		m.reset(0, 1, 0, 0, 0, 0, false);
		m.tick(1, 0, 0, 0, NORMAL, true, false, false, true, -64, 320, d -> new PlaceholderMovement.Motion(0, d.y(), 0));
		close(0, m.z(1), "ordinary flight cannot pass walls");
		m.tick(1, 0, 0, 0, NORMAL, true, false, false, true, -64, 320, true,
			d -> { throw new AssertionError("explicit noclip must bypass collision"); });
		check(m.z(1) > 0, "explicit noclip passes walls");
	}

	private static void testControls() {
		PlaceholderControls c = new PlaceholderControls();
		c.reset(true, false, true, true);
		for (int i = 0; i < 30; i++) keys(c, true, true, true);
		check(c.sprinting(), "held sprint survives handoff");
		check(!c.flying(), "held Space at handoff must NOT fly");
		keys(c, true, false, true);
		keys(c, true, true, true);
		check(!c.flying(), "handoff-held Space did not arm a first tap");
		keys(c, true, false, true);
		keys(c, true, true, true);
		check(c.flying(), "only double tap enables flight");
		keys(c, true, false, true);
		keys(c, true, true, true);
		keys(c, true, false, true);
		keys(c, true, true, true);
		check(!c.flying(), "double tap disables flight");
		c.reset(false, false, false, false);
		keys(c, true, false, true);
		keys(c, false, false, true);
		keys(c, true, false, true);
		check(c.sprinting(), "double W starts sprint");
		keys(c, false, false, true);
		check(!c.sprinting(), "release W stops sprint");
		c.reset(false, false, false, false);
		keys(c, true, true, false);
		keys(c, false, false, false);
		keys(c, true, true, true);
		check(!c.sprinting() && !c.flying(), "menu typing cannot arm gestures");
		c.reset(false, false, false, false);
		for (int i = 0; i < 4; i++) c.tick(i % 2 == 0, false, false, false, false, 0, true);
		check(!c.sprinting(), "zero sprintWindow disables double-W");
		c.tick(true, false, false, false, true, 0, true);
		check(c.sprinting(), "dedicated sprint key works with double-W disabled");
		c.setFlying(true);
		c.restrictFlight(false);
		check(!c.flying(), "denied ability flight cancels a local gesture");
	}

	private static void testFlyingHandoff() {
		PlaceholderControls c = new PlaceholderControls();
		c.reset(true, true, true, true);
		for (int i = 0; i < 30; i++) keys(c, true, true, true);
		check(c.flying(), "Inherited flight survives a held jump key");
		keys(c, true, false, true);
		keys(c, true, true, true);
		check(c.flying(), "First fresh tap does not cancel inherited flight");
		keys(c, true, false, true);
		keys(c, true, true, true);
		check(!c.flying(), "Double tap can cancel inherited flight");
		c.reset(false, true, false, false);
		keys(c, false, true, false);
		check(c.flying(), "Opening a menu or disabling movement does not cancel flight");
		PlaceholderMovement m = new PlaceholderMovement();
		m.reset(0, 90, 0, .2, 0, .3, false);
		for (int i = 0; i < 100; i++) {
			m.tick(0, 0, 0, 0, NORMAL, c.flying(), false, false, true, -64, 320,
				d -> d);
		}
		close(90, m.y(1), "Flying handoff does not acquire gravity");
		check(!m.onGround(), "Flying handoff is not a landing");
		c.reset(false, false, false, false);
		check(!c.flying(), "A new walking/synthetic player never inherits the previous sandbox's flight");
	}

	private static void keys(final PlaceholderControls c, final boolean forward, final boolean jump, final boolean enabled) {
		c.tick(forward, false, jump, false, false, 7, enabled);
	}

	private static void check(final boolean condition, final String message) {
		assertions++;
		if (!condition) throw new AssertionError(message);
	}
	private static void close(final double expected, final double actual, final String message) {
		check(Double.isFinite(actual) && Math.abs(expected - actual) <= 1.0E-8, message + ": expected " + expected + ", got " + actual);
	}
}
