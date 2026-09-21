# Version Matrix / 版本矩阵

Minecraft **26.1 / 26.1.1 / 26.1.2 share one `26.1.x` release JAR per loader**, compiled against 26.1. Loaders remain separate; 26.2, 26.3 and the older versions retain their own artifacts. The Mod version remains **1.1.0**.

Minecraft **26.1、26.1.1、26.1.2 在同一加载器下共用一份 `26.1.x` JAR**，以 26.1 为编译基线。不同加载器不能混用；26.2、26.3 和旧版本仍使用各自的产物。Mod 版本保持 **1.1.0**。

## Targets

| Minecraft | Loader (pinned build/test version) | Java | Gradle project |
|---|---|---|---|
| 26.1.x | Fabric Loader 0.19.3 (all three games) | 25 | `platforms/fabric-26.1` |
| 26.2 | Fabric Loader 0.19.3 | 25 | `.` |
| 26.3 | Fabric Loader 0.19.5 | 25 | `platforms/fabric-26.3` |
| 26.1.x | NeoForge 26.1.0.19-beta / 26.1.1.15-beta / 26.1.2.109 respectively | 25 | `platforms/neoforge-26.1` |
| 26.2 | NeoForge 26.2.0.88 | 25 | `platforms/neoforge-26.2` |
| 26.1.x | Forge 62.0.9 / 63.0.2 / 64.1.3 respectively | 25 | `platforms/forge-26.1` |
| 26.2 | Forge 65.1.3 | 25 | `platforms/forge-26.2` |
| 1.21.10 | NeoForge 21.10.64 | 21 | `platforms/neoforge-1.21.10` |
| 1.21.11 | NeoForge 21.11.45 | 21 | `platforms/neoforge-1.21.11` |

Minecraft 26.3 (released 2026-09-15) currently has a Fabric target only: as of 2026-09-16 NeoForge's `26.3.x` port is still an unmerged, non-compiling pull request and no NeoForge or Forge 26.3 build exists on either Maven. Their targets will be added once a build is published. NeoForge's last builds for Minecraft 26.1 and 26.1.1 are beta releases. Minecraft 26.1.2 has a stable NeoForge build. The existing 1.21.10/1.21.11 targets, dependencies and optional-mod policy remain unchanged; see [their evidence and limits](NEOFORGE.md).

Minecraft 26.3（2026-09-15 发布）目前只有 Fabric 目标：截至 2026-09-16，NeoForge 的 26.3 移植仍是未合并、尚不能编译的 PR，NeoForge 与 Forge 的 Maven 上都没有 26.3 构建；发布后再补目标。NeoForge 对 Minecraft 26.1、26.1.1 的最后发布版本仍带 beta 标记；26.1.2 有正式版。原有 NeoForge 1.21.10、1.21.11 的依赖、支持与可选模组策略不变。

## Build

Run the repository wrapper, selecting one project from the table:

```bash
./gradlew -p platforms/fabric-26.1 build check
./gradlew -p platforms/neoforge-26.2 build check
./gradlew -p platforms/forge-26.2 build check
```

The selected project's `build/libs/` contains `NoLoadingScreen-1.1.0-Loader-Minecraft.jar` and its `-sources.jar`; the 26.1.x projects instead produce the loader-specific `NoLoadingScreen-1.1.0-Loader-26.1.x.jar` named below. Only the main JAR belongs in the game's `mods` directory. Root `./gradlew build` continues to build Fabric 26.2 only. `-Pversion=...` explicitly overrides the Mod version; it is not needed for these builds.

产物统一放在对应项目的 `build/libs/`；游戏只安装不带 `-sources` 的主 JAR。26.1 族的产物为 `NoLoadingScreen-1.1.0-Fabric-26.1.x.jar`、`NoLoadingScreen-1.1.0-NeoForge-26.1.x.jar`、`NoLoadingScreen-1.1.0-Forge-26.1.x.jar`。原 `*-26.1.1`、`*-26.1.2` 独立 Gradle 项目已删除；不要再使用旧构建目录中残留的分版本 JAR。根目录构建入口仍为 Fabric 26.2，不会一次性启动所有加载器工具链。

