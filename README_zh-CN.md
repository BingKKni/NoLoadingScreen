# NoLoadingScreen（无加载页面）

[English](README.md) | **简体中文**

[![build](https://github.com/BingKKni/NoLoadingScreen/actions/workflows/build.yml/badge.svg)](https://github.com/BingKKni/NoLoadingScreen/actions/workflows/build.yml)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.2%20%7C%201.21.10%20%7C%201.21.11-brightgreen)](https://www.minecraft.net/)
[![Loaders](https://img.shields.io/badge/Loader-Fabric%20%2F%20NeoForge-dbd0b4)](docs/NEOFORGE.md)
[![Environment](https://img.shields.io/badge/Environment-Client-blue)]()
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

**不需要再盯着加载页面等待进入世界。**

NoLoadingScreen 是一个仅客户端运行的 Fabric / NeoForge Mod。它会隐藏 Minecraft 在创建或加入世界、切换维度以及服务器切服时显示的“加载地形中”“重载配置中”等界面，改为显示可观察的占位世界或刚刚离开的世界，让区块直接在眼前逐步加载。

> NoLoadingScreen 主要改变加载过程的呈现方式和客户端进入世界的时机，并对加载世界逻辑的过程做了一些优化。

## AI 生成内容声明

本 Mod 除美术资源、README 资源外的其他内容（包括代码）均由 AI 生成。英文版 README 由 AI 翻译。

## 功能

- 用可自由观察的虚空占位世界替代加载页面和黑屏
- 跨服时保留刚刚离开的世界，直到新世界到达
- 切服等待时按 Esc 可返回占位视图或立即断开连接
- KickWarn：切服失败、被踢或意外断线后永久保留可操作的占位世界，聊天栏立即显示原始原因（保留颜色与换行）；按 Esc → 断开连接后离开
- 被踢后冒险/旁观模式转为本地生存模式；原有生存/创造模式不变，仍可使用占位飞行
- 占位阶段保留跑跳惯性和切服前的能力飞行状态；步行支持疾跑和方块碰撞，双击空格切换飞行及穿墙
- 加载阶段可切换第三人称，合成虚空没有区块网格时仍显示人物；本地玩家的行走、手臂与相机缓动继续更新，其他实体保持冻结
- 按 E 打开带人物预览的本地物品栏，可取放/整理物品；打开物品栏或聊天时仍继续惯性、重力与动画
- 占位阶段左键每次按下最多瞬间破坏一个方块，长按转头不连续破坏；右键按生存放置规则消耗手中方块；数字键/滚轮切换快捷栏，F 交换副手；物品名称按原版计时正常淡出
- 占位阶段中键选取准星指向的方块，自动保留客户端已有的 NBT/数据组件（包括玩家头颅的皮肤资料）；按创造模式规则选取或补入快捷栏，不需 Ctrl
- 所有占位阶段的方块与物品改动在加载结束时丢弃，不发送到服务器
- 可打开聊天框，但加载期聊天与指令仅显示红色禁止提示
- 可在首个区块到达前进入世界，并在区块到达前锁定玩家位置
- 单人游戏中保持客户端画面和操作响应，不再因本地服务器启动而冻结
- 单人保存退出期间保留原世界画面，可转头、F5、E、移动/飞行及切换快捷栏；保存完成后返回主菜单，本地操作不写入存档
- 单人从「准备资源中」进入占位世界；多人从「通讯加密中」开始，跳过加密的离线服在「加入中」触发
- 单人从准备资源过渡到启动世界时复用同一占位场景，不插入过渡屏、不重置镜头
- 在 HUD 上仅显示加载阶段、等待时间和进度条，不再显示中央区块状态矩形
- 可在聊天框及日志中显示完整进图耗时和分段明细
- 启动收尾时提前解析进图类型、预编译已验证版本 Sodium 的地形着色器，减少首次进图才发生的准备工作
- 合成虚空只创建一个 Sodium 区块工作线程，并合并未使用渲染器的首次重复重建
- 加载期间把原版同步区块建模交给后台构建器，并避免 Sodium 在渲染线程上强制等待建模（显式完整帧模式除外）
- 收包和普通客户端任务分帧处理，不创建、丢弃或重排协议包；部分处理及回复会推迟到后续帧

## 安装

选择对应构建，**不同加载器/游戏版本的 JAR 不能混用**：

| Minecraft | 加载器 | Java |
|---|---|---|
| 26.2 | Fabric Loader ≥0.19.3 | 25 |
| 1.21.10 | NeoForge21.10.64 | 21 |
| 1.21.11 | NeoForge21.11.45 | 21 |

1. 安装对应版本的 [Fabric Loader](https://fabricmc.net/use/installer) 或 [NeoForge](https://neoforged.net/)。
2. 从 [Releases](https://github.com/BingKKni/NoLoadingScreen/releases) 获取匹配产物，或按[支持与构建矩阵](docs/NEOFORGE.md)自行构建。Fabric 保留原文件名；NeoForge 文件名明确包含加载器和 Minecraft 版本。
3. 将文件放入 `.minecraft/mods/`。

本 Mod 只需安装在客户端，服务器无需安装。

Fabric 可选安装 [Mod Menu](https://modrinth.com/mod/modmenu)；NeoForge 在 Mod 列表中直接提供同一设置页。也可直接编辑配置文件。

## 配置

通过 Fabric Mod Menu / NeoForge Mod 列表打开设置页面，或编辑 `.minecraft/config/noloadingscreen.json`。

| 选项 | 默认值 | 说明 |
|:--|:--:|:--|
| 启用 Mod | 开 | 总开关；关闭后恢复原版行为 |
| 允许加载时移动 | 开 | 占位世界中行走、疾跑、空格跳跃；双击空格切换飞行，飞行时空格上升、Shift 下降并允许穿墙 |
| 加载信息浮层 | 开 | 加载期间在 HUD 上显示阶段、耗时和进度 |
| 显示进图耗时 | 关 | 进图后在聊天框显示总耗时和分段明细 |

## 兼容性

NeoForge1.21.10 /1.21.11 对应 Sodium0.7.3 /0.8.14、ViaForge4.3.1 /4.3.2；已完成实际目标转换、原生收集器回归及独立 GPU 冒烟测试。可选优化模组矩阵、精确版本、构建命令和验证限制见[NeoForge 说明](docs/NEOFORGE.md)。不强制依赖或捆绑这些模组。

以下原有结果针对 **Fabric26.2**：

- Sodium 私有优化只对当前最新正式版 `0.9.2+mc26.2` 启用并经过验证；未知或预发布 Sodium 版本会安全关闭私有钩子，保留原版/该版本自身的渲染路径。
- 一个组合无窗口 Mixin 目标转换矩阵已同时通过：Sodium 0.9.2、Iris 1.11.4、ImmediatelyFast 1.16.4、Lithium 0.25.3、FerriteCore 9.0.0、EntityCulling 1.10.5、MoreCulling 1.8.1、Dynamic FPS 3.11.9、Sodium Extra 0.9.3、RRLS 5.2.8，并与 ViaFabricPlus 5.0.1 同时加载。除 Sodium 已验证的私有优化外，这些模组大多不需要专用钩子，沿用可串联的原版生命周期，不绑定第三方私有 API。
- 额外的目标转换检查也通过了 Bobby 5.2.15、Distant Horizons 3.2.0-b、FastQuit 3.1.5 和 Reese's Sodium Options 2.2.3（Minecraft 26.2）。Nvidium 当前 26.2 构建要求 Sodium 0.9.1，而已验证组合使用 Sodium 0.9.2，因此暂不将 Nvidium 宣称为已认证组合。
- ViaFabricPlus 4.6.1 与 5.0.1 的输入/生命周期目标均通过实际 JAR 无窗口检查；Sodium 0.9.2 + Fabric API 0.158.0 + ViaFabricPlus 5.0.1 的加载策略回归也通过。
- 上述结果证明对应版本能共同完成类加载和 Mixin 转换，但不等于全部 GPU、着色器、整合包 FPS 或真实服务器场景均已验收。修改世界加载流程、加载界面、渲染后端或客户端就绪判定的其他 Mod 仍可能冲突。

## 已知行为

- 启动预热会增加客户端启动收尾时间，不能保证首次进图零卡顿；独立测试仍有约 200 ms 的首次占位安装开销。
- 皮肤预加载结果衔接到真实本地玩家，服务端纹理就绪后立即交还；首次下载尚未完成时仍可能暂用默认皮肤。
- 后台建模和分帧预算不等于零卡顿：单个数据包处理、渲染器销毁、GPU 上传及驱动/资源重载仍可能阻塞。加载保护在提前就绪后延续 5 秒；超过 100 ms 的慢阶段会限量记录为 `[loading-work]`。详见[本轮核实与边界](docs/LOADING_IMPROVEMENTS.zh-CN.md)。
- 模组会立即放行客户端就绪闸门；区块到达前画面可能暂时为空，这是预期行为。
- 区块到达前只锁定玩家位置，仍可转动镜头；锁定位置跟随服务器传送更新，不伪造落地，也不覆盖新服的飞行权限。
- 提前占位使用异步预热的独立原版注册表；尚未就绪或占位出错时保留对应原版加载界面。实现与边界见[功能说明](docs/LOADING_IMPROVEMENTS.zh-CN.md)。
- 中键不会向服务器查询额外 NBT，只能复制客户端已接收的数据；方块实体尚未加载时不选取，避免生成丢失数据的默认物品。
- KickWarn 不会保持服务器连接，也不会绕过封禁或协议限制。只有收到真实断线事件才提示；服务器无响应时仍遵循原版网络超时，占位渲染失败时保留原版错误界面。
- 不跳过认证、加密、配置和保存。占位操作不改变真实玩家的坐标、飞行权限或背包，但不能据此保证所有服务器的反作弊政策都接受此模组。

## 技术细节

对加载状态机、占位世界、数据包安全、Mixin 注入点和实测数据感兴趣吗？请阅读
[实现原理与实测](docs/TECHNICAL_DETAILS.zh-CN.md)。

## 从源码构建

```bash
./gradlew build
```

根目录命令构建 Fabric26.2，产物位于 `build/libs/`。NeoForge 使用独立的 `-p platforms/neoforge-1.21.10` / `-p platforms/neoforge-1.21.11` 构建，详见[构建矩阵与产物名称](docs/NEOFORGE.md)。构建时会运行移动模型和无窗口 Mixin 回归测试；实机测试步骤见[切服修复测试说明](docs/TESTING.zh-CN.md)。

## 许可

[MIT](LICENSE) © 2026 小布丁 ([BingKKni](https://github.com/BingKKni))
