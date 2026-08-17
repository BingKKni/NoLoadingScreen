# NoLoadingScreen：实现原理与实测

[English technical document](TECHNICAL_DETAILS.md) | [返回简体中文 README](../README_zh-CN.md) | [English README](../README.md)

> 本文面向希望了解内部实现、兼容性设计和测试过程的开发者。安装与日常使用请直接阅读项目 README。

[![build](https://github.com/BingKKni/NoLoadingScreen/actions/workflows/build.yml/badge.svg)](https://github.com/BingKKni/NoLoadingScreen/actions/workflows/build.yml)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-brightgreen)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-dbd0b4)](https://fabricmc.net/)
[![Environment](https://img.shields.io/badge/Environment-Client-blue)]()
[![License](https://img.shields.io/badge/License-MIT-yellow)](../LICENSE)

**把加载界面从进图流程里拿掉。** 登录、切维度、走传送门、跨服——原版会用一块盖住整个窗口的板子
（「加载地形中」「重载配置中」，或者干脆是黑屏）填满这段时间，本模组把这块板子换成世界本身：

1. **世界还没到的时候，给一个能看的画面。** 登录包到达之前客户端的 `level` 和 `player` 都是 `null`，
   而整个渲染世界的流程被这两个字段挡死——这就是那块板子存在的**唯一**原因。
   本模组临时填上它们：跨服时沿用刚刚离开的那个世界，单人开图时合成一个有天空的虚空，
   鼠标是自己的，可以自由转视角、飘动，直到真世界到达。**[占位世界](#一占位世界核心)是这个模组的主体。**
2. **世界一到就把画面交还。** 原版会把玩家按在「加载地形中」界面上，直到脚下的区块被**建模完成**；
   而区块数据一到，碰撞体积就已经是实心的了，建模纯粹是画面问题。本模组让原版自己的就绪闸门提前打开，
   界面直接不再出现，区块在眼前一块块流进来。
3. **写清楚现在在等什么。** 原版跨服时那块板子上一个字都没有。本模组在屏幕上写明当前阶段
   （启动世界 / 同步数据 / 等待世界 / 接收区块）和已等待秒数，日志里每次进图也留一行分段汇总。

纯客户端 Fabric 模组，**不构造、不拦截、不延迟、不重排任何一个发往服务端的数据包**。

> ⚠️ **先说清楚这个模组不做什么：它不会让进图变快。**
> 服务端要花的时间，客户端模组一秒也省不掉——Hypixel 跨服的「重载配置中」是服务端在重发注册表和标签，
> 实测占整次进图的 80~98%。单人开图那一秒是集成服务端在启动和准备出生点，本模组一律**不去动服务端的时序**。
> 它改的是**那段时间屏幕上是什么**：从一块板子，变成一个能自由转视角的世界。

---

## 安装

1. 装 [Fabric Loader](https://fabricmc.net/use/installer) ≥ 0.19.3（Minecraft 26.2，需要 Java 25）
2. 把 `noloadingscreen-x.y.z.jar` 丢进 `.minecraft/mods/`
3. 完事——**不需要 Fabric API，不需要任何前置**

[Mod Menu](https://modrinth.com/mod/modmenu) 是可选的，装了就能在里面点开图形配置界面；不装的话改
`.minecraft/config/noloadingscreen.json` 也一样。

---

## 进图的时候到底在等什么

26.2 把进图的等待逻辑放在 `net.minecraft.client.multiplayer.LevelLoadTracker`，是一个三态状态机：

```
WaitingForServer
      │  收到 ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START
      ▼
WaitingForPlayerChunk        ← 「加载地形中」停留的地方
      │  玩家所在的 section 被区块构建器「建模完成」（或 30 秒超时）
      ▼
ClientLevelReady
      ▼
isLevelReady() == true
```

`isLevelReady()` 返回 `true` 的那一刻，原版**同时**做两件事：关闭加载界面，以及发送
`ServerboundPlayerLoadedPacket`。

而 `WaitingForPlayerChunk` 等的是**网格建模（mesh）完成，不是区块数据到达**。区块数据一到，碰撞体积就已经
是实心的了，建模纯粹是画面问题——渲染距离越大、材质包越重、mod 越多，这一步越慢。

**但这还不是最长的一段。** 从点下「进入世界」到 `ClientboundLoginPacket` 到达之间，客户端连
`ClientLevel` 对象都没有，而 `GameRenderer.render` 里渲染世界的那一整块是这样写的：

```java
boolean flag1 = flag && advanceGameTime && this.minecraft.level != null;
if (flag1) { ... this.renderLevel(deltaTracker); ... }
```

`renderLevel` 第一件事又是 `LocalPlayer localplayer = this.minecraft.player;`，紧接着不判空就解引用。
所以那段时间里，画面上除了一块全屏界面**不可能有别的东西**——黑屏不是取舍，是这两个字段为 null 的必然结果。

---

## 它做了什么

### 一、占位世界（核心）

把那两个字段填上。**分两种方式，取决于有没有东西可以接管。**

#### 跨服：接管即将被拆掉的旧世界（不构造任何东西）

代理服把玩家打回配置阶段时，被拆掉的那个世界**就在手上**，而且是完整的：每个区块都在，
而且**都已经建模完成**。所以最好的办法不是造一个新的，而是**别让它死**：

- 让原版的 `clearClientLevel` 照常把 `minecraft.level` / `player` 置空——这一点必须保留，
  它正是「每个 tick、每个数据包处理、每个别的模组仍然走原版路径」的前提；
- 但把其中一行 `updateLevelInEngines(null)` 拦掉。那一行会 `levelExtractor.setLevel(null)`，
  **释放掉所有已建好的区块网格**；再挂回去就要把整个世界重新建模一遍——那恰好是这个模组存在的意义。
  拦掉之后渲染引擎仍指向旧世界，画面一帧不掉。（这一行里唯一保留的是停止音效。）
- 然后每帧渲染时把旧的 level / player 临时递回去。

看到的是**刚刚离开的那个地方，静止着**，可以自由转视角。**全程没有 new 出任何对象。**

#### 单人开图：合成一个虚空

单人开图时没有任何东西可以接管，所以这里才构造：一个完整的、普通的 `ClientLevel`
（空区块、平原群系、正午、晴天）和一个站在里面的 `LocalPlayer`。

⚠️ **构造 `ClientPacketListener` 是有代价的，这一点是实机跑出来才发现的**：它的构造函数被别的模组挂了钩子。
Fabric API 的网络模块在里面把新监听器注册成全局唯一的 client play addon，已经有一个时直接抛异常：

```
java.lang.IllegalStateException
    at ClientNetworkingImpl.setClientPlayAddon(ClientNetworkingImpl.java:126)
    at ClientPacketListener.handler$...$fabric-networking-api-v1$initAddon
    at ClientPacketListener.<init>
```

**所以只有单人开图这一条路径会构造**，而它恰好是安全的：那时根本不存在 play 会话，也正因如此才没有东西可接管。
即便如此，构造前后仍会把那个全局槽位保存并还原（见 `PlayAddonGuard`）——否则这个马上要被丢弃的监听器
会一直占着槽位，等真正的监听器建立时同样抛异常，而那已经在进图中途、救不回来了。
如果那个槽位因为 Fabric 内部改动而无法安全存取，本模组**宁可不构造**，保留加载界面；
其中「加载地形中」界面仍会按极简样式绘制。

#### 为什么这对服务端是安全的

- 无论占位世界拿的是哪个监听器，它底下的 `Connection` 都**没有 channel**。
  `Connection#send` 在 `isConnected()` 为假时把包塞进 `pendingActions` 队列，而这个对象马上会被丢弃。
  接管来的监听器会被换掉连接（`ClientCommonPacketListenerImplAccessor`），合成的天生如此。
  也就是说，绑定期间任何人通过 `minecraft.getConnection()`（原版实现就是 `player.connection`）
  发出的包，**物理上到不了服务器**，任何协议阶段都一样。
- 占位玩家**永远不被 tick**。它不在任何 `tickEntities` 路径上，`aiStep`、`sendPosition` 一次都不会跑。
  它的移动由模组自己每 tick 直接改坐标完成。
- 真世界一到，占位整个被丢掉，原版在原版时机用**服务端给的坐标和视角**新建真玩家。
  在虚空里转过的视角、飘过的距离一点都带不进去，所以服务端没有任何东西需要纠正，
  也不存在「客户端偷跑几格被拉回」这种情况。
- **绑定只发生在一帧渲染期间。** `minecraft.level` / `player` / `gameMode` 只在 `renderFrame` 和鼠标转视角
  这两个调用的前后被填上、随即置回 null。所以**客户端每个 tick、每个数据包处理、每个别的模组看到的仍然是
  原版的 null**——这是把影响面压到最小的关键。

唯一一处必须额外处理的是 `Minecraft#handleKeybinds`：原版只在没有界面时才会走到它，而「没有界面」在原版里
蕴含「有玩家」，它每个分支都解引用 `this.player`、一半分支还要发包。所以占位期间它被整个跳过
（期间攒下的点击也一并丢弃，免得世界一开就集中触发）。

#### 它不允许把游戏搞崩

占位世界是一个**纯装饰**功能，站在进图流程前面。所以整个 `renderFrame` 被 `@WrapMethod` 包住：
绑定期间那一帧如果抛异常，异常会**完整写进日志**，然后占位世界被丢掉、渲染引擎交还、把对应加载界面装回来
（「加载地形中」界面会按极简样式绘制）——而不是把游戏带走。只有本模组改动过的那些帧会被这样兜住，
其余情况原样抛出。

这条不是预防性洁癖，是实机跑出来的：`GameRenderer#renderItemInHand` 会无条件解引用
`minecraft.gameMode`，而 `clearClientLevel` 把它置空的位置**比置空 `level` 早几行**——
于是快照拿到 null，一帧之后就是一次崩溃。现在 `bind()` 要求 level / player / gameMode
**三个都在才肯绑定**，接管路径缺 gameMode 时会自己补一个。

##### ⚠️ 吞掉一帧的代价：必须把交换链图像交还

这是「安全网」自己带来的问题。`renderFrame` 的结构是：

```java
public void renderFrame(boolean advanceGameTime) {
   if (this.windowSurface.isAcquired()) return;   // ← 第一行
   ...
   this.windowSurface.acquireNextTexture();       // 取一张交换链图像
   ...
   this.gameRenderer.extract(...);                // 抛异常的位置在这之后
   this.gameRenderer.render(...);
   ...
   if (this.windowSurface.isAcquired()) this.windowSurface.present();   // 唯一的交还点
}
```

**中间抛异常 → `present()` 不会执行 → 图像一直被占着 → 从此每一帧都在第一行原地返回。**
游戏还在跑（tick、聊天、进图全都正常，日志一片祥和），但**画面永远停在最后那一帧**，
退出时以 `Shutdown failure! Cannot close a surface while it is acquired` 收尾。
**这比它想消灭的加载界面糟糕得多。**

现在吞掉一帧之后会补上原版尾部真正要紧的那三步（blit → submit → present）把图像交还；
如果连这一步都失败，就**原样抛出原异常**——一份崩溃报告远好过一个假装还活着的冻住的窗口。
另外，占位世界累计失败 3 次之后会**在本次运行内不再启用**（日志里写明），后续由对应加载界面承接；
提前放行闸门不受影响。

### 二、闸门提前打开

`LevelLoadTracker` 有一个 public 方法 `getPlayerCompiledSectionCallback()`，返回的正是原版自己用来标记
「玩家所在 section 已就绪」的回调。本模组在 `tickClientLoad()` 的 HEAD 取得回调后，直接调用这个**原版回调**。

于是原版状态机照常推进——加载界面关闭和 `ServerboundPlayerLoadedPacket` 依然由原版代码在同一 tick、
按原版顺序发出。

这就是为什么它对反作弊是安全的：**没有任何一个发往服务端的包是模组发的。** 那个「ready 包」
（`ServerboundPlayerLoadedPacket`，1.21.4 引入）本来就是原版机制的一部分，本模组只是让它提前触发。

闸门固定为立即放行，不等服务端开始发区块。若此时玩家所在区块还没到达，本模组会固定锁定玩家位置，
直到该区块到达，避免客户端物理让玩家坠入虚空。

### 三、单人不再空转

原版 `doWorldLoad` 在集成服务端启动期间是这样等的：

```java
while (!this.singleplayerServer.isReady() || this.gui.overlay() != null) {
   levelloadingscreen.tick();
   this.renderFrame(false);      // advanceGameTime = false
   this.runAllTasks();
   this.managedBlock(...);
}
```

这个循环**占着客户端主线程**：不处理输入、不推进 tick、不驱动连接，而且 `renderFrame(false)` 里
`advanceGameTime` 为假，渲染世界那一段本来就被跳过。这就是单人进图开头那一秒「完全动不了」的来源，
光把界面画好看是没用的。

这个等待不是必需的：`ServerConnectionListener` 在 `MinecraftServer` 构造函数里就建好了（构造函数在
`spin()` 里跑在客户端线程上，早于这个循环），所以内存通道可以立刻打开，握手包先在队列里等着，
等服务端线程开始 tick 再被处理。去掉等待之后客户端回到正常主循环，像推进任何 `pendingConnection`
一样推进它——于是占位世界在这段时间里是**活的**。（条件里等资源包重载的那一半原样保留。）

**服务端自己的启动时长一点没变**，变的是那段时间客户端在干什么。

### 四、关于「重载配置中」

这个界面不能简单丢掉，它的 `tick()` 里藏着这段：

```java
if (this.connection.isConnected()) {
   this.connection.tick();      // 配置阶段的连接全靠这里驱动
} else {
   this.connection.handleDisconnection();
}
```

`Minecraft` 自己只 tick *pending* 连接，所以界面一没，连接就不再推进，会一直卡到超时。
本模组的做法是**界面对象留着并继续被 tick，只是不再挂在 Gui 上**：模组每个客户端 tick 直接调它的
`tick()`，原版逻辑一行没改，连那个 600 tick 的「断开连接」按钮延时都照跑。

**按 Esc 可以随时把这个界面装回来**，因为上面的「断开连接」按钮是配置阶段唯一的退路（Esc 在原版那里无效），
一个出不去的虚空是个陷阱。虚空画面下方会一直写着这行提示。

### Mixin 清单

九个 Mixin（其中三个是纯访问器），全部是 `@Inject` / `@Redirect` / `@ModifyVariable` / `@WrapMethod`，
**没有 `@Overwrite`**：

| 类 | 注入点 | 作用 |
|---|---|---|
| `Minecraft` | `renderFrame` `@WrapMethod` | 渲染一帧期间绑定/解绑占位世界 + 异常安全网 + 交还交换链图像 |
| `Minecraft` | `runTick` → `handleAccumulatedMovement` | 绑定后调用，让鼠标能转占位玩家的视角 |
| `Minecraft` | `handleKeybinds` HEAD | 占位期间整个跳过（见上） |
| `Minecraft` | `doWorldLoad` → `IntegratedServer.isReady` | 去掉启动空转 |
| `Minecraft` | `doWorldLoad` RETURN | 单人：装上占位世界 |
| `Minecraft` | `setScreenAndShow` → `renderFrame` | 界面被丢弃时跳过那一帧强制渲染，消掉闪黑 |
| `Minecraft` | `tick` HEAD | 驱动被撤下的「重载配置中」界面 + 占位世界的 tick |
| `Minecraft` | `clearClientLevel` → `updateLevelInEngines` | 跨服时不释放区块网格，旧世界留在屏幕上 |
| `Minecraft` | `clearClientLevel` / `setLevel` / `disconnect` | 交还渲染引擎 + 诊断分段 |
| `ClientPacketListener` | `handleConfigurationStart` ×2 | 跨服：快照旧世界 → 装上占位世界 |
| `ClientPacketListener` | `handleLogin` HEAD | 真世界到达，撤下占位世界 |
| `ClientPacketListener` | `<init>` RETURN | 检查点：配置阶段结束 |
| `ClientPacketListener` | `startWaitingForNewLevel` RETURN | 撤下单人沿用中的加载界面 |
| `LevelLoadTracker` | `startClientLoad` / `loadingPacketsReceived` / `tickClientLoad` / `isLevelReady` | 闸门逻辑 |
| `Gui` | `setScreen` HEAD / `extractRenderState` | 丢弃加载界面 / 极简加载界面 |
| `Hud` | `extractRenderState` TAIL | 加载信息浮层 |
| `LocalPlayer` | `aiStep` TAIL | 区块未到达时锁定位置，区块到达后恢复正常物理 |
| `ClientCommonPacketListenerImpl` | `connection` 字段（访问器） | 接管旧监听器后把它的连接换成死连接 |
| `LevelLoadingScreen` | `loadTracker` 字段（访问器） | 取出界面正在显示的 tracker，交给浮层继续画 |
| `ServerReconfigScreen` | `connection` / `delayTicker` 字段（访问器） | 交还界面时保留已经等过的时间 |

---

## 配置

Mod Menu 里点开，或直接编辑 `.minecraft/config/noloadingscreen.json`。

| 选项 | 默认 | 说明 |
|---|---|---|
| 启用 Mod | 开 | 关闭后一切行为与原版完全一致。 *连日志都不写* |
| 允许加载时移动 | 开 | 占位世界生效时可以用移动键飘动，空格上升、Shift 下降；纯本地效果，不发包 |
| 加载信息浮层 | 开 | 界面撤掉后继续在 HUD 上画阶段文字、已等待秒数和进度条 |
| 显示进图耗时 | 关 | 每次进图后把总耗时和分段明细发到聊天框。**关掉也照样写进 `latest.log`** |

### 固定行为

以下行为不再提供单独开关；启用本模组后会固定生效：

- 世界还没到时显示占位世界；占位世界不可用时保留加载界面，并以极简样式绘制「加载地形中」界面。
- 客户端已有真世界后隐藏「加载地形中」界面，让区块直接在眼前加载。
- 单人游戏不在 `doWorldLoad` 中空等集成服务端启动，使客户端保持响应。
- 就绪闸门立即放行，不等待服务端开始发送区块，也不等待区块建模完成。
- 若放行时玩家所在区块还没到达，则将玩家锁定在服务端给出的坐标；区块到达后自动恢复正常物理。

最后一项与「允许加载时移动」作用在不同阶段：该配置只控制**占位世界**里的纯本地飘动；
位置锁定作用于**真世界已经建立、但脚下区块尚未到达**的短暂窗口，不能关闭。

---

## 兼容性与已知限制

- **纯客户端**，服务端不需要装。
- **已与 Sodium 0.9.2-alpha.4 共存验证。** 它替换了区块构建器，而闸门依赖的正是「建模完成」这个信号；
  实测两者不冲突（本模组绕过等待，不参与建模）。占位世界会多触发两次 `LevelExtractor.setLevel`
  （装上 / 撤下各一次）。
- ⚠️ **占位世界期间，别的模组在渲染钩子里看到的是那个假世界。** 绑定只发生在一帧渲染之内，
  所以 tick 事件、数据包处理里它们看到的仍是原版的 `null`；但如果某个模组在渲染回调里对
  `mc.level` / `mc.player` 做了很强的假设，理论上可能表现异常。当前不能单独关闭占位世界；
  可关闭「启用 Mod」恢复完整原版行为，以确认问题来源。
- ⚠️ **客户端和集成服务端会重叠启动**，两者争 CPU，单人启动的总墙钟时间可能略微变长——
  换来的是那一秒里窗口保持响应。当前不能单独关闭这项优化；不接受这个取舍时可关闭「启用 Mod」。
- **不缩短服务端的任何时序。** 出生点准备、世界生成、注册表同步该多久还是多久。
- 遇到问题请先关闭「启用 Mod」并复现一次，确认是不是本模组导致的，然后带日志开
  [issue](https://github.com/BingKKni/NoLoadingScreen/issues)。

### 验证状态

- `./gradlew build` 通过。
- **全部 Mixin 确认实际生效**：通过临时的 `preLaunch` 入口强制加载全部目标类触发 Mixin 变换，
  逐个检查变换后的类里是否出现了注入方法。由于 `injectors.defaultRequire = 1`，
  任何一个注入点找不到目标都会在变换时直接抛错——所以这同时证明了 `doWorldLoad → IntegratedServer.isReady`、
  `runTick → handleAccumulatedMovement`、`setScreenAndShow → renderFrame`、
  `clearClientLevel → updateLevelInEngines` 这几个 `@Redirect` 的目标调用都真实存在且被改写。
- 占位世界经过**四轮实机检验**，每一轮都改掉了只有实机才会暴露的东西：单人 `setLocalMode` 解引用空 player；
  多人构造 `ClientPacketListener` 撞上 Fabric API 的 play addon 全局槽位；跨服渲染时 `gameMode` 为 null
  撞死在 `renderItemInHand`；以及最后一轮——**安全网自己把窗口冻住了**（吞掉一帧后没有交还交换链图像，
  见上文）。现在这四条各自都有对应的处理。
- 所有占位世界失败都会写进 `latest.log`，并由对应加载界面继续流程；其中「加载地形中」界面会按极简样式绘制。
  排查时搜 `Could not build`、`Could not adopt`、`Could not install`、`failed to render`。失败那一行会带上完整现场
  （`mode=` / `bindDepth=` / `phase=` / 三个字段各自是否为空）。
- 闸门部分已在 Hypixel SkyBlock（含 Sodium 0.9.2、Skyblocker 等 148 个 mod）实机跑过。

### 时间都花在哪（实测）

Minecraft 26.2 + Fabric，148 个 mod，Hypixel SkyBlock：跨服一次总计约 1.0~1.2 秒，其中
**80~92% 是在等服务端**重发注册表和标签；单人新建世界里 98% 是世界生成。而「地形加载」这一段——
也就是闸门唯一能影响的部分——实测只有 **27~201 ms**，且放行时记录到的状态一律是「玩家区块已到达 = true」。

**所以这个模组不承诺让进图变快**，它承诺的是那 80~98% 的时间里，屏幕上不再是一块板子。
想知道自己每次进图卡在哪一段，看日志里的
`Join finished in ... ms [configuring 812ms, waitingworld 143ms, receivingchunks 74ms]`，
或者打开「显示进图耗时」直接看聊天框。

---

## 构建

```bash
git clone https://github.com/BingKKni/NoLoadingScreen.git
cd NoLoadingScreen
./gradlew build
```

产物在 `build/libs/noloadingscreen-1.0.0.jar`。需要 **JDK 25**。

发版：推一个 `v` 开头的 tag，CI 会用 tag 里的版本号构建并自动创建 GitHub Release，把 jar 附上去。

```bash
git tag v1.0.1 && git push origin v1.0.1
```

> Minecraft 26.1 是首个客户端**不再混淆**的正式版，1.21.11 是最后一个混淆正式版（[Mojang 公告](https://www.minecraft.net/en-us/article/removing-obfuscation-in-java-edition)，[Fabric 确认](https://fabricmc.net/2026/03/14/261)）。26.1 的 `version_manifest` 已经没有 `client_mappings`，Yarn 也停在了 1.21.11，
> 所以这个项目不需要任何映射，Loom 1.17 也去掉了 `modImplementation` 等重映射配置——mod 依赖就是普通的
> `implementation` / `compileOnly`。

配置界面用的是原版 `OptionInstance` + `OptionsSubScreen` 控件，**没有依赖 Cloth Config 或 YACL**：
两者各约 1.07 MB，是整个模组的几十倍；为了这样一个小型界面增加必装前置并不划算。

---

## 许可

[MIT](../LICENSE) © 2026 小布丁 ([BingKKni](https://github.com/BingKKni))
