# Version Matrix / 版本矩阵

Every target is a separate build and release JAR. Do not mix loader or Minecraft versions. The Mod version remains **1.1.0**.

每个目标独立编译和验证，不能混用不同加载器或 Minecraft 版本的 JAR。Mod 版本保持 **1.1.0**。

## Targets

| Minecraft | Loader (pinned build/test version) | Java | Gradle project |
|---|---|---|---|
| 26.1 | Fabric Loader 0.19.3 | 25 | `platforms/fabric-26.1` |
| 26.1.1 | Fabric Loader 0.19.3 | 25 | `platforms/fabric-26.1.1` |
| 26.1.2 | Fabric Loader 0.19.3 | 25 | `platforms/fabric-26.1.2` |
| 26.2 | Fabric Loader 0.19.3 | 25 | `.` |
| 26.1 | NeoForge 26.1.0.19-beta | 25 | `platforms/neoforge-26.1` |
| 26.1.1 | NeoForge 26.1.1.15-beta | 25 | `platforms/neoforge-26.1.1` |
| 26.1.2 | NeoForge 26.1.2.109 | 25 | `platforms/neoforge-26.1.2` |
| 26.2 | NeoForge 26.2.0.88 | 25 | `platforms/neoforge-26.2` |
| 26.1 | Forge 62.0.9 | 25 | `platforms/forge-26.1` |
| 26.1.1 | Forge 63.0.2 | 25 | `platforms/forge-26.1.1` |
| 26.1.2 | Forge 64.1.3 | 25 | `platforms/forge-26.1.2` |
| 26.2 | Forge 65.1.3 | 25 | `platforms/forge-26.2` |
| 1.21.10 | NeoForge 21.10.64 | 21 | `platforms/neoforge-1.21.10` |
| 1.21.11 | NeoForge 21.11.45 | 21 | `platforms/neoforge-1.21.11` |

NeoForge's last builds for Minecraft 26.1 and 26.1.1 are beta releases. Minecraft 26.1.2 has a stable NeoForge build. The existing 1.21.10/1.21.11 targets, dependencies and optional-mod policy remain unchanged; see [their evidence and limits](NEOFORGE.md).

NeoForge 对 Minecraft 26.1、26.1.1 的最后发布版本仍带 beta 标记；26.1.2 有正式版。原有 NeoForge 1.21.10、1.21.11 的依赖、支持与可选模组策略不变。

## Build

Run the repository wrapper, selecting one project from the table:

```bash
./gradlew -p platforms/fabric-26.1 build check
./gradlew -p platforms/neoforge-26.2 build check
./gradlew -p platforms/forge-26.2 build check
```

The selected project's `build/libs/` contains `NoLoadingScreen-1.1.0-Loader-Minecraft.jar` and its `-sources.jar`. Only the main JAR belongs in the game's `mods` directory. Root `./gradlew build` continues to build Fabric 26.2 only. `-Pversion=...` explicitly overrides the Mod version; it is not needed for these builds.

产物统一放在对应项目的 `build/libs/`；游戏只安装不带 `-sources` 的主 JAR。根目录构建入口仍为 Fabric 26.2，不会一次性启动所有加载器工具链。

Use a local JDK 25 for 26.x and JDK 21 for the older NeoForge targets. On Windows:

```powershell
$env:JAVA_HOME = 'D:\Program Files\JDK25'
.\gradlew.bat -p platforms\fabric-26.1 build check
$env:JAVA_HOME = 'D:\Program Files\JDK21'
.\gradlew.bat -p platforms\neoforge-1.21.11 build check
```

If necessary, set `org.gradle.java.installations.paths=/path/to/jdk` in your local Gradle user-home `gradle.properties` (normally `~/.gradle/gradle.properties`) to help toolchain discovery. Download proxies can be supplied for one invocation with `-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=10808 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=10808`. Build scripts contain no hardcoded local JDK or proxy paths. Run initial Forge/NeoForge game-artifact preparation sequentially on memory-constrained machines.

Build plugins are pinned to Loom 1.17.20 for the new Fabric targets, ModDevGradle 2.0.147, and ForgeGradle 7.0.40. Forge's Mavenizer can also obtain a JDK 8 for legacy build helpers; that is not the Mod's runtime requirement. When such subprocess downloads need a proxy, pass the proxy properties through `JAVA_TOOL_OPTIONS` for that invocation too.

