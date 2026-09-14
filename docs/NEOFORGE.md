# NeoForge 1.21.10 / 1.21.11

> 本文保留原有 1.21 支持与验证记录。新增 Fabric / NeoForge / Forge 26.x 的独立构建、图标检查和验证边界见 [版本矩阵](VERSIONS.md)。原有两个 NeoForge 目标不变；1.21 的保存退出输入、疾跑 FOV、物品提示修复与 `verifyRepairGpu` 回归见 [测试说明](TESTING.zh-CN.md)。

## 支持与构建 / Support and builds

三套独立构建，共享功能状态机；**不能混用不同加载器/游戏版本的 JAR**。NeoForge 使用正常映射工具链，不使用 Mojang 的独立 unobfuscated 实验版。

| Target | Loader / Java | Command (repository root) | Main artifact (version 1.1.0) |
|---|---|---|---|
| Fabric 26.2 | Fabric Loader ≥0.19.3 / Java25 | `./gradlew build check` | `build/libs/NoLoadingScreen-1.1.0-Fabric-26.2.jar` |
| NeoForge 1.21.10 | NeoForge21.10.64 / Java21 | `./gradlew -p platforms/neoforge-1.21.10 build check` | `platforms/neoforge-1.21.10/build/libs/NoLoadingScreen-1.1.0-NeoForge-1.21.10.jar` |
| NeoForge 1.21.11 | NeoForge21.11.45 / Java21 | `./gradlew -p platforms/neoforge-1.21.11 build check` | `platforms/neoforge-1.21.11/build/libs/NoLoadingScreen-1.1.0-NeoForge-1.21.11.jar` |

NeoForge metadata accepts exactly its named Minecraft release and NeoForge `[21.10.64,21.11)` / `[21.11.45,21.12)` respectively. Only the pinned loader versions above were tested. Gradle9.5.1 and ModDevGradle2.0.147 are used; no global Gradle/JDK configuration changes are needed. If toolchain discovery needs help, supply your own local path with `-Dorg.gradle.java.installations.paths=/path/to/jdk21`. `-Pversion=...` overrides the artifact version. Sources JARs are built alongside the main artifacts.

仅客户端安装。NeoForge 的 Mod 列表可直接打开同一套原版样式设置页；Fabric 仍可选装 Mod Menu。配置文件与四个设置项保持一致。上述构建命令不发布任何文件。

## 官方未混淆实验版 / Official unobfuscated experiment

