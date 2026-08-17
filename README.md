# NoLoadingScreen

**English** | [简体中文](README_zh-CN.md)

[![build](https://github.com/BingKKni/NoLoadingScreen/actions/workflows/build.yml/badge.svg)](https://github.com/BingKKni/NoLoadingScreen/actions/workflows/build.yml)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-brightgreen)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-dbd0b4)](https://fabricmc.net/)
[![Environment](https://img.shields.io/badge/Environment-Client-blue)]()
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

**Stop staring at loading screens while joining a world.**

NoLoadingScreen is a client-side Fabric mod that hides Minecraft's "Loading terrain" and "Reconfiguring" screens when creating or joining a world, changing dimensions, or switching servers. Instead, it displays an interactive placeholder or the world you just left while the new world and its chunks stream in.

> NoLoadingScreen primarily changes how loading is presented and when the client enters the world, while also optimizing parts of the world-loading process.

## Features

- Replaces loading screens and black frames with a free-look placeholder void
- Keeps the previous world visible while switching servers
- Can enter the world before the first chunk arrives while holding the player in place until it arrives
- Keeps the client responsive while an integrated singleplayer server starts
- Shows the current loading phase, elapsed time, and progress on the HUD
- Reports total join time and a per-phase breakdown in chat and logs
- Does not create, delay, or reorder network packets

## Installation

Requirements:

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer
- Java 25 or newer

1. Install [Fabric Loader](https://fabricmc.net/use/installer).
2. Download `noloadingscreen-x.y.z.jar` from [Releases](https://github.com/BingKKni/NoLoadingScreen/releases).
3. Place the file in `.minecraft/mods/`.

This mod only needs to be installed on the client. Servers do not need it.

[Mod Menu](https://modrinth.com/mod/modmenu) is optional but recommended for changing settings in game. Without it, you can edit the configuration file directly.

## Configuration

Open the settings screen through Mod Menu, or edit `.minecraft/config/noloadingscreen.json`.

| Option | Default | Description |
|:--|:--:|:--|
| Enable Mod | On | Master switch; disabling it restores vanilla behavior |
| Allow Movement While Loading | On | Allows movement in the placeholder with movement keys, Space to rise, and Shift to sink |
| In-World Loading Overlay | On | Shows the loading phase, elapsed time, and progress on the HUD |
| Show Join Time | Off | Prints total and per-phase join times in chat after entering the world |

## Compatibility

- Tested with Sodium 0.9.2 Alpha 4.
- No other known incompatibilities at this time.
- Mods that alter world loading, loading screens, or the client readiness gate may conflict with NoLoadingScreen.

## Known Behavior

- The mod releases the client readiness gate immediately, so the world may briefly appear empty before chunks arrive. This is expected.
- The player's position is held until the current chunk arrives to prevent client-side falling.
- If the placeholder world cannot be created, the mod falls back to its minimal loading screen.

## Technical Details

Interested in the loading state machine, placeholder world, packet safety, Mixin injection points, and test results? Read
[Implementation Details and Measurements](docs/TECHNICAL_DETAILS.md).

## Building From Source

```bash
./gradlew build
```

Build artifacts are written to `build/libs/`.

## License

[MIT](LICENSE) (c) 2026 BingKKni ([BingKKni](https://github.com/BingKKni))
