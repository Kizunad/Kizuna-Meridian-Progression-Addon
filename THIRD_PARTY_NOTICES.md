# 第三方说明

本项目原创部分采用 PolyForm Noncommercial 1.0.0；不改变依赖的原始许可。

| 组件 | 许可与使用方式 |
|---|---|
| Meridian Mod Framework / Items Addon | PolyForm Noncommercial 1.0.0；编译引用 Framework，安装产物内嵌原始 Items 库及其许可，不复制源码 |
| Backpack and Storage 3D | MIT，Copyright (c) 2026 Kizunad；调用 ItemMetadata，作为整套必需运行依赖，不内嵌；原许可见 LICENSE-BS3D |
| Kizuna Inventory UI Framework | MIT；复用客户端和服务端统一 Protobuf 路由，必需运行依赖，不内嵌；原许可见 LICENSE-INVENTORY-UI |
| Protocol Buffers 4.36.2 | BSD 3-Clause；生成本项目协议，内嵌原始 Java 运行库；许可见 LICENSE-PROTOBUF |
| Fabric Loader / Fabric API | Apache-2.0；游戏宿主提供，不内嵌 |
| Fabric Loom / Yarn | MIT / CC0-1.0；仅构建和映射使用，不分发映射或游戏类 |
| Gradle Wrapper 8.8 | Apache-2.0 及其附带组件许可；从同级 Items 仓库复制，保留 LICENSE-GRADLE、NOTICE-GRADLE 和原始版权头 |
| Protobuf Gradle Plugin 0.9.6 | Apache-2.0；仅构建使用 |
| JUnit 4.13.2 / Hamcrest 1.3 | EPL-1.0 / BSD-3-Clause；仅测试使用，不进入安装产物 |
| ffmpeg / libvorbis | 仅作为本机占位音效编码工具，不分发二进制；音效为本仓库脚本原创合成，不采样第三方录音 |
| Minecraft 1.20.1 | Mojang/Microsoft 专有许可与 EULA；不分发游戏类与原版纸张素材，物品使用自行生成的纹理与宿主 generated 模型父级 |

三卷背景与物品图标由 `gpt-image-2` 按本项目提示词生成，不复制第三方图片；资源来源、后处理和完整提示词见 [美术资源](docs/wiki/Art.md)。

所有 Meridian 模块和 Players-Animation 在适用端均须安装。具体组件关系见 [装配清单](docs/wiki/Integration.md)。本地 SNAPSHOT 不代表已发布的 Maven 坐标或稳定二进制兼容承诺。
