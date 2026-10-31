# 构建与验证

## 目录与依赖

| 目录 | 职责 |
|---|---|
| shared | 唯一 Protobuf、正文、竖排布局与播放时间规则 |
| server | 独立于 Minecraft 的阅读会话、进度规则和身体宿主接口 |
| fabric | 实际 Item、Items/BS3D 登记、战利品、骷髅、服务端路由和 OP 命令 |
| client | 手卷画面、占位音效、输入意图和服务端进度只读投影 |
| docs/wiki | 文档唯一编辑来源；README 仅为入口 |

目标为 Java 25、Minecraft 26.2（不混淆，无映射）、Fabric Loader 0.20.1、Fabric API 0.161.0+26.2、Loom 1.19.3、Gradle 9.8.0、Protobuf 4.36.2；第三方版本取自 Framework 的 `scripts/libs.versions.toml`。Gradle 守护进程按 `gradle/gradle-daemon-jvm.properties` 使用 JDK 25，产物以 Java 25 编译。

settings 应用 Framework 的 `scripts/workspace.gradle`，相邻仓库位置与产物路径都按其装配清单生成（`-Pkizuna.dir.<清单 id>` 改指单个仓库）。Meridian Framework server、Items、BS3D server 与 Inventory UI client/server 以源码组合构建，编译直接使用上游普通 JAR 并随本仓先行构建，开发运行由完整装配按替换坐标装入。Items 是无模组 ID 的库，编译与安装嵌套使用同一个 JAR，安装时只嵌套一次。client 另以 compileOnly 声明 owo：Loom 按运行变体解析编译依赖来决定注入接口，Inventory UI 带来的 owo 会给 `GuiGraphicsExtractor` 注入接口，javac 须能解析该接口；本仓不直接调用 owo。运行统一应用 Framework 的完整装配脚本，按 fabric/client 源码模块分别排除自身，并显式提供开发环境所需的纯 Java 库。路径以 [fabric 构建文件](../../fabric/build.gradle) 和 [client 构建文件](../../client/build.gradle) 为准。

26.2 的物品默认组件（含单栈上限）在服务器数据加载后才绑定，Items 目录校验要读它们，因此卷轴的 Items 目录在每次 `SERVER_STARTING` 时加载，不在模组初始化时加载。四卷另有 `assets/meridian_progression/items/` 客户端物品定义，指向原有的 generated 模型。

## 常规验证

```bash
./gradlew build
python3 scripts/check_docs.py
python3 scripts/check_source_text.py "../Kizuna-remote-session/07-单机流程.md"
git diff --check
```

`build` 汇总四模块构建、服务端 JUnit 回归与文档检查；协议生成代码、描述集、测试输出与缓存不入库。安装产物为 fabric 和 client 的 `jar` 任务 JAR（26.2 不重映射）；shared 另输出协议 ZIP。仅编译通过不能代表游戏接线完成。

## 自动验收入口

- `./gradlew build`：protoc、四模块编译、Loom 安装 JAR、阅读/正文整体回归和文档及美术资源检查。
- `./gradlew :fabric:runGametest`：真实注册表、四箱战利品注入、骷髅携卷与已有副手保护。
- Human 仓 `:fabric:test`：真实身体事务、未读拒绝、保存恢复、重置和其他身体算子保留进度。
- Human-UI 仓 `env -u DISPLAY -u WAYLAND_DISPLAY LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1920x1100x24" ./gradlew :client:runSmoke`（无显示写法与屏幕尺寸的原因见 Human-UI 仓 `docs/wiki/Integration.md`）：独立整合服完整阅读闭环，然后验证正式内观和 GPU 人中光迹。`PROGRESSION_JOURNEY_OK` 与 `HUMAN_UI_SMOKE_OK` 必须同时出现；测试不通过 OP 旁路替代第一次正常阅读。音效检查使用真实 SoundManager：正常展开、正文重排不重播、低风音量 0.05、Esc 清理及空格加速读完后合拢一次；分别输出 `SCROLL_AUDIO_ESC_OK` 和 `SCROLL_AUDIO_COMPLETE_OK`。

## 本轮实际结果

2026-11-01 迁移到 Minecraft 26.2：本仓 `test build checkDocs` 通过，JUnit 17 项（server 16、client 1）零失败、错误、跳过，与迁移前相同；文档检查覆盖 14 个 JSON 资源；GameTest 源集编译通过。阅读界面改为 26.2 的 `extractRenderState`：背景透明度随贴图颜色传入，裁切框在单位变换下按原屏幕坐标登记，不叠加原版模糊背景。`:fabric:runGametest`、客户端阅读画面与 Human-UI smoke 都依赖完整装配，其中 Human、Vanilla Damage、Vanilla Healing 尚未迁到 26.2，本次没有运行；阅读画面效果也尚未实看。下方 1.20.1 时期的结果不代表 26.2 已验证。