从其他计算机拷贝项目后，不需要沿用旧机器的 JDK 路径。首次构建会解析本机工具链并补齐缓存；内存较小时请串行构建 Forge/NeoForge，避免多个反编译任务同时占用大量内存。

## Architecture

- `src/main/java` remains the shared feature state machine and Fabric 26.2 API layer.
- `platforms/minecraft-26.1` supplies the older GUI ownership, frame/extraction backend, scene constructors, readiness and terrain compilation APIs. All three 26.1 releases use this same source pool, compiled separately against each real game JAR.
- Loader-specific source layers contain only loader/bootstrap/configuration APIs and patched injection hosts. Source selection happens during Gradle configuration by package-relative file path, not by rewriting Java source or testing Minecraft versions at runtime.
- Existing mapped NeoForge 1.21 builds stay separate under `neoforge-common.gradle`. Java 21 mappings and Java 25 unobfuscated game dependencies never share a compilation classpath.
- All targets reuse `src/main/resources/assets/noloadingscreen/icon.png`. Fabric metadata references `icon`; FML metadata references `logoFile`. New release checks validate the actual PNG bytes, reference, metadata, Java 25 bytecode and test/loader isolation.

## Verification Limits

New 26.x builds run `verifyClient` through the real loader's CLIENT transformer. The verifier loads every required non-Sodium Mixin target under `defaultRequire=1`, then runs shared regression fixtures: 150 movement assertions, 114 skin-preload assertions, class-warmup checks, and 190 lifecycle/HUD/field-ownership assertions. `verifyModernArtifact` checks the distributable JAR rather than only the source resources. Neither task opens a save or connects to a game server.

Forge's bundled upstream Mixin uses the `JAVA_21` capability label, while compilation and runtime still require Java 25. This does not change the JAR's Java 25 class-file target or relax required injection points. Other new loaders use `JAVA_25`.

新增版本的无窗口转换检查证明严格注入点能应用到对应游戏和加载器，不等同于 GPU、整合包、真实跨服或服务器玩法验收。发布前仍应执行[实机清单](TESTING.zh-CN.md)，尤其是保存退出、KickWarn、F5、物品栏、资源包重载和渲染异常恢复。

The existing Fabric 26.2 and NeoForge 1.21 optional-mod results do **not** certify the new targets by themselves. NeoForge 26.2 now shares the root whitelist: Sodium's private hooks, class warmup, terrain-pipeline precompilation and single-worker synthetic void are enabled for the exact build `0.9.2+mc26.2`, verified through `./gradlew -p platforms/neoforge-26.2 verifyCompatibility -PcompatibilityJars=<sodium-neoforge jar>` (seven private hooks plus the actual collector fixtures) and the `verifyRepairGpu` run with that jar. Sodium's NeoForge release keeps its classes in a `META-INF/jarjar` nested jar; the whitelist checks the `sodium` mod version, not the outer jar. Forge 26.x and Fabric 26.1 still keep Sodium's native scheduling because no build was verified for them. Other loading/rendering mods can still conflict. No optional performance mod is bundled or required.

`verifyRepairGpu` (opt-in, every target) runs an isolated real-GPU regression: synthetic placeholder, a live-session save wait through the actual transformed `Minecraft.disconnect` with GLFW-injected input, KickWarn, middle-click pick, hand swap, sprint FOV and inventory frames. Commands and asset indexes are in [TESTING.zh-CN.md](TESTING.zh-CN.md).

Fabric 26.1 compiles its optional Mod Menu integration against 18.0.1, whose upstream release covers 26.1/26.1.1/26.1.2. Root Fabric 26.2 retains Mod Menu 20.0.1. Both use the same settings screen; FML loaders expose it in their Mod list.

## CI and Releases

The build workflow contains all 14 targets. The tag-release workflow reuses that matrix, waits for all checks, and attaches the 14 main JARs and 14 source JARs. It does not publish a partial release when a target fails. CI uses the tag's version explicitly; normal local builds keep `gradle.properties` unchanged.
