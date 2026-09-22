# NoLoadingScreen：实现原理与实测

> 下文 API 名称与历史测量针对 Fabric26.2。NeoForge1.21.10/.11 的编译期适配、精确依赖和独立验证范围见 [NEOFORGE.md](NEOFORGE.md)；功能状态机仍共享。

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

纯客户端 Fabric 模组，**不构造、丢弃或重排协议数据包**。加载期间会将接收包处理分摊到后续帧，处理及原版回复的时机因此可能改变；这不是后台线程直接修改真实世界。

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

### 「游戏规则、游戏模式、天气、时间」是和区块一起到的吗

经常有这样的观感：单人进图时，周围方块显现出来的那一刻，进度条也差不多走完了，而游戏模式、天气这些「数据」看上去和区块是同时出现的。这不是 Mod 改了顺序，也不是看错，而是原版本来就这样发：

1. **进度条量的不是客户端。** 单人进度条来自服务端的 `LevelLoadListener`，权重固定为 `10（准备出生点）+ 初始区块数 + 49（玩家出生点周围 7×7 区块）`，全部是**服务端**加载/生成区块的进度（`LOAD_INITIAL_CHUNKS`、`LOAD_PLAYER_CHUNKS`）。最后一段 `LOAD_PLAYER_CHUNKS` 在配置阶段的 `PrepareSpawnTask` 里完成，也就是在服务端**发送 `ClientboundLoginPacket` 之前**。所以进度条到 100% 的那一刻，客户端还没有世界、没有玩家；紧接着才是登录、规则、区块。
2. **规则和世界信息都塞在登录后的同一批包里。** `PlayerList.placeNewPlayer` 在一次 `suspendFlushing()` 里依次发送：`ClientboundLoginPacket`（其中已带 `reducedDebugInfo`、`doLimitedCrafting`、`immediateRespawn` 这些游戏规则位和 `CommonPlayerSpawnInfo` 里的游戏模式、维度、种子）→ 难度 → 能力 → 手持槽 → 配方 → 权限 → 计分板 → **传送包**（第一次给客户端玩家坐标）→ 玩家列表（含皮肤 profile）→ `sendLevelInfo`（世界边界、**时钟/时间**、出生点、**天气**、`LEVEL_CHUNKS_LOAD_START`），然后 `resumeFlushing()`。这些包在同一个 TCP flush 里到达，客户端在同一个或相邻的 tick 里处理完，人眼分不出先后。
3. **区块紧跟其后。** `LEVEL_CHUNKS_LOAD_START` 之后，`PlayerChunkSender` 每 tick 从 9 个区块起步发送玩家周围已就绪的区块（它们在 `PrepareSpawnTask` 里已经加载好了），所以玩家脚下和周围的第一批区块几乎与上面那批包同时到达；远处的区块才需要生成、按批发送、逐步显现。
4. **本 Mod 改变的只是「看得见」。** 原版在这段时间显示「加载地形中」，直到玩家所在 section 建模完成才关掉界面——你看到世界时，上面的一切早就到了。本 Mod 一有 `ClientLevel` 就撤掉界面并立即放行闸门，你因此**看见了**这批包到达的瞬间；它没有创建、丢弃或重排任何数据包（`PacketProcessorMixin` 只在处理时长超过 8 ms 时在两个包之间让出一帧，队列顺序不变）。

所以「按顺序一项一项加载」的心理模型对应的是服务端准备阶段，而那一段在原版和本 Mod 里都发生在你能看见世界之前。想让区块「慢慢在眼前出现」，能看到的部分只有 `PlayerChunkSender` 之后远处区块的逐批到达与建模——在渲染距离 4 且本地集成服务端的情况下，这一段本来就只有零点几秒。

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

#### 首次加载：合成一个虚空

单人开图及首次多人连接没有旧世界可接管，因此构造一个完整的、普通的 `ClientLevel`
（空区块、平原群系、正午、晴天）和一个站在里面的 `LocalPlayer`。

⚠️ **构造 `ClientPacketListener` 是有代价的，这一点是实机跑出来才发现的**：它的构造函数被别的模组挂了钩子。
Fabric API 的网络模块在里面把新监听器注册成全局唯一的 client play addon，已经有一个时直接抛异常：

