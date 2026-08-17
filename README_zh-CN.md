# NoLoadingScreen（无加载页面）

[English](README.md) | **简体中文**

[![build](https://github.com/BingKKni/NoLoadingScreen/actions/workflows/build.yml/badge.svg)](https://github.com/BingKKni/NoLoadingScreen/actions/workflows/build.yml)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-brightgreen)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-dbd0b4)](https://fabricmc.net/)
[![Environment](https://img.shields.io/badge/Environment-Client-blue)]()
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

**不需要再盯着加载页面等待进入世界。**

NoLoadingScreen 是一个仅客户端运行的 Fabric Mod。它会隐藏 Minecraft 在创建或加入世界、切换维度以及服务器切服时显示的“加载地形中”“重载配置中”等界面，改为显示可观察的占位世界或刚刚离开的世界，让区块直接在眼前逐步加载。

> NoLoadingScreen 主要改变加载过程的呈现方式和客户端进入世界的时机，并对加载世界逻辑的过程做了一些优化。

## 功能

- 用可自由观察的虚空占位世界替代加载页面和黑屏
- 跨服时保留刚刚离开的世界，直到新世界到达
- 可在首个区块到达前进入世界，并在区块到达前锁定玩家位置
- 单人游戏中保持客户端画面和操作响应，不再因本地服务器启动而冻结
- 在 HUD 上显示加载阶段、等待时间和进度
- 可在聊天框及日志中显示完整进图耗时和分段明细
- 不创建、延迟或重排任何网络数据包

## 安装

要求：

- Minecraft 26.2
- Fabric Loader 0.19.3 或更高版本
- Java 25 或更高版本

1. 安装 [Fabric Loader](https://fabricmc.net/use/installer)。
2. 从 [Releases](https://github.com/BingKKni/NoLoadingScreen/releases) 下载 `noloadingscreen-x.y.z.jar`。
3. 将文件放入 `.minecraft/mods/`。

本 Mod 只需安装在客户端，服务器无需安装。

可选安装 [Mod Menu](https://modrinth.com/mod/modmenu)，以便在游戏内调整设置。没有 Mod Menu 时也可以直接编辑配置文件。

## 配置

通过 Mod Menu 打开设置页面，或编辑 `.minecraft/config/noloadingscreen.json`。

| 选项 | 默认值 | 说明 |
|:--|:--:|:--|
| 启用 Mod | 开 | 总开关；关闭后恢复原版行为 |
| 允许加载时移动 | 开 | 占位世界生效时允许使用移动键移动、空格上升和 Shift 下降 |
| 加载信息浮层 | 开 | 加载期间在 HUD 上显示阶段、耗时和进度 |
| 显示进图耗时 | 关 | 进图后在聊天框显示总耗时和分段明细 |

## 兼容性

- 已测试与 Sodium 0.9.2 Alpha 4 兼容。
- 暂未发现其他明确不兼容的 Mod。
- 修改世界加载流程、加载界面或客户端就绪判定的 Mod 可能与本 Mod 冲突。

## 已知行为

- 模组会立即放行客户端就绪闸门；区块到达前画面可能暂时为空，这是预期行为。
- 玩家位置会在区块到达前被锁定，避免因客户端物理计算而坠落。
- 占位世界构建失败时，会自动回退到极简加载界面。

## 技术细节

对加载状态机、占位世界、数据包安全、Mixin 注入点和实测数据感兴趣吗？请阅读
[实现原理与实测](docs/TECHNICAL_DETAILS.zh-CN.md)。

## 从源码构建

```bash
./gradlew build
```

构建产物位于 `build/libs/`。

## 许可

[MIT](LICENSE) © 2026 小布丁 ([BingKKni](https://github.com/BingKKni))
