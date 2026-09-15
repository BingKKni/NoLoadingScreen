# NoLoadingScreen（NLS，无加载页面）

<p align="center">
  <img src="src/main/resources/assets/noloadingscreen/icon.png" alt="NoLoadingScreen icon" width="128">
  <img src="image/banner.png" alt="NoLoadingScreen Banner" width="850">
</p>

[English](README.md) | **简体中文**

[![build](https://github.com/BingKKni/NoLoadingScreen/actions/workflows/build.yml/badge.svg)](https://github.com/BingKKni/NoLoadingScreen/actions/workflows/build.yml)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.1.x%20%7C%2026.2%20%7C%201.21.10%20%7C%201.21.11-brightgreen)](docs/VERSIONS.md)
[![Loaders](https://img.shields.io/badge/Loader-Fabric%20%2F%20NeoForge%20%2F%20Forge-dbd0b4)](docs/VERSIONS.md)
[![Environment](https://img.shields.io/badge/Environment-Client-blue)]()
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

**不需要再盯着加载页面等待进入世界。**

NoLoadingScreen 是一个仅客户端运行的 Forge /Fabric / NeoForge Mod。它会隐藏 Minecraft 在创建或加入世界、切换维度以及服务器切服时显示的“加载地形中”“重载配置中”等界面，改为显示可观察的占位世界或刚刚离开的世界，让区块直接在眼前逐步加载。

> NoLoadingScreen 主要改变加载过程的呈现方式和客户端进入世界的时机，并对加载世界逻辑的过程做了一些优化。

## 功能
![](image/not_installed.png)
![](image/installed.png)

- 用可自由观察的虚空占位世界替代加载页面和黑屏
- 跨服时保留刚刚离开的世界，直到新世界到达
- 切服失败、被踢或意外断线后不退出当前世界，直到您按 Esc → 主动断开连接

## 安装

选择对应构建，**不同加载器/游戏版本的 JAR 不能混用**：

| Minecraft | 加载器 | Java |
|---|---|---|
| 26.1 | Fabric / NeoForge（Beta）/ Forge | 25 |
| 26.1.1 | Fabric / NeoForge（Beta）/ Forge | 25 |
| 26.1.2 | Fabric / NeoForge / Forge | 25 |
| 26.2 | Fabric / NeoForge / Forge | 25 |
| 1.21.10 | NeoForge 21.10.64 | 21 |
| 1.21.11 | NeoForge 21.11.45 | 21 |

加载器精确版本与各版本产物见[版本矩阵](docs/VERSIONS.md)。Fabric 要求 Loader >=0.19.3。

1. 安装对应版本的 [Fabric Loader](https://fabricmc.net/use/installer)、[NeoForge](https://neoforged.net/) 或 [Forge](https://files.minecraftforge.net/)。
2. 从 [Releases](https://github.com/BingKKni/NoLoadingScreen/releases) 获取匹配产物，或按[支持与构建矩阵](docs/VERSIONS.md)自行构建。所有发布 JAR 都使用 `NoLoadingScreen-版本-加载器-Minecraft版本.jar` 格式，例如 `NoLoadingScreen-1.1.0-Fabric-26.2.jar`。
3. 将文件放入 `.minecraft/mods/`。

本 Mod 只需安装在客户端，服务器无需安装。

Fabric 可选安装 [Mod Menu](https://modrinth.com/mod/modmenu)；NeoForge 和 Forge 在 Mod 列表中直接提供同一设置页。也可直接编辑配置文件。

## 配置

通过 Fabric Mod Menu / NeoForge 或 Forge Mod 列表打开设置页面，或编辑 `.minecraft/config/noloadingscreen.json`。

| 选项 | 默认值 | 说明 |
|:--|:--:|:--|
| 启用 Mod | 开 | 总开关；关闭后恢复原版行为 |
| 允许加载时移动 | 开 | 占位世界中行走、疾跑、空格跳跃；双击空格切换飞行，飞行时空格上升、Shift 下降并允许穿墙 |
| 加载信息浮层 | 开 | 加载期间在 HUD 上显示阶段、耗时和进度 |
| 显示进图耗时 | 关 | 进图后在聊天框显示总耗时和分段明细 |

## 兼容性

NeoForge 1.21.10 /1.21.11 对应 Sodium 0.7.3 /0.8.14、ViaForge 4.3.1 / 4.3.2；已完成实际测试。可选优化模组矩阵、精确版本、构建命令和验证限制见[NeoForge 说明](docs/NEOFORGE.md)。

以下原有结果针对 **Fabric 26.2**：

- Mod 与 Sodium `0.9.2+mc26.2`, `ViaFabricPlus 4.6.1`, `ViaFabricPlus 5.0.1` 做了兼容处理，其他 Minecraft 版本或 Mod 版本不保证兼容。
- 受测试的 Mod 还有: Iris 1.11.4、ImmediatelyFast 1.16.4、Lithium 0.25.3、FerriteCore 9.0.0、EntityCulling 1.10.5、MoreCulling 1.8.1、Dynamic FPS 3.11.9、Sodium Extra 0.9.3、RRLS 5.2.8。

## 已知行为

- 启动预热会增加客户端启动收尾时间，不能保证进图零卡顿。
- 后台建模和分帧预算不等于零卡顿：单个数据包处理、渲染器销毁、GPU 上传及驱动/资源重载仍可能阻塞。加载保护在提前就绪后延续 5 秒；超过 100 ms 的慢阶段会限量记录为 `[loading-work]`。详见[本轮核实与边界](docs/LOADING_IMPROVEMENTS.zh-CN.md)。
- 模组会立即放行客户端就绪闸门；区块到达前画面可能暂时为空，这是预期行为。
- 提前占位使用异步预热的独立原版注册表；尚未就绪或占位出错时保留对应原版加载界面。实现与边界见[功能说明](docs/LOADING_IMPROVEMENTS.zh-CN.md)。
- 虽然我们在开发 Mod 时就特意针对多人游戏服务器的反作弊做了兼容（如不伪造发包、强制同步处理等），但不能保证一定不会被反作弊检测或报告。我们欢迎各位玩家实机测试后向我们报告关于多人游戏的反作弊问题（如安装了 Mod 后出现回弹、视角被多次重置等）。

## 技术细节

对加载状态机、占位世界、数据包安全、Mixin 注入点和实测数据感兴趣吗？请阅读
[实现原理与实测](docs/TECHNICAL_DETAILS.zh-CN.md)。

## 从源码构建

```bash
./gradlew build
```

根目录命令仍构建 Fabric 26.2，产物位于 `build/libs/`。其他目标使用独立的 `-p platforms/<加载器>-<Minecraft版本>` 构建，详见[构建矩阵与产物名称](docs/VERSIONS.md)。构建运行对应的无窗口回归与产物检查；实机测试步骤见[切服修复测试说明](docs/TESTING.zh-CN.md)。

## 许可

[MIT](LICENSE) © 2026 小布丁 ([BingKKni](https://github.com/BingKKni))