2026-10-23 即时解锁与独立美术：Progression、Human、UI、Human-UI 四仓构建通过，共 64 项 JUnit（14/37/10/3）零失败、错误、跳过；Progression 1 项和 Human 3 项 GameTest 通过。Human-UI 在 Xvfb 下完成真实客户端/整合服 smoke，输出 `SCROLL_AUDIO_ESC_OK`、`SCROLL_AUDIO_COMPLETE_OK`、`AUTOMATIC_BREATHING_JOURNEY_OK`、`CULTIVATION_JOURNEY_OK`、`PROGRESSION_JOURNEY_OK`、`HUMAN_UI_SMOKE_OK`。覆盖真实右键立即解锁、立即完成与 Esc 保留权限、两卷提前关卷后的菜单、保存重启、OP 撤权与重生。三卷阅读截图保存在 Human-UI 的 `client/build/smoke/screenshots/{qi-notes,gathering,middle-jiao}-reading.png`。本轮没有重新进行 Windows Native 验收，不能将这些 Linux 客户端结果称为 WN 截图。

2026-10-21 残卷降噪：本仓四模块构建、重新执行的 13 项阅读回归与 1 项 GameTest 通过；Human-UI 构建与 3 项回归通过。在独立 WN 目录 `D:/Minecraft/Meridian-Scroll-Audio-20261021` 中运行完整装配，真实拾取右键、正文重排、Esc 取消、正常展开后空格加速读完均通过音效检查，记录到展开和合拢纸声各一次、正文低风 0.05 以及关闭后无残留声源。完整流程同时输出 `CULTIVATION_JOURNEY_OK`、`PROGRESSION_JOURNEY_OK`、`HUMAN_UI_SMOKE_OK`。播放端回环录音中正文约 −64 dBFS；素材半秒 RMS 波动低于 1 dB，循环接缝小于日常采样变化，没有原先的固定周期音量峰。录音、运行日志和测试世界只存于独立验收目录。

2026-10-20：本仓构建、13 项 JUnit 和 1 项 GameTest 通过；十一仓共 431 项整体回归、13 项 GameTest 通过。Human-UI 的完整装配 smoke 同时输出 `PROGRESSION_JOURNEY_OK` 与 `HUMAN_UI_SMOKE_OK`。

上述 2026-10-20/21 记录对应当时“完整阅读后解锁”的旧规则。2026-10-23 起，回归改为真实右键立即解锁、Esc 与立即完成保留进度，同时保留完整播放音效、修炼菜单显隐、OP 撤权、旧令牌失效、丢卷后保存重启及死亡重生检查。首次解锁始终走真实物品使用与统一路由，不用 OP 解锁替代。Human 身体回归补充服务端授权、吐纳撤权与其他玩家隔离。

动画仓尚未交付正式骨架与 `meditate` 资源，因此本次内观 smoke 以开脉会话验证重置，不宣称打坐动画视觉验收完成。窗口、纸张与人中光迹截图在 Human-UI 忽略的 `client/build/smoke/screenshots/`。

## 人工表现验收参考

在 [完整装配](Integration.md) 中检查下列实际交互，不能以绕过必需模块的独立场景代替验收。

1. 分别 `/give @s meridian_progression:fragment_scroll`、`gathering_scroll`、`middle_jiao_scroll`，确认三种图标不同、不可堆叠、手持右键不消耗、BS3D 描述正确。首次右键立即解锁对应功能，Esc 关闭后权限保留。
2. 用 `/loot` 分别生成四个目标表，多轮抽样核实独立池注入；生成普通骷髅抽样确认少数副手带卷，击杀确认必掉。关闭原版 doMobLoot 的行为遵循原版，不作为物品专用规则。
3. 阅读时确认四卷各自的生成背景、横向展开、竖排从右往左、匀速流动和正文标记。展开一声纸声，正文只有轻微低风，末尾合拢一声纸声；低风随开合淡入淡出，正文没有每隔数秒明显起伏或接缝。按住/松开空格、改变窗口尺寸不重播纸声、不叠加风声。Esc 关闭立即静音，中途退出不补播合拢声。
4. 周围保留怪物，确认游戏不暂停、能受到伤害；死亡后关闭并撤销阅读。断线、重生和连接替换清理声音与凭据。
5. 功能闭环已由上述真实客户端自动验证；可按 [接线清单](Integration.md) 在人工游玩时复核交互手感。

代码遵循单一职责：共享层定义内容与时序、规则层决定是否解锁、宿主适配负责实体与保存边界、客户端只发意图并呈现结果。本轮只建立残卷需要的契约，没有预建后续流程系统。