```
java.lang.IllegalStateException
    at ClientNetworkingImpl.setClientPlayAddon(ClientNetworkingImpl.java:126)
    at ClientPacketListener.handler$...$fabric-networking-api-v1$initAddon
    at ClientPacketListener.<init>
```

**只在真正的 play 监听器尚未创建的首次加载阶段构造**；切服继续接管旧世界。
即便如此，构造前后仍会把那个全局槽位保存并还原（见 `PlayAddonGuard`）——否则这个马上要被丢弃的监听器
会一直占着槽位，等真正的监听器建立时同样抛异常，而那已经在进图中途、救不回来了。
如果那个槽位因为 Fabric 内部改动而无法安全存取，本模组**宁可不构造**，保留加载界面；
其中「加载地形中」界面仍会按极简样式绘制。

#### 占位移动与冻结渲染

占位视图使用独立的每秒 20 tick 移动模型，继承速度、疾跑状态、移动与飞行速度属性，以及旧玩家的能力飞行开关。新建的合成玩家仍从步行开始，不继承上一次占位世界的飞行状态。临时切服界面不再清空持续按键；空中使用空中加速度与阻力，跳跃使用原版跳跃强度、重力和疾跑跳跃冲量，长按空格落地后继续跳。双击 W 或疾跑键可以疾跑，双击空格切换飞行；切服前已按住的空格不会被误认为一次新的双击。

非飞行时仅调用原版碰撞查询、台阶计算与潜行边缘退让，不调用带游戏副作用的 `Entity.move`。飞行时才绕过碰撞。单人合成虚空没有地形，因此在初始高度提供本地虚拟落脚面，不向世界放置方块。不执行玩家的游戏 tick；这不是水流、梯子、载具等全部原版物理的复刻。打开 E/聊天只屏蔽输入，不再清零速度或暂停重力；关闭移动配置才停用移动模拟。

受控位置在移动 tick 之间仅插值一次。拆分后的渲染时钟对世界环境返回 `1`，对相机眼高/FOV、手臂及本地玩家动画保留实时 partial tick；`EntityRenderDispatcher` 将其他实体固定为 `1`，避免反复重播其最后一次移动。此前把相机插值也固定为 `1` 是缺陷，会让这些缓动变成每秒 20 次阶梯跳变，即使 FPS 正常。渲染帧率不会推进移动模拟。

天空沿用旧世界的群系、时间和环境属性。26.2 拆除世界会清空 `Camera.attributeProbe`，而占位阶段原版不调用 `GameRenderer.tick`；仅恢复相机实体仍会取到默认黑色天空和默认太阳角度。现在在相机对齐后、天空和雾提取前刷新环境采样，单人合成世界则在构造 `ClientLevel` 前设置正午时钟，避免缓存旧亮度。

仅更新本地视觉状态：潜行/游泳姿势及缓动、手臂旋转、相机眼高/FOV、步行动画/视角摇晃/披风状态、头身朝向、动画年龄及已开始的挥手进度。飞行会退出游泳/潜行姿势，不添加自定义手部变换。不调用实体 tick 或游戏 AI。

水体采样后还需同步 `Player.wasUnderwater`：`LocalPlayer.isUnderWater()` 读取这份独立缓存，单独更新眼部流体标记并不能进入游泳姿势。状态变化时播放原版入水／出水环境音，水花音量使用占位移动的实际位移；离开水体不重复生成入水水花。本地丢弃使用无发包的挥手入口，成功拾取则在缩减／移除物品前提取原版渲染状态，通过独立 `ItemPickupParticleGroup` 更新三 tick 的吸入动画，并沿用本地碎屑的提取、时钟和清理生命周期，不解冻旧世界粒子。

上述修复共用于全部加载器，26.1.x 与 NeoForge 1.21 的流体适配同步调整。本轮仅完成全部 9 个发布目标的 `assemble` 构建，不执行冒烟、GPU 或实际玩法测试；26.1.x 的三个游戏版本仍共用每个加载器的一份发布 JAR。

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
- **绑定限于局部调用，并在 finally 中还原。** 渲染、鼠标转视角、初始化、整个局部移动/视觉更新及必要的界面初始化/点击临时使用占位字段；不绑定整个 `Minecraft.tick`，也不绑定连接驱动。正常游戏 tick、数据包处理与生命周期事件仍看到原版的空世界。

