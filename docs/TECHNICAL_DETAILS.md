# NoLoadingScreen: Implementation Details and Measurements

[Back to English README](../README.md) | [简体中文技术文档](TECHNICAL_DETAILS.zh-CN.md)

> This document is intended for developers who want to understand the internal implementation, compatibility design, and testing process. For installation and everyday use, see the project README.

[![build](https://github.com/BingKKni/NoLoadingScreen/actions/workflows/build.yml/badge.svg)](https://github.com/BingKKni/NoLoadingScreen/actions/workflows/build.yml)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-brightgreen)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-dbd0b4)](https://fabricmc.net/)
[![Environment](https://img.shields.io/badge/Environment-Client-blue)]()
[![License](https://img.shields.io/badge/License-MIT-yellow)](../LICENSE)

**Remove loading screens from the world-entry flow.** When logging in, changing dimensions, going through a portal, or switching servers, vanilla fills the entire window during the wait with a panel
("Loading terrain," "Reconfiguring," or simply a black screen). This mod replaces that panel with the world itself:

1. **Show something worth looking at before the world arrives.** Before the login packet arrives, the client's `level` and `player` are both `null`,
   and those two fields block the entire world-rendering pipeline. That is the **only** reason the panel exists.
   This mod temporarily fills them in: when switching servers, it keeps using the world you just left; when opening a singleplayer world, it synthesizes a void with a sky.
   Mouse control remains yours, so you can freely look around and float until the real world arrives. **The [placeholder world](#1-placeholder-world-core) is the main body of this mod.**
2. **Hand the view back as soon as the world arrives.** Vanilla holds the player on the "Loading terrain" screen until the chunk underfoot has been **fully meshed**.
   However, collision geometry is already solid as soon as the chunk data arrives; meshing is purely a visual concern. This mod opens vanilla's own readiness gate early,
   so the screen never appears and chunks stream into view one by one.
3. **State clearly what is currently being awaited.** Vanilla's panel contains no text at all when switching servers. This mod displays the current phase
   (starting world / synchronizing data / waiting for world / receiving chunks) and the elapsed wait time, and writes a one-line phase breakdown to the log for every world entry.

This is a client-only Fabric mod that **does not create, intercept, delay, or reorder any packet sent to the server**.

> **WARNING: To be clear about what this mod does not do: it does not make world entry faster.**
> A client mod cannot save even one second of the time spent by the server. Hypixel's "Reconfiguring" phase during a server switch is the server resending registries and tags,
> which accounted for 80-98% of the entire measured world-entry process. The one-second delay when opening a singleplayer world is the integrated server starting and preparing the spawn point; this mod **never changes server-side timing**.
> It changes **what is displayed during that time**: instead of a panel, you get a world in which you can freely look around.

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer) >= 0.19.3 (Minecraft 26.2 requires Java 25).
2. Put `noloadingscreen-x.y.z.jar` in `.minecraft/mods/`.
3. That is all: **Fabric API and other prerequisites are not required.**

[Mod Menu](https://modrinth.com/mod/modmenu) is optional. If installed, it provides access to the graphical configuration screen; otherwise, edit
`.minecraft/config/noloadingscreen.json` directly.

---

## What Exactly Is the Game Waiting For During World Entry?

In 26.2, the world-entry waiting logic resides in `net.minecraft.client.multiplayer.LevelLoadTracker`, a three-state state machine:

```
WaitingForServer
      |  ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START received
      v
WaitingForPlayerChunk        <- where the "Loading terrain" screen remains
      |  The chunk builder has "finished meshing" the player's section (or a 30-second timeout occurs)
      v
ClientLevelReady
      |
      v
isLevelReady() == true
```

At the moment `isLevelReady()` returns `true`, vanilla does two things **at the same time**: it closes the loading screen and sends
`ServerboundPlayerLoadedPacket`.

However, `WaitingForPlayerChunk` waits for **mesh construction to complete, not for chunk data to arrive**. Collision geometry is already solid as soon as the chunk data arrives;
meshing is purely a visual concern. The greater the render distance, the heavier the resource pack, and the more mods installed, the slower this step becomes.

**But this is not the longest phase.** Between clicking "Join World" and the arrival of `ClientboundLoginPacket`, the client does not even have a
`ClientLevel` object. The entire world-rendering block in `GameRenderer.render` is guarded as follows:

```java
boolean flag1 = flag && advanceGameTime && this.minecraft.level != null;
if (flag1) { ... this.renderLevel(deltaTracker); ... }
```

The first thing `renderLevel` does is `LocalPlayer localplayer = this.minecraft.player;`, immediately followed by a dereference without a null check.
Therefore, nothing except a full-screen UI **can possibly be displayed** during that period. The black screen is not a design tradeoff; it is the inevitable result of those two fields being null.

---

## What It Does

### 1. Placeholder World (Core)

Fill in those two fields. **There are two ways to do this, depending on whether anything is available to adopt.**

#### Server Switch: Adopt the Old World That Is About to Be Torn Down (Without Constructing Anything)

When a proxy sends the player back to the configuration phase, the world being torn down is **already available**, and it is complete: every chunk is present,
and **all chunks have already been meshed**. The best solution is therefore not to create a new world, but to **keep the existing one alive**:

- Allow vanilla's `clearClientLevel` to set `minecraft.level` / `player` to null as usual. This behavior must be preserved,
  because it ensures that every tick, every packet handler, and every other mod still follows the vanilla path.
- Intercept one call to `updateLevelInEngines(null)`, however. That call executes `levelExtractor.setLevel(null)`,
  **releasing all previously built chunk meshes**. Attaching the world again would then require remeshing the entire world, which is exactly the problem this mod exists to avoid.
  Once the call is intercepted, the rendering engine continues to point to the old world without dropping a frame. (The only original behavior retained from this call is stopping sounds.)
- Temporarily pass the old level / player back during each rendered frame.

The result is **the place you just left, frozen in time**, with free camera rotation. **No objects are instantiated anywhere along this path.**

#### Singleplayer World Load: Synthesize a Void

There is nothing to adopt when opening a singleplayer world, so construction is required only here: a complete, ordinary `ClientLevel`
(empty chunks, plains biome, noon, clear weather) and a `LocalPlayer` standing in it.

**WARNING: Constructing a `ClientPacketListener` has a cost, which was discovered only through testing on a real client:** its constructor is hooked by other mods.
Fabric API's networking module registers the new listener there as the single global client play addon and immediately throws an exception if one already exists:

```
java.lang.IllegalStateException
    at ClientNetworkingImpl.setClientPlayAddon(ClientNetworkingImpl.java:126)
    at ClientPacketListener.handler$...$fabric-networking-api-v1$initAddon
    at ClientPacketListener.<init>
```

**Therefore, only the singleplayer world-load path constructs one**, and that path happens to be safe: no play session exists at that time, which is precisely why there is nothing to adopt.
Even so, the global slot is saved before construction and restored afterward (see `PlayAddonGuard`). Otherwise, this listener, which is about to be discarded,
would continue to occupy the slot and cause the same exception when the real listener is created. At that point, world entry would already be in progress and could not recover.
If the slot cannot be accessed safely because of internal Fabric changes, this mod **declines to construct the placeholder** and falls back to the vanilla screen.

#### Why This Is Safe for the Server

- Regardless of which listener the placeholder world uses, its underlying `Connection` has **no channel**.
  When `isConnected()` is false, `Connection#send` places packets in the `pendingActions` queue, and the object is discarded immediately afterward.
  An adopted listener has its connection replaced (`ClientCommonPacketListenerImplAccessor`); a synthesized listener is created this way from the start.
  This means that any packet sent during binding through `minecraft.getConnection()` (whose vanilla implementation is simply `player.connection`)
  **physically cannot reach the server**, regardless of the protocol phase.
- The placeholder player is **never ticked**. It is not part of any `tickEntities` path, and neither `aiStep` nor `sendPosition` runs even once.
  The mod moves it by directly changing its coordinates on each tick.
- As soon as the real world arrives, the entire placeholder is discarded, and vanilla creates the real player at the vanilla point in time using **the position and view rotation supplied by the server**.
  None of the rotation or movement performed in the void carries over, so the server has nothing to correct,
  and there is no scenario in which "the client moves several blocks early and gets pulled back."
- **Binding occurs only during a single rendered frame.** `minecraft.level` / `player` / `gameMode` are populated only around the `renderFrame` call and mouse-look rotation,
  then immediately restored to null. Therefore, **every client tick, every packet handler, and every other mod still sees vanilla's
  null values**. This is the key to minimizing the affected surface area.

The only place requiring additional handling is `Minecraft#handleKeybinds`. Vanilla reaches it only when no screen is open, and in vanilla,
"no screen is open" implies that a player exists. Every branch dereferences `this.player`, and half the branches also send packets. It is therefore skipped entirely while the placeholder is active
(clicks accumulated during that period are discarded as well, preventing them from firing all at once when the world opens).

#### It Must Not Be Allowed to Crash the Game

The placeholder world is a **purely decorative** feature positioned in front of the world-entry flow. The entire `renderFrame` call is therefore wrapped with `@WrapMethod`:
if a frame throws during binding, the exception is **written to the log in full**, the placeholder world is discarded, the rendering engine is returned, and the vanilla screen is restored,
rather than taking down the game. This safety net applies only to frames modified by this mod; exceptions in all other cases are rethrown unchanged.

This is not merely defensive overengineering; it came from testing on a real client. `GameRenderer#renderItemInHand` unconditionally dereferences
`minecraft.gameMode`, while `clearClientLevel` clears it **a few lines before clearing `level`**.
As a result, the snapshot captured null and the client crashed one frame later. `bind()` now binds only when level / player / gameMode
**are all present**, and the adoption path supplies its own gameMode when one is missing.

##### WARNING: The Cost of Swallowing One Frame: The Swapchain Image Must Be Returned

This is a problem introduced by the safety net itself. `renderFrame` has the following structure:

```java
public void renderFrame(boolean advanceGameTime) {
   if (this.windowSurface.isAcquired()) return;   // <- first line
   ...
   this.windowSurface.acquireNextTexture();       // Acquire a swapchain image
   ...
   this.gameRenderer.extract(...);                // The exception occurs after this point
   this.gameRenderer.render(...);
   ...
   if (this.windowSurface.isAcquired()) this.windowSurface.present();   // The only return point
}
```

**An exception in the middle -> `present()` does not run -> the image remains acquired -> every subsequent frame returns immediately on the first line.**
The game continues running (ticks, chat, and world entry all work, with nothing suspicious in the log), but **the image remains frozen on the final frame forever**,
and shutdown ends with `Shutdown failure! Cannot close a surface while it is acquired`.
**That is far worse than the loading screen the mod is intended to eliminate.**

After swallowing a frame, the mod now completes the three essential operations from vanilla's tail (blit -> submit -> present) to return the image.
If even that step fails, the **original exception is rethrown unchanged**. A crash report is far better than a frozen window pretending to be alive.
In addition, after the placeholder world fails three times during the current run, it is **disabled for the remainder of that run** (with an explanatory log entry). Subsequent joins use the corresponding loading screen, while early gate release remains active.

### 2. Open the Gate Early

`LevelLoadTracker` has a public method, `getPlayerCompiledSectionCallback()`, which returns the same callback vanilla uses to mark
"the player's section is ready." Once that callback is available at the HEAD of `tickClientLoad()`, this mod directly invokes the **vanilla callback**.

The vanilla state machine then advances normally. The loading screen closes and `ServerboundPlayerLoadedPacket` is still sent by vanilla code on the same tick,
in the vanilla order.

This is why it is safe with anti-cheat systems: **the mod does not send any packet to the server.** The "ready packet"
(`ServerboundPlayerLoadedPacket`, introduced in 1.21.4) is already part of vanilla's mechanism; this mod merely causes it to trigger earlier.

The gate always opens immediately without waiting for the server to begin sending chunks. If the player's chunk has not arrived yet, the mod holds the player in place until it does, preventing client physics from dropping the player into the void.

### 3. Singleplayer No Longer Spins Idly

Vanilla's `doWorldLoad` waits as follows while the integrated server starts:

```java
while (!this.singleplayerServer.isReady() || this.gui.overlay() != null) {
   levelloadingscreen.tick();
   this.renderFrame(false);      // advanceGameTime = false
   this.runAllTasks();
   this.managedBlock(...);
}
```

This loop **occupies the client main thread**: it does not process input, advance ticks, or drive the connection. Furthermore, because
`advanceGameTime` is false in `renderFrame(false)`, the world-rendering block is skipped in the first place. This is the source of the one-second period at the beginning of singleplayer world entry during which "nothing can move";
making the screen look better does not solve it.

This wait is unnecessary. `ServerConnectionListener` is created in the `MinecraftServer` constructor (the constructor runs on the client thread inside
`spin()`, before this loop), so the in-memory channel can be opened immediately and the handshake packet can wait in the queue
until the server thread begins ticking and processes it. Removing the wait returns the client to its normal main loop, where the connection advances like any other `pendingConnection`.
The placeholder world is therefore **alive** during this period. (The half of the condition that waits for resource-pack reloading is preserved unchanged.)

**The server's own startup duration does not change at all**; only what the client does during that time changes.

### 4. About "Reconfiguring"

This screen cannot simply be discarded, because its `tick()` method contains the following logic:

```java
if (this.connection.isConnected()) {
   this.connection.tick();      // The configuration-phase connection is driven entirely here
} else {
   this.connection.handleDisconnection();
}
```

`Minecraft` itself ticks only *pending* connections. Once the screen is gone, the connection stops advancing and remains stuck until it times out.
This mod **keeps the screen object and continues ticking it, but detaches it from the Gui**. The mod invokes its
`tick()` directly on every client tick, without changing a single line of vanilla logic. Even the 600-tick delay for the "Disconnect" button continues to run.

**Pressing Esc restores this screen at any time**, because its "Disconnect" button is the only way out during the configuration phase (Esc does nothing there in vanilla).
A void that cannot be exited would be a trap. This hint remains displayed at the bottom of the void view.

### Mixin Inventory

Nine Mixins (three of which are accessors), all implemented with `@Inject` / `@Redirect` / `@ModifyVariable` / `@WrapMethod`,
with **no `@Overwrite`**:

| Class | Injection Point | Purpose |
|---|---|---|
| `Minecraft` | `renderFrame` `@WrapMethod` | Bind/unbind the placeholder world during a rendered frame, provide the exception safety net, and return the swapchain image |
| `Minecraft` | `runTick` -> `handleAccumulatedMovement` | Invoke after binding so the mouse can rotate the placeholder player's view |
| `Minecraft` | `handleKeybinds` HEAD | Skip the entire method while the placeholder is active (see above) |
| `Minecraft` | `doWorldLoad` -> `IntegratedServer.isReady` | Remove idle spinning during startup |
| `Minecraft` | `doWorldLoad` RETURN | Singleplayer: install the placeholder world |
| `Minecraft` | `setScreenAndShow` -> `renderFrame` | Skip the forced render of that frame when the screen is discarded, eliminating the black flash |
| `Minecraft` | `tick` HEAD | Drive the detached "Reconfiguring" screen and tick the placeholder world |
| `Minecraft` | `clearClientLevel` -> `updateLevelInEngines` | Preserve chunk meshes during a server switch so the old world remains on screen |
| `Minecraft` | `clearClientLevel` / `setLevel` / `disconnect` | Return the rendering engine and record diagnostic phase boundaries |
| `ClientPacketListener` | `handleConfigurationStart` x2 | Server switch: snapshot the old world -> install the placeholder world |
| `ClientPacketListener` | `handleLogin` HEAD | Remove the placeholder world when the real world arrives |
| `ClientPacketListener` | `<init>` RETURN | Checkpoint: configuration phase complete |
| `ClientPacketListener` | `startWaitingForNewLevel` RETURN | Remove the retained loading screen in singleplayer |
| `LevelLoadTracker` | `startClientLoad` / `loadingPacketsReceived` / `tickClientLoad` / `isLevelReady` | Gate logic |
| `Gui` | `setScreen` HEAD / `extractRenderState` | Discard the loading screen / render the minimal loading screen |
| `Hud` | `extractRenderState` TAIL | Loading-information overlay |
| `LocalPlayer` | `aiStep` TAIL | Lock the position until the chunk arrives, then restore normal physics |
| `ClientCommonPacketListenerImpl` | `connection` field (accessor) | Replace the adopted old listener's connection with a dead connection |
| `LevelLoadingScreen` | `loadTracker` field (accessor) | Retrieve the tracker displayed by the screen so the overlay can continue rendering it |
| `ServerReconfigScreen` | `connection` / `delayTicker` fields (accessors) | Preserve the elapsed wait time when restoring the screen |

---

## Configuration

Open the configuration through Mod Menu, or edit `.minecraft/config/noloadingscreen.json` directly.

| Option | Default | Description |
|---|---|---|
| Enable Mod | On | When disabled, all behavior is exactly the same as vanilla, and no logs are written |
| Allow Movement While Loading | On | Allows movement-key flight while the placeholder is active, with Space to rise and Shift to sink; this is purely local and sends no packets |
| Loading Information Overlay | On | After the screen is removed, continue rendering phase text, elapsed wait time, and a progress bar on the HUD |
| Show World-Entry Duration | Off | After each world entry, print the total duration and phase breakdown in chat. **The result is still written to `latest.log` when this is off** |

### Fixed Behavior

The following behavior no longer has separate switches and is always active while the mod is enabled:

- Display the placeholder before a world exists. If it is unavailable, retain a loading screen and render "Loading terrain" in the minimal style.
- Hide "Loading terrain" once a real client world exists, allowing chunks to stream in visibly.
- Avoid idling in `doWorldLoad` while the integrated server starts, keeping the singleplayer client responsive.
- Release the readiness gate immediately, without waiting for chunk transmission to start or for chunk meshing to finish.
- If the player's chunk has not arrived at release time, hold the player at the server-provided coordinates until it arrives, then restore normal physics.

The last item applies at a different stage from "Allow Movement While Loading." That setting controls purely local movement in the **placeholder world**; position holding applies after the **real world exists but the chunk underfoot has not arrived**, and cannot be disabled separately.

---

## Compatibility and Known Limitations

- **Client-only**; the server does not need the mod.
- **Verified to coexist with Sodium 0.9.2-alpha.4.** Sodium replaces the chunk builder, while the gate depends on its "meshing complete" signal.
  Testing found no conflict between the two (this mod bypasses the wait and does not participate in meshing). The placeholder world triggers two additional `LevelExtractor.setLevel` calls
  (one when installed and one when removed).
- **WARNING: During placeholder-world rendering, other mods see the placeholder world inside rendering hooks.** Binding occurs only within one rendered frame,
  so tick events and packet handlers still see vanilla's `null`; however, a mod that makes strong assumptions about
  `mc.level` / `mc.player` inside a rendering callback could theoretically behave incorrectly. The placeholder cannot currently be disabled separately;
  turn off "Enable Mod" to restore full vanilla behavior and confirm whether the mod is responsible.
- **WARNING: Client startup overlaps integrated-server startup**, causing both to compete for CPU.
  The total wall-clock time for singleplayer startup may increase slightly in exchange for keeping the window responsive during that second. This optimization cannot currently be disabled separately; turn off "Enable Mod" if the tradeoff is undesirable.
- **Does not shorten any server-side timing.** Spawn preparation, world generation, and registry synchronization still take exactly as long as before.
- If you encounter a problem, first turn off "Enable Mod" and reproduce it once to confirm whether this mod is responsible, then open an [issue](https://github.com/BingKKni/NoLoadingScreen/issues) with the log.

### Verification Status

- `./gradlew build` passes.
- **All Mixins were confirmed to apply in practice.** A temporary `preLaunch` entry point forcibly loaded every target class to trigger Mixin transformation,
  after which each transformed class was checked for the injected methods. Because `injectors.defaultRequire = 1`,
  any injection point that cannot find its target throws immediately during transformation. This also proves that the target calls for the
  `doWorldLoad -> IntegratedServer.isReady`, `runTick -> handleAccumulatedMovement`, `setScreenAndShow -> renderFrame`, and
  `clearClientLevel -> updateLevelInEngines` `@Redirect`s actually exist and were rewritten.
- The placeholder world went through **four rounds of testing on a real client**, each of which fixed an issue exposed only by real-client testing: singleplayer `setLocalMode` dereferencing a null player;
  multiplayer construction of `ClientPacketListener` colliding with Fabric API's global play-addon slot; `gameMode` being null during cross-server rendering
  and crashing in `renderItemInHand`; and, in the final round, **the safety net itself freezing the window** (because it swallowed a frame without returning the swapchain image;
  see above). Each of these four cases now has dedicated handling.
- Every placeholder failure is written to `latest.log`, and the corresponding loading screen continues the join; "Loading terrain" uses the minimal style.
  During diagnosis, search for `Could not build`, `Could not adopt`, `Could not install`, or `failed to render`. The failure line includes the complete state at the failure point
  (`mode=` / `bindDepth=` / `phase=` / whether each of the three fields is null).
- The gate was tested on a real client in Hypixel SkyBlock (with Sodium 0.9.2, Skyblocker, and 148 mods in total).

### Where the Time Goes (Measured)

Minecraft 26.2 + Fabric, 148 mods, Hypixel SkyBlock: a server switch took approximately 1.0-1.2 seconds in total, of which
**80-92% was spent waiting for the server** to resend registries and tags; in a newly created singleplayer world, 98% was world generation. The "terrain loading" phase,
which is the only phase the gate can affect, measured only **27-201 ms**, and the recorded state at release was always "player chunk arrived = true."

**Therefore, this mod does not promise faster world entry.** It promises that during those 80-98% of the total time, the screen will no longer be covered by a panel.
To see where each world entry spends its time, check the log for
`Join finished in ... ms [configuring 812ms, waitingworld 143ms, receivingchunks 74ms]`,
or enable "Show World-Entry Duration" to view it directly in chat.

---

## Build

```bash
git clone https://github.com/BingKKni/NoLoadingScreen.git
cd NoLoadingScreen
./gradlew build
```

The artifact is written to `build/libs/noloadingscreen-1.0.0.jar`. **JDK 25** is required.

To publish a release, push a tag beginning with `v`. CI uses the version number from the tag to build and automatically create a GitHub Release with the jar attached.

```bash
git tag v1.0.1 && git push origin v1.0.1
```

> Minecraft 26.1 is the first stable release whose client is **not obfuscated**; 1.21.11 was the last obfuscated release ([Mojang announcement](https://www.minecraft.net/en-us/article/removing-obfuscation-in-java-edition), [Fabric confirmation](https://fabricmc.net/2026/03/14/261)). The 26.1 `version_manifest` no longer contains `client_mappings`, and Yarn stopped at 1.21.11.
> Therefore, this project does not require any mappings. Loom 1.17 also removed remapping configurations such as `modImplementation`; mod dependencies are ordinary
> `implementation` / `compileOnly` dependencies.

The configuration screen uses vanilla `OptionInstance` + `OptionsSubScreen` controls and **does not depend on Cloth Config or YACL**.
Each library is approximately 1.07 MB, dozens of times the size of the entire mod; adding a mandatory prerequisite for such a small screen is not worthwhile.

---

## License

[MIT](../LICENSE) Copyright 2026 BingKKni ([BingKKni](https://github.com/BingKKni))