正常发布的 1.21.10、1.21.11 仍是混淆 JAR + 官方映射，并不等于独立实验构建。Mojang 的 [1.21.11 官方实验 ZIP](https://piston-data.mojang.com/v1/objects/82332dfb17146de34cb7a36d2b910e3b2009191a/1_21_11_unobfuscated.zip)（已校验 SHA1 `82332dfb17146de34cb7a36d2b910e3b2009191a`）内的启动器 ID 为 `1.21.11_unobfuscated`；其独立 client/server 对象分别为 `4509ee9b65f226be61142d37bf05f8d28b03417b`、`3ca78d5068bf9b422f694d3f0820e289581c0f0d`。[实验版本历史](https://minecraft.wiki/w/Unobfuscated_version)记载从25w45a开始；本次检索的官方清单及该历史中未找到1.21.10的独立实验包。这里仅陈述已检索范围，不把未找到当作未来不会发布的保证。

The separate experiment is **not** a replacement for the normal release artifacts used by NeoForge. Both builds here retain the standard NeoForm mapped toolchain; experimental client/server JARs must not be substituted into a normal NeoForge installation.

## Optional mods / 可选模组

Neither Sodium nor ViaForge is bundled or required. Sodium private hooks use **exact version equality**, not prefix matching; absent/unknown versions retain their own normal renderer path.

| Minecraft | Sodium NeoForge | ViaForge upstream release | ViaForge filename / mod metadata |
|---|---|---|---|
| 1.21.10 | `0.7.3+mc1.21.10` | 4.3.1 | `viaforge-mc1219-4.3.1.jar` / `viaforge=1.21.10` |
| 1.21.11 | `0.8.14+mc1.21.11` | 4.3.2 | `viaforge-mc12111-4.3.2.jar` / `viaforge=4.3.2` |

1.21.10 的 ViaForge 文件名虽然带 `mc1219`，上游版本声明支持1.21.10。这里指 [ViaForge](https://modrinth.com/mod/viaforge)，不是仅覆盖其他版本的 ViaNeoForgePlus。Sodium 使用[上游 NeoForge 发布包](https://modrinth.com/mod/sodium)及其原始内嵌依赖。

Additional combined **target-transformation** checks passed with these unmodified optional releases (not a gameplay certification):

| Mod | 1.21.10 | 1.21.11 |
|---|---|---|
| Iris | 1.9.7 | 1.10.7 |
| Lithium | 0.20.1 | 0.21.4 |
| FerriteCore | 8.1.0 | 8.2.0 |
| ImmediatelyFast | 1.13.6 | 1.14.3 |
| EntityCulling | 1.10.5 | 1.10.5 |
| Dynamic FPS | 3.11.6 | 3.11.6 |
| Sodium Extra | 0.7.1 | 0.9.3 |
| Reese's Sodium Options | 1.8.6 | 2.2.3 |
| Cloth Config | 20.0.149 | 21.11.153 |
| MoreCulling | not in this matrix | 1.6.2 |

```bash
./gradlew -p platforms/neoforge-1.21.10 verifyCompatibility \
  -PcompatibilityJars="/path/to/sodium.jar,/path/to/viaforge.jar,/path/to/optional-mods-directory"
# 同样适用于 platforms/neoforge-1.21.11；必须提供对应版本的 JAR。
```

The task uses native FML `Dist.CLIENT` discovery, forces all NoLoadingScreen target transformations, asserts Sodium/ViaForge presence, prints versions and supplied SHA1 hashes, and executes real Sodium collector/deferred/full-frame-completion fixtures. It does not construct the entire optional modpack or enable a shader pack.

## Architecture / 实现边界

- Root Loom/Fabric26.2 stays separate. `platforms/neoforge-common.gradle` selects a shared source pool plus narrow loader/API-family files; it does not generate rewritten Java or branch on Minecraft versions at runtime.
- `NoLoadingScreen`, `PlaceholderWorld`, movement/controls/interactions, connection ownership, saving/disconnect phases, skin futures and budgets stay shared. `platform/`, inventory drawing adapters and exact injection hosts isolate incompatible APIs.
- 1.21 readiness uses a strict `WaitingForPlayerChunk.isReady` hook. Vanilla still constructs `ClientLevelReady`, retains its close delay and sends the loaded notification. No fake26.2 callback or fabricated packet.
- Disconnect on both1.21 targets retains the outgoing level through its saving/final-screen sites. The compile-selected hosts relinquish only `level`/`player` there, **after** native logout/unload events and the saved registry-reversion decision, before shared scene installation. Mesh attachment, native final cleanup, registry-reversion call/order and the shared refusal to adopt over a live world remain intact. Disabled and ordinary multiplayer exits retain vanilla pointer ordering.
- Older `runTick` includes tasks/packets/gameplay: **never bind the whole method**. Only the renderer/mouse/UI operations bind. Synchronous waits invoke `runTick(false)` but promote only the renderer's world/HUD flag. GPU pending work, overlays and normal frame events still run; this is not a promise to suppress all kinds of tasks.
- Recovery restores native matrix depth/projection/texture state and releases unfinished GUI meshes before resuming the vanilla presentation tail. Cleanup failure rethrows the original error with cleanup failure suppressed; non-placeholder errors are never masked.
- NeoForge synthetic listener construction was audited against both mapped constructor patches and the listed ViaForge injections. Those ViaForge versions hook real login/connect/encryption, not the isolated listener constructors. No live connection or handler is replaced.
- Sodium GL programs are compiled using its actual `SMOOTH`/terrain-pass/vertex options. Their ownership moves out of the temporary shell before shell deletion, then into one matching completed default renderer. Pending objects are released on mismatch, resource invalidation or shutdown. This does **not** promise zero compilation after a later scene teardown or with shader packs. Fabric26.2 keeps its original pipeline backend.

## Verified evidence / 验证范围

Default NeoForge `check` includes Java21 artifact/metadata isolation, native CLIENT strict-hook inventory, shared movement/skin/queue fixtures, local field ownership/readiness/wait tests, matrix-depth and real partial-GUI-mesh recovery, and pending-program ownership/error tests. Vanilla registry decoding runs in a **separate JVM with an explicitly empty mod-event container fixture**, not as a claim that external mods were initialized. Root Fabric regression fixtures remain enabled.

Opt-in full GPU smoke:

```bash
./gradlew -p platforms/neoforge-1.21.10 verifyGpu
./gradlew -p platforms/neoforge-1.21.10 verifyGpu \
  -PsmokeModJars="/path/to/sodium.jar,/path/to/viaforge.jar"
# Replace project path for 1.21.11.
```

Both targets passed on the test host with and without Sodium+ViaForge: normal client/mod construction, actual synthetic world frames, third-person and local inventory, local debris, intentional render failure and ten subsequent usable frames. The disconnect regression additionally promotes an initialized in-memory scene to a **live** client owner and executes the actual transformed `Minecraft.disconnect`: save wait, multiplayer kick, disabled/refused adoption, save-wait exception and explicit exit. Native logout/unload events, registry reversion, screen/field cleanup, mesh retention and dead-connection isolation are asserted. Its integrated-server shutdown probe is allocated without running a server constructor/thread or touching a save; this verifies client handoff/wait ordering, not disk saving or server gameplay. With Sodium, assertions inspect retained and adopted **actual GL program identities**, not just successful temporary compilation. Tests use only target `build/gpu-smoke`, an offline test name/token, official cache assets, no save and no server; they stop automatically and have a finite timeout. All launcher/fixture/smoke files are excluded from release artifacts.

NeoForge enables its Blaze3D validation wrapper by default in development but not production. Sodium0.7.3 cannot cast those wrapper textures to its native GL textures. Modded smoke therefore writes `enableB3DValidationLayer=false` **only in its own build-directory config**, matching production; bare smoke retains validation. No production workaround or user config is changed. Expected offline authentication warnings and the intentionally injected render error are not PASS evidence; the task requires its explicit `NeoForgeGpuSmoke PASSED` marker and successful exit.

**未验证 / Not established:** real remote/proxy or cross-protocol transfers, integrated save cancellation under gameplay, real account/server skin handoff, shader-pack output, visual pixel correctness, modpack gameplay or FPS. Existing manual gameplay checklist remains necessary: [TESTING.zh-CN.md](TESTING.zh-CN.md). Transformation checks and short isolated smoke are not substitutes for it.
