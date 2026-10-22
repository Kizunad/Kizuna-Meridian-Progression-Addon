# 三卷残卷

三卷各自独立记录身体进度。《炁脉札记》解锁修炼页；聚气残卷解锁吐纳及自动吐纳；中焦残卷解锁独立中焦弹窗及自动周天。后两卷可以先阅读，实际操作仍需札记解锁修炼页。没有旧存档迁移。

## 聚气残卷与中焦残卷

新正文使用直白现代中文，说明真实控件、服务器判定、资源费用和停止条件。共享同一竖排布局、速度和音效，但每卷按自己的列数计算播放时间。

| 正文 | 物品 ID（命名空间 meridian_progression） | 列数 | 正常播放 | 最短播放 |
|---|---|---:|---:|---:|
| [聚气残卷](../../shared/src/main/resources/meridian_progression/gathering.txt) | gathering_scroll | 53 | 237300 ms | 59325 ms |
| [中焦残卷](../../shared/src/main/resources/meridian_progression/middle-jiao.txt) | middle_jiao_scroll | 87 | 356300 ms | 89075 ms |

聚气正文 SHA-256：`4a0e777b72de4d1bb54ebd93e04df5bff7d57e6d0db03bdb830e6d964f42bf63`。

中焦正文 SHA-256：`0dfd192c093df8453edb493f1efebfb81efa29ea963a6081b929d7f894e9efc2`。

聚气暂从地牢、废弃矿井箱子取得，目标表集中在 `GATHERING_LOOT_TABLES`，独立概率 `GATHERING_CHEST_CHANCE=0.12f`。中焦暂从沙漠神殿、丛林神庙箱子取得，目标表集中在 `MIDDLE_JIAO_LOOT_TABLES`，独立概率 `MIDDLE_JIAO_CHEST_CHANCE=0.12f`。获取点及概率都是待平衡的命名占位常量。新卷不可堆叠、右键不消耗，沿用纸张占位模型；原札记获取规则保持不变。

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
| 普通骷髅 | 生成初始化尾部 3% 装入空副手；不覆盖主手弓与已有副手 |

概率集中在 ScrollAcquisition 的 `CHEST_SCROLL_CHANCE=0.12f`、`SKELETON_SCROLL_CHANCE=0.03f`，均是待平衡的命名占位值。只注入原版内建来源，尊重数据包对战利品表的替换。骷髅不在读档时重抽；副手可见，装备掉率设为原版必掉值 `2.0f`，掉落遵循原版 doMobLoot 和物品实体规则。

## 播放

手卷横向展开，背景压暗，纸面旧色与固定折痕。文字逐列自上而下，列按从右到左的顺序排列；整排向右匀速流动，旧列从右端退出，后续列从左端进入。结尾合拢并淡出。

`▒` 仅画不规则洇墨/水渍，不显示可辨认的字；`〔字〕` 去掉括号后以半透明字与四向淡晕渲染。污渍形状和模糊位置固定。

按住空格加速，松开恢复；Esc 立即关闭，中途退出不解锁。游戏不暂停，死亡、旁观或玩家实体替换会撤销阅读。窗口缩放只缩放画面，不重建播放进度或声音。

展开、末尾合拢各播放一次短纸声，正文不播放纸声；空格加速也不会重复触发。正文只有轻微低风循环，音量随展开淡入、随合拢淡出。Esc、服务器撤销和屏幕移除会立即停止本次阅读的全部声音，中途关闭不补播合拢声。

| 命名常量 | 占位值 |
|---|---:|
| NORMAL_CHARACTERS_PER_SECOND | 每秒 4 字位 |
| FAST_FORWARD_MULTIPLIER | 4 倍 |
| ROWS / VIEW_COLUMNS | 每列 14 行 / 视口 14 列 |
| OPENING_MS / CLOSING_MS | 1200 / 1600 毫秒 |

固定布局共 93 列，含段落空列。正文每列按完整 14 行字位计时；入场及离场经过一个视口，段落留白也参与匀速流动。正常完整播放 377300 毫秒（约 6 分 17 秒），全程加速最短 94325 毫秒（约 1 分 34 秒），展开和合拢也按相同倍速推进。服务端从相同资源、布局与速度计算最短时长，不写另一份魔法数字。

## 占位资源

物品模型暂时引用 `minecraft:item/paper`，没有定稿残卷贴图；副手渲染沿用该物品模型。卷轴纸面、卷轴轴杆、折痕及墨渍由代码绘制，都是本轮占位美术。

展开纸声（0.28 秒）、合拢纸声（0.24 秒）与低风 OGG（12 秒循环）由本仓库 [脚本](../../scripts/generate_placeholder_audio.py) 原创合成，没有第三方采样，也是占位音效。纸声使用单次首尾归零包络，短于四倍速的开合时长；低风使用预热后的循环带限噪声，不叠加周期音量起伏，不在素材首尾淡出。客户端纸声音量为 0.22，低风最高为 0.05（原为 0.13），均为可调整占位值。运行 `python3 scripts/generate_placeholder_audio.py` 可重新生成，需要本机 ffmpeg/libvorbis。正式美术与录音尚待替换。

解锁事实与门槛见 [协议](Protocol.md)，本轮暂未接好的游戏行为见 [清单](Integration.md)。