### Verify the same 26.1.x JAR / 同一产物跨版本验证

Build once with the commands above, then run the existing JAR on each real game/loader runtime. Paths passed as Gradle properties are relative to the selected project (absolute paths also work):

```bash
for game in 26.1 26.1.1 26.1.2; do
  ./gradlew -p platforms/fabric-26.1 check \
    -PtestMinecraftVersion="$game" \
    -PverificationJar=build/libs/NoLoadingScreen-1.1.0-Fabric-26.1.x.jar
done
```

For NeoForge/Forge, change the project and loader in the filename. `testMinecraftVersion` is verification-only and requires an existing `verificationJar`: production compilation, resource processing and release packaging are disabled. Only fixtures compile against the selected runtime, under `build/verification/<game>/`. The verifier checks the actual game version, the engine's code source, byte-for-byte equality with the supplied JAR, its SHA-256 and family metadata. Forge uses a separate fixture-only mod to avoid split Java module packages; it does not repackage the release.

`testMinecraftVersion` 只选择测试环境，不会重新编译引擎或生成补丁版本产物；必须同时传入已有的 `verificationJar`。切换到 NeoForge/Forge 时替换项目路径和文件名中的加载器即可。`verifyCompatibility` 与 `verifyRepairGpu` 同样接受这两个参数，因此可以验证正式 JAR 搭配优化模组与真实 GPU 的行为。发布前应验证三个版本，不应只依赖默认 26.1 的源码构建检查。

Metadata covers Minecraft `[26.1,26.1.2]` (Fabric: `>=26.1 <=26.1.2`). NeoForge/Forge dependency ranges are unions preserving the original minimum tested loader for each game line; the merge does not lower those requirements or include 26.2.

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
- `platforms/minecraft-26.1` supplies the older GUI ownership, frame/extraction backend, scene constructors, readiness and terrain compilation APIs. All three 26.1 releases use this same source pool, compiled once per loader against 26.1 and verified unchanged on all three game runtimes. `platforms/minecraft-26.1.gradle` separates the release family from the selected verification runtime; no engine version branches were added.
- `platforms/minecraft-26.3` supplies the SDL input path (`SDLEventHandlerMixin`, `WaitInputMixin`, `MinecraftEventsAccessor`), the renderpearl GPU surface (`FrameSurface`), authlib 10's profile result (`ProfileLookup`), the `SwingState`-based arm swing (`PlayerAnimation`, `SwingStateAccessor`), the player-owned first-person hand state (`SceneRenderer.tickHands`), the `ItemActivation` player constructor (`SceneFactory.createPlayer`) and the fade-aware entity visibility test. Shared code reaches all of these through adapter classes; no shared file names a game version. A family may also list shared files it has no counterpart for (`sharedExcludes` in the target's `build.gradle`; 26.3 drops `GameRendererAccessor` because 26.3's `GameRenderer` has no private member left to reach). Test fixtures follow the same rule through `platforms/<family>/src/test/java`.
- Loader-specific source layers contain only loader/bootstrap/configuration APIs and patched injection hosts. Source selection happens during Gradle configuration by package-relative file path, not by rewriting Java source or testing Minecraft versions at runtime.
- Existing mapped NeoForge 1.21 builds stay separate under `neoforge-common.gradle`. Java 21 mappings and Java 25 unobfuscated game dependencies never share a compilation classpath.
- All targets reuse `src/main/resources/assets/noloadingscreen/icon.png`. Fabric metadata references `icon`; FML metadata references `logoFile`. New release checks validate the actual PNG bytes, reference, metadata, Java 25 bytecode and test/loader isolation.

## Verification Limits

New 26.x builds run `verifyClient` through the real loader's CLIENT transformer. The verifier loads every required non-Sodium Mixin target under `defaultRequire=1`, then runs shared regression fixtures: 153 movement assertions, 114 skin-preload assertions, class-warmup checks, and 197 lifecycle/HUD/field-ownership assertions, plus sandbox and retained-light-queue fixtures. `verifyModernArtifact` checks the distributable JAR rather than only the source resources. Neither task opens a save or connects to a game server.