初始化姿势也必须处于上述绑定内：`updateSwimming` 会经 `AbstractClientPlayer.getPlayerInfo` 访问 `Minecraft.getConnection`。此前在绑定前初始化就是日志中该单人空指针异常的原因。

唯一一处必须额外处理的是 `Minecraft#handleKeybinds`：原版只在没有界面时才会走到它，而「没有界面」在原版里
蕴含「有玩家」，不少分支解引用 `this.player` 或发包。占位阶段保留 F5、聊天、指令框、E、数字键/滚轮、F 及本地左右键操作；剩余游戏点击丢弃。加载期间禁止补全请求，并在聊天提交和 `ClientPacketListener.sendChat/sendCommand` 两层拦截，分别显示红色「当前状态无法发言!」「当前状态无法执行指令!」。E 打开 `LoadingInventoryScreen`：继承 AbstractContainerScreen，使用一次性玩家的物品栏和原版人物预览，不额外显示警告文字。`slotClicked` 绕过 MultiPlayerGameMode，仅调用本地菜单逻辑；禁用合成、丢弃及网络驱动的鼠标扩展。其输入回调和 final tick 单独绑定占位字段，`onClose/removed` 不调用会发包的原版关闭/丢弃流程。配置连接继续独立 tick。

`PlaceholderInteraction` 通过新的射线查询操作旧 ClientLevel：左键用客户端 setBlock 立即移除方块，不调用挖掘控制器、不掉落；右键仅调用 BlockItem.place（不调用 useOn/use），保留原版放置校验，成功后确保消耗一件。快捷栏和副手交换仅修改旧玩家物品栈。每 tick 单独更新 ItemInHandRenderer 与换手计时，挥手调用不发客户端包的双参数重载。所有方块/物品改动在占位卸载时丢弃，同时清除攻击/使用按住状态，避免在新服继续执行。

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

本模组不伪造「ready 包」：`ServerboundPlayerLoadedPacket`（1.21.4 引入）仍由原版机制提前触发。但客户端就绪时机发生了变化，不能由此推导所有反作弊政策都会接受，仍须在有权限的测试服验证。

闸门固定为立即放行，不等服务端开始发区块。若此时玩家所在区块还没到达，本模组会固定锁定玩家位置，
直到该区块到达，避免客户端物理让玩家坠入虚空。锁定属于当前玩家，`handleMovePlayer` 完成原版绝对/相对坐标解析与传送确认后更新锚点，不回写旧出生点、不锁定 yaw/pitch。等待时不伪造 `onGround=true`，防止触发原版的落地取消飞行；重建世界、断开或禁用 Mod 时清除锁定。

**就绪之前的头和身体。** `LocalPlayer.tick` 在 `hasClientLoaded()` 为假时直接返回，所以从登录到 `ServerboundPlayerLoadedPacket` 发出（新建世界还有 500 ms 的 `closeDelayMs`），没有任何原版代码把视角写进 `yHeadRot` 或转动 `yBodyRot`。原版用界面盖住这段时间；本模组撤掉界面并抓住鼠标，第三人称就会看到头停在构造器的随机 yaw 上、以零 `yHeadRotO` 为插值起点逐 tick 抽搐，身体也不跟着镜头转。`LocalPlayerMixin` 于是在 `tick` 的 HEAD、且仅在尚未就绪时，跑一遍 `Player.aiStep` 的头部跟随和 `LivingEntity.tick` 的身体转向/角度归一化（`PlaceholderVisuals.followView`）。只有旋转簿记，没有移动、挥手、年龄或数据包；原版接手后姿态连续，不会突然转头。

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

`Minecraft` 自己只 tick *pending* 连接；原版界面撤下后，配置阶段连接必须由模组继续驱动。
模组保留原界面，在每个客户端 tick 直接调用原版 `ServerReconfigScreen.tick()`，直到登录成功或断开连接，不再复制连接更新分支。打开其他界面不会丢失连接的驱动权；原重载配置界面挂载时则交给原版 tick，避免重复更新。

**按 Esc 会打开带有「回到游戏」和立即可用的「断开连接」按钮的加载菜单**，不再等待原版的 600 tick 延时。断线仍交由原连接监听器处理。意外断线进入 KickWarn：保留本地场景且没有停留超时，聊天栏立即打印服务器原始被踢消息或原版超时原因（保留颜色与换行）。主动断开、成功的协议转服和单人错误不进入 KickWarn；主动返回标题界面或服务器列表时清除占位世界及渲染引擎引用。

