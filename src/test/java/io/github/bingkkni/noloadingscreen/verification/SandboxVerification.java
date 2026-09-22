package io.github.bingkkni.noloadingscreen.verification;

import io.github.bingkkni.noloadingscreen.CapeState;
import io.github.bingkkni.noloadingscreen.ClientCommands;
import io.github.bingkkni.noloadingscreen.NoLoadingScreenConfig;
import io.github.bingkkni.noloadingscreen.PlaceholderCommands;
import io.github.bingkkni.noloadingscreen.PlaceholderWorld;
import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.world.phys.Vec3;

/** Same transformed visual-state/command/config regression on every API family and loader. */
public final class SandboxVerification {
	private static int assertions;
	public static void run() throws ReflectiveOperationException {
		var config = new NoLoadingScreenConfig();
		check(!config.allowFlightAndNoclip && !config.retainWorldOnKick, "New behaviors are opt-in");
		check(config.waitSeconds() == 30, "Default multiplayer timeout");
		for (int input : new int[]{Integer.MIN_VALUE, -1, 0, 1, 3, 30, 60, 61, Integer.MAX_VALUE}) {
			config.multiplayerWaitSeconds = input;
			check(config.waitSeconds() == (input == 0 ? 0 : Math.clamp(input, 3, 60)), "Validated finite/infinite timeout");
		}
		var state = new ClientAvatarState();
		Vec3 position = new Vec3(0, 80, 0);
		state.tick(position, Vec3.ZERO);
		for (int i = 0; i < 40; i++) {
			position = position.add(0, 0, .28);
			state.tick(position, new Vec3(0, 0, .28));
			state.addWalkDistance(.168F);
			state.updateBob(.1F);
		}
		CapeState.Snapshot moving = ((CapeState) state).nls$capture(position);
		check(moving.current().z < -.5, "Walking cape lags behind the body");
		check(!moving.current().equals(moving.previous()), "Cape interpolation retains its last two positions");
		var incoming = new ClientAvatarState();
		Vec3 serverPosition = new Vec3(4096, -20, -3000);
		((CapeState) incoming).nls$restore(moving, serverPosition, 0);
		CapeState.Snapshot copied = ((CapeState) incoming).nls$capture(serverPosition);
		near(copied.current(), moving.current(), "New world keeps relative cape position");
		near(copied.previous(), moving.previous(), "New world keeps cape velocity/interpolation");
		check(copied.walk() == moving.walk() && copied.bob() == moving.bob(), "Handoff does not restart walk/bob phase");
		incoming.tick(serverPosition, Vec3.ZERO);
		check(((CapeState) incoming).nls$capture(serverPosition).current().length() > .2, "First real tick settles instead of snapping to initial pose");
		((CapeState) incoming).nls$restore(moving, serverPosition, 90);
		near(((CapeState) incoming).nls$capture(serverPosition).current(), moving.current().yRot((float) -Math.PI / 2), "Server yaw rotates relative cape offset");

		var installed = PlaceholderWorld.class.getDeclaredField("installed");
		installed.setAccessible(true);
		boolean previous = installed.getBoolean(null);
		try {
			installed.setBoolean(null, true);
			boolean enabled = NoLoadingScreenConfig.get().enabled;
			try {
				NoLoadingScreenConfig.get().enabled = false;
				check(io.github.bingkkni.noloadingscreen.NoLoadingScreen.isLoading(), "Disabling the mod inside a retained scene cannot release outgoing-message guards");
			} finally { NoLoadingScreenConfig.get().enabled = enabled; }
			for (String mode : new String[]{"survival", "creative", "adventure", "spectator"}) {
				String text = "/gamemode " + mode.substring(0, 2);
				check(PlaceholderCommands.suggest(text, text.length()).join().getList().stream().anyMatch(s -> s.getText().equals(mode)), "Local TAB completion: " + mode);
			}
			check(PlaceholderCommands.suggest("/op ", 4).join().isEmpty(), "Remote commands are not offered");
			installed.setBoolean(null, false);
			check(PlaceholderCommands.suggest("/gamemode c", 11).join().isEmpty(), "Local dispatcher never replaces the live server dispatcher");
		} finally { installed.setBoolean(null, previous); }
		for (String root : new String[]{"nls", "noloadingscreen", "nlsdebug"}) {
			check(ClientCommands.owns("/" + root) && ClientCommands.owns("/" + root + " invalid"), "Local command ownership: " + root);
			check(!ClientCommands.owns("/" + root + "other"), "No prefix hijack: " + root);
			check(ClientCommands.suggest("/n", 2).join().getList().stream().anyMatch(s -> s.getText().equals(root)), "Live root TAB: " + root);
		}
		check(ClientCommands.suggest("/nlsdebug k", 11).join().getList().stream().anyMatch(s -> s.getText().equals("kick")), "Debug subcommand TAB");
		check(ClientCommands.suggest("/gamemode ", 10).join().isEmpty(), "Live server command suggestions remain remote");
		check(ClientCommands.suggest("/nls", 0).join().isEmpty(), "Cursor before slash is safe");
		check(!ClientCommands.execute("/say unchanged"), "Unrelated commands are not intercepted");
		System.out.println("SandboxVerification: " + assertions + " assertions passed (defaults, bounds, cape continuity, local completions).");
	}
	private static void near(Vec3 actual, Vec3 expected, String message) { check(actual.distanceTo(expected) < 1.0E-5, message); }
	private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
}
