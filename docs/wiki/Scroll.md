# 四卷残卷

四卷各自独立记录身体进度。服务端确认真实右键后立即解锁，无须看完：《炁脉札记》解锁修炼页；内观残卷解锁「身体 → 检视」整个窗口；聚气残卷解锁吐纳及自动吐纳；中焦残卷解锁独立中焦弹窗及自动周天。具体能力卷可以先使用，实际操作仍需札记解锁修炼页。旧存档缺少内观字段时视为未读，其他卷保留。

## 内观残卷与四卷试玩

[内观正文](../../shared/src/main/resources/meridian_progression/inner-vision.txt)依次讲述闭目返照、察看皮肉、筋骨脏腑及经脉气行；内观是只读检视，不代替治疗或开脉。背景为靛蓝卷边和浅色纸面，物品为蓝带折角纸页。

内观正文 SHA-256：`5a29a076a61d0b063df70d1e417e3a3d05bdd3f085d58de3d80d0450af878998`。

创造模式打开本模组的「修炼 / Cultivation」页，图标为残卷，条目恰好依次为残卷、内观残卷、聚气残卷、中焦残卷。也可开作弊后输入：

```text
/give @s meridian_progression:fragment_scroll
/give @s meridian_progression:inner_vision_scroll
/give @s meridian_progression:gathering_scroll
/give @s meridian_progression:middle_jiao_scroll
```

先右键残卷，按 I 进入修炼页，再打开「身体 → 检视」：未读内观卷时显示「内观」「尚未读过内观残卷」。右键内观残卷立即收到动作栏提示，Esc 合卷后重开检视可见体表，再依次选择骨架、肌肉、器官和经脉。`/meridian progression reset inner_vision` 会立即关闭检视并撤销内部订阅，但保留其他三卷；`unlock inner_vision` 只恢复该卷权限。首次解锁才提示，重复阅读只重播。

内观残卷与札记共用四种箱子来源，各以独立池 12% 掉落；普通骷髅保持 3% 携卷概率，在札记与内观残卷之间等概率选择，不覆盖已有副手。

## 聚气残卷与中焦残卷

新正文使用直白现代中文，说明真实控件、服务器判定、资源费用和停止条件。共享同一竖排布局、速度和音效，但每卷按自己的列数计算播放时间。

| 正文 | 物品 ID（命名空间 meridian_progression） | 列数 | 正常播放 | 最短播放 |
|---|---|---:|---:|---:|
| [聚气残卷](../../shared/src/main/resources/meridian_progression/gathering.txt) | gathering_scroll | 54 | 240800 ms | 60200 ms |
| [中焦残卷](../../shared/src/main/resources/meridian_progression/middle-jiao.txt) | middle_jiao_scroll | 88 | 359800 ms | 89950 ms |

聚气正文 SHA-256：`e5c0ef0f851ffa86d601228d8da463ef4890d36e0e2b745c454b042a2e307680`。

中焦正文 SHA-256：`8f6baae682057597198afbcf1d567356a6b1bb283a29dc43b67d7ce8a20ee9a4`。

聚气暂从地牢、废弃矿井箱子取得，目标表集中在 `GATHERING_LOOT_TABLES`，独立概率 `GATHERING_CHEST_CHANCE=0.12f`。中焦暂从沙漠神殿、丛林神庙箱子取得，目标表集中在 `MIDDLE_JIAO_LOOT_TABLES`，独立概率 `MIDDLE_JIAO_CHEST_CHANCE=0.12f`。获取点及概率都是待平衡的命名占位常量。四卷不可堆叠、右键不消耗，各有独立生成的物品图标；原札记箱子池保持不变。

## 《炁脉札记》原文

设计来源为同级 `Kizuna-remote-session/07-单机流程.md` 第二节全部；背景参考同目录 `02-修炼体系.md`、`06-游炁突破-中焦周天.md`。正文逐字转录，仅去除 Markdown 引用与粗体标记，完整保留标点、空格、`▒`、`〔〕` 和署名。

札记唯一正文为 [qi-notes.txt](../../shared/src/main/resources/meridian_progression/qi-notes.txt)，本次保持不变。

正文 SHA-256：`8053e3137b1a399d977b9a8dfb7a85cdde9a1c4125288bfef75e39b1dff2b362`。此摘要也用于客户端/服务端内容修订核对；更换正文后必须一起更新文档与验证，不能随机改写模糊位置。

## 物品与获取