### 五、单人保存退出画面

`SavingWorldView` 在原版拆除前捕获场景，在旧监听器断开后接管。`LoadingWaitLoop` 轮询输入，用独立 20 Hz 时钟推进已有占位移动、动画及物品栏操作，并逐帧处理鼠标。保存期间不调用 Minecraft/玩家/连接 tick，也不处理通用任务队列。仅在绑定的占位保存玩家内放行 null-screen 检查，全局 teardown 标记仍保留；保存文字改由 HUD 绘制。

保存循环仍等待原版 `server.isShutdown()`，不缩短或跳过保存。最后的界面/引擎拆除前释放场景和时钟，finally 再幂等清理；占位失败恢复原版保存界面。本地操作不写入存档。

**这不是逐块卸载动画。** 其他实体及环境保持冻结，不虚构保存百分比或区块消失顺序。

`MouseHandler` 和 `KeyboardHandler` 的输入派发使用可串联的 `@WrapOperation`。此前相互竞争的 `@Redirect` 会导致 ViaFabricPlus 4.6.1 的 `storeEvent` 启动注入失败。仅局部等待主动轮询的客户端线程输入立即执行，其余输入交还原操作链，保留其他 Mod 的调度行为。可选 `verifyInputCompatibility` 任务使用提供的未修改 ViaFabricPlus JAR 验证转换；这不是完整游戏或服务器兼容性测试。

### 六、资源准备与首次多人连接提前占位

单人在 `WorldOpenFlows.openWorldLoadLevelStem` 的「准备资源」界面后进入占位，局部包装 managedBlock 完成谓词插入输入/渲染帧，完成任务处理与失败/确认界面仍归原版所有。多人在后台加密状态回调中只发布标记，下一次客户端 tick 安装；离线服在加入阶段触发。隐藏的 ConnectScreen 仍驱动连接，挂载时不重复 tick，成功登录不关闭连接，取消沿用 aborted/channelFuture 同步语义。

`PlaceholderRegistries` 在启动时异步解码原版客户端注册表，同时独立复制静态 holder 与标签，不向全局注册表或服务器监听器应用本地标签。预热未完成/失败则保留原版界面。详见[实现与边界](LOADING_IMPROVEMENTS.zh-CN.md)和[实机测试清单](TESTING.zh-CN.md)。

1.0.4 在资源准备到 `doWorldLoad` 之间保留同一占位世界：只跳过重复的空会话清理，保留原版新 tracker，不重建玩家或重置镜头。占位本地玩家仅跳过 `LevelExtractor` 的区块网格可见性检查，继续使用原版人物渲染；加载 HUD 不再画中央区块状态矩形。

保存、配置和 KickWarn 使用共享的 `OutgoingWorld` 快照。KickWarn 仍执行原版实际断线清理，仅保留场景和网格；原版断线详情页作为安全回退及退出目的地来源。左键消费原版按下事件，不再在长按转头时不断破坏方块。

1.0.5 在被踢后的占位绑定内把冒险/旁观控制器改为生存，玩家模式查询复用该控制器而不修改共享 PlayerInfo。中键通过原版克隆、方块实体序列化/组件收集与 Inventory 选取方法工作，保留头颅皮肤等客户端可用数据；不调用服务器选取/查询包。详见[增量说明](LOADING_IMPROVEMENTS.zh-CN.md)。

### 七、皮肤交接与加载工作调度（1.0.5 增量）

真实本地玩家在 PlayerInfo 未到达或服务器皮肤 Future 未完成时，暂用已完成的账户皮肤；查询先走原版，以保证下载启动。服务器完成的自定义/默认皮肤与空结果都优先，其他玩家不受影响。

加载期间客户端收包队列采用 8 ms 软预算，普通帧任务队列采用 4 ms 软预算，只在完整包/任务边界让出，FIFO、异常与主线程归属保留。原版同步区块构建交给 `compileAsync`，Sodium 可选钩子避免 `awaitCompletion` 的主线程等待/抢任务；显式完整帧模式保留等待。提前就绪后延续 5 秒，随后恢复普通玩法策略。合成虚空的有效渲染距离最多 2，不改动已保存的选项。

