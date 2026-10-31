# 阅读协议与身体宿主

唯一协议为 [progression.proto](../../shared/src/main/proto/kizuna/meridian/progression/v1/progression.proto)，使用 protoc 4.36.2 生成 Java 与描述集，不维护平行 JSON 或手写消息。

## 消息

| 契约 | 用途 |
|---|---|
| PlayerProgress | 身体内持久进度：schema_version=1，qi_notes_read=2、gathering_scroll_read=3、middle_jiao_scroll_read=4、inner_vision_scroll_read=5 四个独立字段；真实右键即授予 |
| ReadingIntent | finished / cancelled / synchronize 互斥意图；客户端不提交时长或解锁值 |
| OpenScroll | 服务器随机会话 UUID、正文 SHA-256、scroll 卷种；卷种由真实物品决定 |
| ReadingOutcome | 播放完成 COMPLETED=8、凭据无效、取消或操作不允许；结果带服务端凭据绑定的 scroll。旧 TOO_EARLY=3 弃用保留编号 |
| ProgressionEvent | open / progress / outcome 的互斥服务端事件 |

使用 Inventory UI Framework 的统一路由：模块 `meridian_progression:reading`，C2S `meridian_progression:intent`，S2C `meridian_progression:event`，版本均为 1，两端能力均 required。不新增 Fabric 网络通道。RESULT 仅表示请求是否受理；解锁只以服务端身体与 progress 事件为准。

## 服务端判定

服务端真实物品右键核对登记、玩家存活与非旁观状态、统一连接就绪后，立即将对应卷种记入身体，再同步 progress 并发送 OpenScroll。无须看完，也不等待客户端确认。每个玩家最多一个播放会话；凭据绑定玩家 UUID、当前统一协议连接和当前玩家实体。客户端从 OpenScroll 才能开始播放，并先校验正文修订。

`ReadingService.open` 返回服务端 Java 记录 `OpenedScroll(message, result)`：message 为原有 OpenScroll，result 首次为 UNLOCKED、重复为 ALREADY_UNLOCKED。先成功写入身体才创建凭据，保留其他卷与未知字段；首次打开提示一次，内观提示使用动作栏，重复打开不重复写入。此结果仅供可信宿主使用，不是客户端提交的解锁证据。

收到 finished/cancelled 后只校验玩家、连接和令牌，再消费匹配凭据；finished 返回 COMPLETED，cancelled 返回 CANCELLED，二者均不修改身体进度。伪造或不同连接的令牌不能影响当前有效凭据，也不能授予任何卷。重开从头播放。死亡、旁观、玩家实体替换和断线只撤销播放，右键所得权限保留；OP 重置另行撤权并撤销凭据，旧 finished 无法恢复授权。迟到的旧连接清理不能撤销新连接的凭据，会话不落盘、不跨重连延续。

正文 SHA 继续校验播放资源一致性；最快播放时间只属于客户端表现，不再是服务器解锁门槛。消息字段编号、身体 schema 和统一路由版本保持；新增 COMPLETED 枚举不复用旧编号，适用端整套更新。

`ScrollKind.INNER_VISION=4` 与 `PlayerProgress.inner_vision_scroll_read=5` 为追加字段。保持进度 schema 1：旧存档缺少字段 5 时解码为 false，无需数据迁移，不会从其他卷推断内观已读。

## 身体接口

本仓库公开 [ProgressionStore](../../server/src/main/java/dev/kizuna/meridian/progression/server/ProgressionStore.java)：

- read(UUID) 读取当前玩家身体中的 PlayerProgress；新身体返回版本 1、未读。
- write(UUID, PlayerProgress) 在身体权威事务中原子写入、保留其他身体状态并标脏，随身体存档。异常不能留下部分变更。

生产代码没有独立文件、玩家 NBT 旁账或内存进度实现。Human 在身体 `addon_progress["meridian_progression:progress"]` 保存 `Any.pack(PlayerProgress)`，通过可信 `SetAddonProgress` 原子更新并标脏。具体内容只由 Progression 解释，Framework 不认识残卷 ID。宿主保留 Protobuf 未知字段；未知 schema_version 明确拒绝，不能覆盖未来数据。

所有宿主调用在服务器主线程完成：

1. Human 在 SERVER_STARTING 调用 ProgressionFabric.installStore(server, store)，每个服务器实例只安装一次；可早于统一模块登记。
2. 统一连接 READY 后客户端发送 synchronize，服务端读取身体并发布 progress。
3. 身体加载、重生或宿主侧修改进度后调用 ProgressionFabric.publishProgress(player)。未就绪连接在 READY 后自行请求。
4. Human 开脉要求 qi_notes_read；内观另要求 inner_vision_scroll_read；聚气另要求 gathering_scroll_read；中焦操作另要求 middle_jiao_scroll_read。普通手动及自动请求均检查身体进度，OP 调试仍走独立权限入口。重置内观仅撤销检视订阅并关窗，不中断其他卷的修炼；重置主动修炼权限按原规则归还在途并释放占用。

客户端通过 ClientProgression.synchronizedWithServer() 判断是否收到当前连接的权威进度，通过 requireProgress() 读取不可变 Protobuf 投影。UI 同步前隐藏相关入口，收到 progress 再刷新；断线清空投影。该客户端值不能代替服务端操作拦截。

`ClientProgression.canInspect()` 与 `ProgressionFabric.canInspect(player)` 均要求札记和内观卷。检视入口保持可见，未获授权时只显示 LockedNotice；订阅服务端再校验身体归属与这两个已读位。

身体宿主为必需接线，缺失直接报告集成错误，不发送伪造的未读/已读进度。`HOST_NOT_CONNECTED` 的旧枚举号保留，但当前服务端不再用它实现缺宿主路径。实际接线见 [协作清单](Integration.md)。

## 调试命令

本仓在 Framework 的 `/meridian` 注册点登记 `progression unlock` 与 `progression reset`：不带 `inner_vision` 时同时解锁或重置四卷，带 `inner_vision` 时只改内观卷；要求 OP 等级 2，省略目标时作用于执行玩家，写法见 [Framework 指令页](https://github.com/Kizunad/Kizuna-Meridian-Mod-Framework/wiki/Commands)。普通阅读始终独立解锁，调试批量授权不改变阅读规则。

命令使用同一身体宿主，先撤销旧阅读凭据再修改和同步；未接宿主时明确报错。原有 OP 命令不受阅读门槛，本仓库没有改动其他命令入口。