物品 ID 为 `meridian_progression:fragment_scroll`，名称“残卷”，不可堆叠，右键只读，不消耗，可反复播放。所有残卷使用相同的模糊位置。真实 Item、BS3D ItemMetadata 及 Items 零消耗绑定均在本仓库登记，Items 仓库保持通用职责。

读取只允许从真实服务端右键事件启动：核对 Items 的物品/触发/效果绑定及消费数量 0，再打开阅读会话。经用户确认，此只读交互不调用需要联合事务的 ItemUseService；登记效果没有事务内副作用，真正打开由可信宿主执行。

| 获取点 | 本轮占位规则 |
|---|---|
| 地牢 `minecraft:chests/simple_dungeon` | 独立池，12% 抽取 1 件 |
| 废弃矿井 `minecraft:chests/abandoned_mineshaft` | 同上 |
| 沙漠神殿 `minecraft:chests/desert_pyramid` | 同上 |
| 丛林神庙 `minecraft:chests/jungle_temple` | 同上 |
| 普通骷髅 | 生成初始化尾部 3% 装入空副手，札记/内观各占一半；不覆盖主手弓与已有副手 |

概率集中在 ScrollAcquisition 的 `CHEST_SCROLL_CHANCE=0.12f`、`SKELETON_SCROLL_CHANCE=0.03f`，均是待平衡的命名占位值。只注入原版内建来源，尊重数据包对战利品表的替换。骷髅不在读档时重抽；副手可见，装备掉率设为原版必掉值 `2.0f`，掉落遵循原版 doMobLoot 和物品实体规则。

## 播放

手卷横向展开，背景压暗，纸面旧色与固定折痕。文字逐列自上而下，列按从右到左的顺序排列；整排向右匀速流动，旧列从右端退出，后续列从左端进入。结尾合拢并淡出。

`▒` 仅画不规则洇墨/水渍，不显示可辨认的字；`〔字〕` 去掉括号后以半透明字与四向淡晕渲染。污渍形状和模糊位置固定。

按住空格加速，松开恢复；Esc 立即关闭，保留右键时已取得的解锁。游戏不暂停，死亡、旁观或玩家实体替换会撤销播放会话，但不撤销身体进度。窗口缩放只缩放画面，不重建播放进度或声音。

展开、末尾合拢各播放一次短纸声，正文不播放纸声；空格加速也不会重复触发。正文只有轻微低风循环，音量随展开淡入、随合拢淡出。Esc、服务器撤销和屏幕移除会立即停止本次阅读的全部声音，中途关闭不补播合拢声。

| 命名常量 | 占位值 |
|---|---:|
| NORMAL_CHARACTERS_PER_SECOND | 每秒 4 字位 |
| FAST_FORWARD_MULTIPLIER | 4 倍 |
| ROWS / VIEW_COLUMNS | 每列 14 行 / 视口 14 列 |
| OPENING_MS / CLOSING_MS | 1200 / 1600 毫秒 |

札记固定布局共 93 列，含段落空列。正文每列按完整 14 行字位计时；入场及离场经过一个视口，段落留白也参与匀速流动。正常完整播放 377300 毫秒（约 6 分 17 秒），全程加速最短 94325 毫秒（约 1 分 34 秒），展开和合拢也按相同倍速推进。四卷均从共享正文、布局和速度计算播放时长；这些时间仅用于动画，不再影响解锁。

## 图像与音效

四卷分别使用 gen-image 生成的 1536×1024 背景和 32×32 透明像素图标。札记为琥珀旧纸与墨色经脉纹，内观为靛蓝卷边与返照纹，聚气为淡青纸面与气流纹，中焦为朱砂铜色与周天纹。图标介于纸页和卷轴之间，各以轮廓和配色区分。纸面、卷边和纹样由生成图提供，程序只做纹理裁切与淡入淡出；正文标记保留原样。资源及生成提示词见 [美术资源](Art.md)。

展开纸声（0.28 秒）、合拢纸声（0.24 秒）与低风 OGG（12 秒循环）由本仓库 [脚本](../../scripts/generate_placeholder_audio.py) 原创合成，没有第三方采样，仍为占位音效。纸声使用单次首尾归零包络，短于四倍速的开合时长；低风使用预热后的循环带限噪声，不叠加周期音量起伏，不在素材首尾淡出。客户端纸声音量为 0.22，低风最高为 0.05（原为 0.13），均为可调整占位值。运行 `python3 scripts/generate_placeholder_audio.py` 可重新生成，需要本机 ffmpeg/libvorbis。

解锁事实与门槛见 [协议](Protocol.md)，本轮暂未接好的游戏行为见 [清单](Integration.md)。