预算不抢占单个操作，渲染器拆除、GPU 上传/着色器编译、注册表应用及其他 Mod 的回调仍可能卡帧。`[loading-work]` 限量记录超过 100 ms 的慢调用。实际客户端日志确认 ViaFabricPlus 4.6.2 的 RETURN 收尾仍需要活 channel，故接管改到整个处理器返回之后；Fabric play addon 被占用时直接拒绝合成。

证据、实现边界及冷/热启动对照方法见[增量说明](LOADING_IMPROVEMENTS.zh-CN.md)和[测试说明](TESTING.zh-CN.md)。已做无窗口回归及实际 Sodium/Fabric API/ViaFabricPlus JAR 转换检查，未完成完整整合包 FPS 和实服验收。

### 八、首次进图冷启动（1.0.5 追加）

复测日志显示单个登录处理仍可耗时 367 ms、收包调用 782 ms。独立 RX 580/OpenGL 测试的 JFR 在首次占位安装期间采到 JAR 读取、类加载和 Mixin 转换，因此增加启动收尾的类型签名预热：不执行初始化器或构造器，不在后台并发驱动类转换。已验证 Sodium 的三种实际地形管线也在进图前预编译，GPU 调用仍归渲染线程。

合成虚空仅创建一个工作线程；未使用、相同视距、未跨资源重载的 Sodium 渲染器合并第一次重复 reload。资源代次变化、使用/编辑/卸载会使这一优化失效，正常玩法重载保留。Fabric 配置 addon 被占用时也拒绝合成，避免其原版 setter 抛错。慢日志增加包类及 Sodium 初始化/销毁，并覆盖资源等待直接绘制的帧。

新增无窗口回归和可选 `verifyColdStartGpu` 均通过；真实图形测试只显示占位场景，不开存档、不连接服务器。类型预热后独立运行的首次占位安装约 195 至 197 ms，仍不是完全不卡顿；启动准备会增加耗时，整合包/真实地形还须复测。完整证据及命令见[追加说明](LOADING_IMPROVEMENTS.zh-CN.md)和[测试说明](TESTING.zh-CN.md)。

### Mixin 清单

主要 Mixin 和访问器使用 `@Inject` / `@Redirect` / `@ModifyVariable` / `@WrapMethod` / `@WrapOperation`，
**没有 `@Overwrite`**：

