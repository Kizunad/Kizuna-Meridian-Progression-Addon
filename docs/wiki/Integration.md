# 跨仓接线与验收

三卷授权独立：札记解锁修炼页及开脉，聚气卷控制吐纳，中焦卷控制周天、冲关、动员及突破。中焦卷同时授权自动，无八次手动成功门槛。Human 在普通网络入口直接读取身体进度；OP 重置经过既有宿主，立即停活动、归还在途并解除占用。Human-UI 完整单机验收串联三卷真实阅读、自动吐纳与周天、保存重启和重生，不新增平行进度账户。

本轮在保留密度模型的基础上接通残卷解锁。Framework 保存通用进度，Human 绑定实际身体，Progression 定义阅读规则，UI 使用权威投影控制入口。以下均为必需接线，没有缺包替代流程。

## 身体与服务端

- [x] **Framework**：`BodySnapshot.addon_progress = 16` 与可信 `SetAddonProgress = 28`，保留身体 schema 3 的密度语义。普通网络拒绝写入，事务校验只改变指定项和 revision。
- [x] **Human fabric**：实现 `ProgressionStore`，在身体 `meridian_progression:progress` 保存 `PlayerProgress` Any。无该项时初始化版本 1、未读；死亡与同 UUID 实体替换保留事实。调用可信事务并标脏，无独立存档。旧密度 schema 的迁移仍不在本轮范围。
- [x] **生命周期**：每次 `SERVER_STARTING` 安装该服务器的适配器；JOIN 先载入身体，READY 同步进度；重生重新发布；`SERVER_STOPPED` 清除运行时。
- [x] **服务端授权**：吐纳要求札记与聚气卷，开脉要求札记；中焦要求札记与中焦卷。内观 `BodyViewRequest` 检查 `canCultivate`。未授权返回 `ACCESS_DENIED`，中焦网络给出找卷提示。重置串行停止吐纳、暂停开脉、解除占用、停止动画并撤销内观订阅。
- [x] **自然身体与 OP**：出生 0.36 元、自然扩散和运动不受阅读门槛影响；原真元调试与新的解锁/重置均保留。

Progression 当前提供 `installStore`、`publishProgress`、`canCultivate`，详见 [协议](Protocol.md)。本轮没有读取/反射访问 Human 私有世界状态，也没有在本仓库建立独立持久账本。

## 客户端 UI

- [x] **UI**：使用 `ClientProgression`，由 Inventory UI 的通用 `featureAccess` 同时隐藏菜单并阻止直接打开；窗口工厂再次检查。断线清空投影，释放在客户端线程执行。
- [x] **Human-UI**：通过必装场景接口提供中焦标题和锁定提示；独立 `meridian_ui:practice` 弹窗，读取同一权威进度。普通经脉内观保留选脉与开脉。
- [x] **依赖声明**：Human 强制依赖 Progression common，UI/Human-UI 强制依赖 Progression client；Framework 客户端库保持内容无关。

服务器在真实右键时写入对应身体字段并立即同步，首次 `OpenedScroll.result=UNLOCKED` 才提示对应功能；播放无需完成，Esc 保留进度。finished 仅返回 `COMPLETED`，不得重新写入权限。实际权限来自身体，不从提示文本推断。

## 适用端与整套装配

- [x] **full-assembly.gradle**：固定装入以下产物；按当前 fabric/client 子项目区分源码，另一端仍装入，并为同仓产物声明 `builtBy`。Loom 开发运行显式提供纯 Java 库。

| 产物 | 适用端 |
|---|---|
| `fabric/build/libs/kizuna-meridian-progression-addon-fabric-0.1.0-SNAPSHOT.jar` | 服务端逻辑与物品，专用服务器及单机整合服务器 |
| `client/build/libs/kizuna-meridian-progression-addon-client-0.1.0-SNAPSHOT.jar` | 客户端，必须同时加载 common 产物 |
| `server/build/libs/kizuna-meridian-progression-addon-server-0.1.0-SNAPSHOT.jar` | 纯 Java 规则，安装时嵌套在 common；Loom 开发时显式提供 |
| `shared/build/libs/kizuna-meridian-progression-addon-shared-0.1.0-SNAPSHOT.jar` | 协议/正文/时间，安装时嵌套在 common；Loom 开发时显式提供 |

- [x] **Items 与 Framework**：Items 没有独立模组 ID，common 内嵌其 remap 库一次；开发编译/运行使用 named dev 库。Framework 使用同一源码/产物，不复制实现。
- [x] **Inventory UI / BS3D**：server 产物改为 common；客户端删除复制的服务端入口，强制依赖 common。BS3D 客户端使用独立 `backpack_storage_3d_client` ID 并加入完整装配，提供握手必需的客户端消息模块。
- [x] **运行入口**：Progression 的 GameTest 和 Human-UI 的真实整合服 smoke 使用同一完整装配，无缺包场景或安装开关。

所有 Meridian 模块（含本仓库）及 Players-Animation 均为适用端强制依赖。本仓库两个 fabric.mod.json 已声明自身可声明的适用端依赖；Framework/Items 为库，不能编造模组 ID。

## 接线后的验收

- [x] 新玩家出生总量含在途保持 0.36 元；实际客户端隐藏修炼且直接打开被拒绝。Human 回归验证服务端吐纳、内观与开脉授权；旧真元调试可用。
- [x] 真实拾取与右键后立即写入并提示一次；中途关闭或立即 finished 保留权限，重复使用不重复保存或提示。未打开的伪造 finished 与重置后的旧令牌不能授予权限。会话回归另覆盖重开、连接替换与玩家隔离。
- [x] 实际身体保存解锁，丢弃残卷、保存退出并重启世界、真实死亡并替换玩家实体后仍保持；Human 回归验证邻居进度不受重置影响。
- [x] OP 重置关闭实际经脉窗口并暂停开脉、释放占用，直接解锁随后恢复入口。Human 回归验证正在吐纳时撤权、旧控制意图拒绝与正常行走保留。
- [x] 完整客户端/整合服握手和专服 GameTest 通过；四箱战利品、骷髅携卷及已有副手保护由真实注册表 GameTest 验证。音效与画面资源实际加载；审美与听感不以自动化通过代替人工验收，详见 [构建页](Development.md)。
