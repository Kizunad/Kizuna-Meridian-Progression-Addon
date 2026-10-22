# 单机修炼流程

本仓库管理单机修炼内容与玩家进度，当前仅实现第一段残卷《炁脉札记》。后续阶段仍归本仓库，本轮不实现吐纳、开脉、外壁、周天或突破的新规则。

当前实现物品登记、战利品与骷髅获取、手卷播放、Protobuf 阅读判定和 OP 命令；Human 将解锁写进 Framework 身体事务，登录/重生同步与服务端主动修炼授权使用同一进度。UI 在未读时隐藏修炼入口，重置立即关窗并撤销主动会话。接线和实测结果分别见清单与构建页。

| 页面 | 内容 |
|---|---|
| [残卷](Scroll.md) | 正文来源、表现、占位值、获取和操作 |
| [协议与宿主](Protocol.md) | 权威时间、会话生命周期、身体读写与客户端投影 |
| [协作接线清单](Integration.md) | 外部改动责任、调用顺序与验收边界 |
| [构建与验证](Development.md) | 目录、依赖、命令、验证结果和游戏验收 |

Meridian Framework、Human、UI、Items、Vanilla-Damage、Vanilla-Healing、Human-UI、本 Progression，以及 Kizuna-Players-Animation 在各自适用端全部必装。Inventory UI Framework 与 BS3D 也是本仓库的必需接入依赖。拆仓仅划分职责；不存在缺包模式。

原创部分采用 [PolyForm Noncommercial 1.0.0](../../LICENSE)，第三方说明见 [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md)。
