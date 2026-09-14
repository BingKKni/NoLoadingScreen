# NoLoadingScreen

**English** | [简体中文](README_zh-CN.md)

[![build](https://github.com/BingKKni/NoLoadingScreen/actions/workflows/build.yml/badge.svg)](https://github.com/BingKKni/NoLoadingScreen/actions/workflows/build.yml)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.2%20%7C%201.21.10%20%7C%201.21.11-brightgreen)](https://www.minecraft.net/)
[![Loaders](https://img.shields.io/badge/Loader-Fabric%20%2F%20NeoForge-dbd0b4)](docs/NEOFORGE.md)
[![Environment](https://img.shields.io/badge/Environment-Client-blue)]()
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

**Stop staring at loading screens while joining a world.**

NoLoadingScreen is a client-side Fabric / NeoForge mod that hides Minecraft's "Loading terrain" and "Reconfiguring" screens when creating or joining a world, changing dimensions, or switching servers. Instead, it displays an interactive placeholder or the world you just left while the new world and its chunks stream in.

> NoLoadingScreen primarily changes how loading is presented and when the client enters the world, while also optimizing parts of the world-loading process.

## AI Disclosure

Except for the artwork and README resources, all other content in this mod, including its code, is AI-generated. The English README is translated by AI.

## Features

- Replaces loading screens and black frames with a free-look placeholder void
- Keeps the previous world visible while switching servers, preserving ability flight and momentum; double-tap Space to toggle local flight
- Press Esc during a server switch to resume the placeholder view or disconnect immediately
- KickWarn: unexpected disconnects, kicks and failed switches leave an interactive offline scene indefinitely; chat immediately shows the original reason, colours and line breaks. Leave via Esc → Disconnect
- Shows the local third-person player even without chunk meshes; walking, arm and camera animations continue while other entities remain frozen
- Press E for a local inventory with a player preview and stack manipulation; inventory/chat screens no longer suspend momentum, gravity or animation
- Each left-button press instantly breaks at most one block; holding while turning cannot break more. Right-click places held blocks with survival consumption, number keys/wheel select hotbar slots and F swaps hands; selected-item names fade on the vanilla timer
- Middle-click picks the targeted block into the hotbar using creative-style selection, retaining client-available NBT/components including player-head profiles; Ctrl is not required
- After a kick, adventure/spectator mode becomes local survival; existing survival/creative mode and sandbox flight are preserved
- Local block/inventory edits are discarded when loading ends and are not sent to the server
- Can enter the world before the first chunk arrives while holding the player in place until it arrives
- Keeps the client responsive while an integrated singleplayer server starts
- During singleplayer saving, keeps the outgoing scene interactive: mouse look, F5, E, movement/flight and hotbar selection remain local until saving finishes
- Enters a placeholder during singleplayer resource preparation and from multiplayer encryption (joining fallback for offline-mode servers)
- Reuses the same scene/camera from singleplayer resource preparation through server startup, without an intermediate screen or camera reset
- Shows only loading text, elapsed time and the progress bar; removes the central chunk-status rectangle
- Reports total join time and a per-phase breakdown in chat and logs
- Resolves first-join types and precompiles supported Sodium terrain shaders during startup completion
- Uses one Sodium worker for the synthetic void and coalesces the first redundant reload of an unused renderer
- Routes loading-time synchronous vanilla chunk builds to background workers and avoids Sodium's render-thread build waits, except explicit full-frame capture modes
- Spreads incoming packet handling and ordinary client tasks across frames without creating, dropping or reordering protocol packets; some handling and replies occur on later frames

## Installation

Choose the matching target; do not mix loader/game-version artifacts:

| Minecraft | Loader | Java |
|---|---|---|
| 26.2 | Fabric Loader ≥0.19.3 | 25 |
| 1.21.10 | NeoForge21.10.64 | 21 |
| 1.21.11 | NeoForge21.11.45 | 21 |

1. Install [Fabric Loader](https://fabricmc.net/use/installer) or [NeoForge](https://neoforged.net/) for your target.
2. Obtain the matching artifact from [Releases](https://github.com/BingKKni/NoLoadingScreen/releases), or build it using the [support/build matrix](docs/NEOFORGE.md). Fabric keeps `noloadingscreen-x.y.z.jar`; NeoForge names include both loader and Minecraft version.
3. Place the file in `.minecraft/mods/`.

This mod only needs to be installed on the client. Servers do not need it.

Fabric optionally uses [Mod Menu](https://modrinth.com/mod/modmenu). NeoForge exposes the same settings in its Mod list. The configuration file also remains editable directly.

## Configuration

Open the settings screen through Mod Menu, or edit `.minecraft/config/noloadingscreen.json`.

| Option | Default | Description |
|:--|:--:|:--|
| Enable Mod | On | Master switch; disabling it restores vanilla behavior |
| Allow Movement While Loading | On | Walk, sprint and jump locally; double-tap Space to toggle flight/noclip, then use Space/Shift to ascend/descend |
| In-World Loading Overlay | On | Shows the loading phase, elapsed time, and progress on the HUD |
| Show Join Time | Off | Prints total and per-phase join times in chat after entering the world |

## Compatibility

NeoForge1.21.10 /1.21.11: exact Sodium0.7.3 /0.8.14 and ViaForge4.3.1 /4.3.2 checks, broader optional matrix, GPU-smoke evidence and limits are documented [here](docs/NEOFORGE.md). Neither mod is required or bundled.

The following existing results concern **Fabric26.2**:

- Sodium-private optimizations are verified for the current stable `0.9.2+mc26.2`. Unknown or pre-release Sodium versions safely skip private hooks and retain their native rendering path.
- One combined headless Mixin target-transformation matrix passed with Sodium 0.9.2, Iris 1.11.4, ImmediatelyFast 1.16.4, Lithium 0.25.3, FerriteCore 9.0.0, EntityCulling 1.10.5, MoreCulling 1.8.1, Dynamic FPS 3.11.9, Sodium Extra 0.9.3, and RRLS 5.2.8, while ViaFabricPlus 5.0.1 was loaded alongside them. Most integrations need no mod-specific hook and rely on the existing chainable vanilla lifecycle hooks rather than private third-party APIs.
- An extended target-transformation profile also passes for Bobby 5.2.15, Distant Horizons 3.2.0-b, FastQuit 3.1.5, and Reese's Sodium Options 2.2.3 on Minecraft 26.2. Nvidium is not certified in this profile because its current 26.2 artifact requires Sodium 0.9.1 while the verified profile uses Sodium 0.9.2.
- Actual-jar headless checks also pass for ViaFabricPlus 4.6.1 and 5.0.1 input/lifecycle targets, including the Sodium 0.9.2 + Fabric API 0.158.0 + ViaFabricPlus 5.0.1 loading-policy regression.
- These checks prove class loading and NoLoadingScreen target transformation for the named versions; they do not prove GPU/shader, full-modpack FPS, or real-server behavior. Other mods that replace world loading, loading screens, rendering backends, or the readiness gate may still conflict.

## Known Behavior

- Warm-up adds startup-completion time, not a zero-stall guarantee. The isolated test still measured roughly 200 ms for first placeholder installation.
- The preloaded account skin bridges the transition to the real local player until server textures are ready. Initial downloads can still require a default skin.
- Background meshing and frame budgets do not guarantee zero stalls: individual packet handlers, renderer teardown, GPU uploads, drivers and resource reloads can still block. Loading protection continues for five seconds after early readiness; slow stages over 100 ms produce limited `[loading-work]` diagnostics. See [findings and limitations (Chinese)](docs/LOADING_IMPROVEMENTS.zh-CN.md).
- The mod releases the client readiness gate immediately, so the world may briefly appear empty before chunks arrive. This is expected.
- The missing-chunk hold follows server teleports without locking mouse look, fabricating a landing, or overriding the new server's flight permissions.
- Early placeholders use asynchronously prepared private vanilla registries. Until these are ready, or on a cosmetic failure, the corresponding vanilla loading screen remains available.
- Picking only copies data already received by the client; it never queries additional server NBT. Unavailable block entities are not picked as data-less defaults.
- KickWarn does not keep a server connection alive or bypass bans/protocol restrictions. It reports actual disconnect events; unresponsive servers still use vanilla's network timeout. Cosmetic failures retain the original error screen.
- Authentication, encryption, configuration and saving are not skipped. Local controls do not change the real player's position, flight permissions or inventory. This is not a guarantee of acceptance by every server's anti-cheat policy.

## Technical Details

Interested in the loading state machine, placeholder world, packet safety, Mixin injection points, and test results? Read
[Implementation Details and Measurements](docs/TECHNICAL_DETAILS.md).

## Building From Source

```bash
./gradlew build
```

This root command builds Fabric26.2 into `build/libs/`. NeoForge targets use separate `-p platforms/neoforge-1.21.10` / `-p platforms/neoforge-1.21.11` builds; see [commands and artifact names](docs/NEOFORGE.md). The build also runs movement-model and headless Mixin regression checks. See the [manual transfer-testing checklist (Chinese)](docs/TESTING.zh-CN.md) for in-game validation.

## License

[MIT](LICENSE) (c) 2026 BingKKni ([BingKKni](https://github.com/BingKKni))