| 类 | 注入点 | 作用 |
|---|---|---|
| `PacketProcessor` | `processQueuedPackets` / 队列检查 | 仅客户端加载期间在完整包之间按预算让出，不出队重排 |
| `Minecraft` | `runTick` 任务范围 / `shouldRun` | 仅普通帧任务按预算让出，同步完成等待不受限 |
| `LevelRenderer` | `compileSections` → `compileSync` | 加载期间使用原版异步构建器 |
| `Options` | `getEffectiveRenderDistance` | 仅合成虚空限制有效距离，不写配置 |
| Sodium `RenderSectionManager`（可选） | `updateChunks` → `awaitCompletion` | 加载期间不等待/抢占后台建模，保留完整帧模式 |
| `Minecraft` | `renderFrame` `@WrapMethod` | 渲染一帧期间绑定/解绑占位世界 + 异常安全网 + 交还交换链图像 |
| `Minecraft` | `runTick` → `handleAccumulatedMovement` | 绑定后调用，让鼠标能转占位玩家的视角 |
| `Minecraft` | `handleKeybinds` HEAD | 占位期间整个跳过（见上） |
| `Minecraft` | `doWorldLoad` → `IntegratedServer.isReady` | 去掉启动空转 |
| `Minecraft` | `doWorldLoad` → `disconnectWithProgressScreen` / `Gui.setScreen` | 保留提前占位场景和镜头；捕获原版 tracker 后隐藏加载界面 |
| `Minecraft` | `setScreenAndShow` → `renderFrame` | 界面被丢弃时跳过那一帧强制渲染，消掉闪黑 |
| `Minecraft` | `tick` HEAD / `pauseGame` HEAD | 驱动配置阶段连接与占位移动 / 打开加载菜单 |
| `Minecraft` | `clearClientLevel` → `updateLevelInEngines` | 跨服时不释放区块网格，旧世界留在屏幕上 |
| `Minecraft` | `clearClientLevel` / `setLevel` / `disconnect` | 交还渲染引擎 + 诊断分段 |
| `ClientPacketListener` | `handleConfigurationStart` ×2 | 跨服：快照旧世界 → 装上占位世界 |
| `ClientPacketListener` | `handleLogin` 线程检查之后 | 真世界到达，撤下占位世界 |
| `ClientPacketListener` | `<init>` RETURN | 检查点：配置阶段结束 |
| `ClientPacketListener` | `startWaitingForNewLevel` RETURN | 撤下单人沿用中的加载界面 |
| `LevelLoadTracker` | `startClientLoad` / `loadingPacketsReceived` / `tickClientLoad` / `isLevelReady` | 闸门逻辑 |
| `Gui` | `setScreen` HEAD / `extractRenderState` | 丢弃加载界面 / 极简加载界面 |
| `GameRenderer` | `update` / `extract` / `render` | 分离实时本地视觉与冻结的环境时间 |
| `EntityRenderDispatcher` | `extractEntity` | 本地玩家使用实时动画插值，其他实体冻结 |
| `MouseHandler` | `onScroll` | 局部绑定，使原版小数滚轮/快捷栏选择可用 |
| `Gui` | `tick` → `Hud.tick` | 只为原版 HUD tick 绑定占位玩家，使快捷栏物品名检测与倒计时正常推进 |
| `Gui` | `tick` → `Screen.tick` | 只为本地物品栏的 final tick 绑定玩家 |
| `Hud` | `extractRenderState` TAIL | 加载信息浮层 |
| `LocalPlayer` | `aiStep` HEAD / TAIL | 等待区块时不伪造落地、只锁位置不锁视角，区块到达后恢复正常物理 |
| `ClientPacketListener` | `handleMovePlayer` RETURN | 原版解析和确认传送后更新位置锁定锚点 |
| `Minecraft` | `disconnect` 包装 / 保存界面挂载前 / 引擎拆除 | 单人保存或 KickWarn 接管；保留离线镜头/网格，清除真实连接所有权 |
| `ClientHandshakePacketListenerImpl` | `onDisconnect` → `Gui.setScreen` | 已有占位场景的登录失败进入统一断线清理/KickWarn |
| `DisconnectedScreen` | `parent` / `details` 访问器 | 复用原版退出目的地、原始文本和错误详情 |
| `LevelExtractor` | `isEntityVisible` → `isSectionCompiledAndVisible` | 无区块网格时仍显示绑定的占位本地人物 |
| `ClientCommonPacketListenerImpl` | `connection` 字段（访问器） | 接管旧监听器后把它的连接换成死连接 |
| `LevelLoadingScreen` | `loadTracker` 字段（访问器） | 取出界面正在显示的 tracker，交给浮层继续画 |
| `ServerReconfigScreen` | `connection` / `disconnectButton` 字段（访问器） | 持续驱动连接，并在回退界面启用立即断开连接按钮 |

---

## 配置

Mod Menu 里点开，或直接编辑 `.minecraft/config/noloadingscreen.json`。