Forge's bundled upstream Mixin uses the `JAVA_21` capability label, while compilation and runtime still require Java 25. This does not change the JAR's Java 25 class-file target or relax required injection points. Other new loaders use `JAVA_25`.

新增版本的无窗口转换检查证明严格注入点能应用到对应游戏和加载器，不等同于 GPU、整合包、真实跨服或服务器玩法验收。发布前仍应执行[实机清单](TESTING.zh-CN.md)，尤其是保存退出、KickWarn、F5、物品栏、资源包重载和渲染异常恢复。

The existing Fabric 26.2 and NeoForge 1.21 optional-mod results do **not** certify the new targets by themselves. NeoForge 26.2 now shares the root whitelist: Sodium's private hooks, class warmup, terrain-pipeline precompilation and single-worker synthetic void are enabled for the exact build `0.9.2+mc26.2`, verified through `./gradlew -p platforms/neoforge-26.2 verifyCompatibility -PcompatibilityJars=<sodium-neoforge jar>` (seven private hooks plus the actual collector fixtures) and the `verifyRepairGpu` run with that jar. Sodium's NeoForge release keeps its classes in a `META-INF/jarjar` nested jar; the whitelist checks the `sodium` mod version, not the outer jar. Fabric/NeoForge 26.1.x share the API-family `SodiumCompatibility.java` whitelist for both `0.8.9+mc26.1.1` (26.1/26.1.1) and `0.9.2+mc26.1.2` (26.1.2). The private hooks are unchanged, not disabled to achieve cross-version support; the family's shader warm-up remains a no-op because those builds compile GL programs on first use. Fabric 26.3 retains its target whitelist for `0.9.2+mc26.3`. The Fabric targets gained the same `verifyCompatibility -PcompatibilityJars=...` task as NeoForge. Forge 26.x still keeps Sodium's native scheduling because no official Sodium Forge build exists. Other loading/rendering mods can still conflict; the per-mod review of the community's most used optimizers, the multi-mod stacks that were transformed per version and the two conflicts fixed on this side are in [OPTIMIZATION_MOD_COMPATIBILITY.zh-CN.md](OPTIMIZATION_MOD_COMPATIBILITY.zh-CN.md). No optional performance mod is bundled or required.

`verifyRepairGpu` (opt-in, every target) runs an isolated real-GPU regression: synthetic placeholder, a live-session save wait through the actual transformed `Minecraft.disconnect` with injected input (GLFW callbacks up to 26.2, `SDL_PushEvent` on 26.3, so the game's own dispatch chain is exercised either way), KickWarn, middle-click pick, hand swap, sprint FOV and inventory frames. Commands and asset indexes are in [TESTING.zh-CN.md](TESTING.zh-CN.md).

Fabric 26.1 compiles its optional Mod Menu integration against 18.0.1, whose upstream release covers 26.1/26.1.1/26.1.2. Root Fabric 26.2 retains Mod Menu 20.0.1. Fabric 26.3 compiles against 21.0.0-beta.1, the only Mod Menu build published for the 26.3 line so far (tagged for 26.3-rc-1; no build is tagged for the 26.3 release yet). All use the same settings screen; FML loaders expose it in their Mod list.

## CI and Releases

The build workflow contains **9 release targets**, retaining **15 game/loader runtime combinations**. Each 26.1.x job builds once, then runs `check` on the unchanged release for 26.1, 26.1.1 and 26.1.2 and checks its SHA-256 has not changed. The tag-release workflow waits for every check and validates the exact **9 main JARs + 9 source JARs**, rejecting missing files and stale extra artifacts. CI uses the tag's version explicitly; normal local builds keep `gradle.properties` unchanged.

CI 由 15 份独立产物合并为 9 份，仍覆盖原有的 15 个游戏/加载器组合；三个 26.1.x 发布任务都要求同一 JAR 跨三个游戏版本通过检查，全部通过后才发布 9 个主 JAR 与 9 个源码 JAR。