| 选项 | 默认 | 说明 |
|---|---|---|
| 启用 Mod | 开 | 关闭后一切行为与原版完全一致。 *连日志都不写* |
| 允许加载时移动 | 开 | 占位世界中行走、疾跑与跳跃；双击空格切换飞行/穿墙，飞行时空格上升、Shift 下降；不发包 |
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
- **Sodium 私有钩子只对每个目标精确验证过的正式版启用。** 已验证 `0.9.2+mc26.2`（26.2）、`0.9.2+mc26.3`（Fabric 26.3）、`0.9.2+mc26.1.2`（26.1.2）与 `0.8.9+mc26.1.1`（26.1/26.1.1，这两个版本仍在首次使用时编译 GL 程序，因此地形着色器预热为空实现）的目标布局与行为；未知或预发布版本安全关闭这些私有优化。26.1.x 统一产物在 Fabric/NeoForge 间共用 API 族白名单，同时保留这两个已验证的 Sodium 版本；引擎和私有注入代码不变，其他版本继续使用各自策略。见[版本与同 JAR 运行验证矩阵](VERSIONS.md)。占位世界装卸仍可能触发渲染器重建和工作线程退出，目前没有将整套 GPU 资源拆除移到其他线程。
- **常见 Fabric 优化组合已做实际 JAR 的无窗口目标转换检查。** 同一矩阵包含 Iris 1.11.4、ImmediatelyFast 1.16.4、Lithium 0.25.3、FerriteCore 9.0.0、EntityCulling 1.10.5、MoreCulling 1.8.1、Dynamic FPS 3.11.9、Sodium Extra 0.9.4、BadOptimizations 2.4.1、Particle Core 0.3.3、RRLS 5.2.8、Sodium 0.9.2 和 ViaFabricPlus 5.0.1；额外组合覆盖 Bobby 5.2.15、Distant Horizons 3.2.0-b、FastQuit 3.1.5 与 Reese's Sodium Options 2.2.3。26.x 平台目标通过 `verifyCompatibility` 用各自版本的 JAR 做同类检查（NeoForge 26.1.2 含 ModernFix 5.27.22）。这只能证明这些精确版本加载时 NoLoadingScreen 钩子成功保留，不能替代 GPU 或玩法测试。源码对照发现的两处冲突已在本 Mod 侧修复：本地破坏碎屑改在 `LevelExtractor` 的调用点追加而不是在 `ParticleEngine.extract` 内部（BadOptimizations 会在粒子表为空时提前返回）；`disconnect` 中若保存/KickWarn 场景即将保留旧世界，则在 HUD 重置后、原版写 `null` 之前先把 `Minecraft.level` 置空（ModernFix 会在原版写 `null` 处清空该世界的区块与光照引擎）。除 Sodium 的精确私有优化外，生产代码优先通过可串联的原版目标与生命周期兼容，不引入脆弱的第三方私有 API 依赖。
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

当前 `./gradlew build` 会执行两项可重复检查：`movementTest`（速度、跑跳惯性、落地/墙壁、双击控制、阻尼与插值断言）以及 `verifyMixins`（无窗口 Fabric 环境中的目标类 Mixin 转换、连接驱动权、原断线原因保留、状态清理、加载菜单按钮、F5 与按键保留、消息拦截、天空探针、手臂缓动、空连接初始化/更新回归、拆分插值时钟、本地动画、菜单内物理、即时破坏/放置规则、按键和小数滚轮路由、本地物品栏生命周期，以及离线占位快捷栏物品名的切换、倒计时、暂停与空格清除）。另有可选 `verifyOptimizationCompatibility` 使用调用者提供的实际 Mod JAR 执行组合转换矩阵；可选 `verifyRepairGpu`（所有目标）在隔离的真实 GPU 客户端中回归保存等待注入输入、KickWarn、方块拾取、换手、疾跑 FOV 与物品栏帧，命令见[测试说明](TESTING.zh-CN.md)。验证程序会在创建游戏窗口前退出，不包含在可分发 JAR 内。这些检查不能替代真实多人服务器切服测试。下方实机数据属于此前测试，并不代表本次更新已经完成实机验证。

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

产物在 `build/libs/NoLoadingScreen-1.1.0-Fabric-26.2.jar`。需要 **JDK 25**。

发版：推一个 `v` 开头的 tag，CI 会用 tag 里的版本号构建并自动创建 GitHub Release，把 jar 附上去。

```bash
git tag v1.1.0 && git push origin v1.1.0
```

> Minecraft 26.1 是首个客户端**不再混淆**的正式版，1.21.11 是最后一个混淆正式版（[Mojang 公告](https://www.minecraft.net/en-us/article/removing-obfuscation-in-java-edition)，[Fabric 确认](https://fabricmc.net/2026/03/14/261)）。26.1 的 `version_manifest` 已经没有 `client_mappings`，Yarn 也停在了 1.21.11，
> 所以这个项目不需要任何映射，Loom 1.17 也去掉了 `modImplementation` 等重映射配置——mod 依赖就是普通的
> `implementation` / `compileOnly`。

配置界面用的是原版 `OptionInstance` + `OptionsSubScreen` 控件，**没有依赖 Cloth Config 或 YACL**：
两者各约 1.07 MB，是整个模组的几十倍；为了这样一个小型界面增加必装前置并不划算。

---

## 许可

[MIT](../LICENSE) © 2026 小布丁 ([BingKKni](https://github.com/BingKKni))
