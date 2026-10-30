# 残卷生成美术

2026-10-23 使用 gen-image 技能的 `scripts/images/gen.py` 生成六张独立原稿，后端为 `gpt-image-2`，质量 high。本轮仅对调用指定可用模型，没有修改生成器或全局配置。用户指定 Minecraft 物品风格，因此使用 `style=none` 和明确的像素风提示词，避免默认写实物品前缀。

## 背景

纸面、卷边、折痕和角落装饰均来自生成图；客户端只负责展开裁切、缩放和透明度。中间区域保留浅色纸面供真实正文渲染，没有在图中烘焙文字。

| 残卷 | 生成背景（1536×1024 RGB） | 提示词 |
|---|---|---|
| 炁脉札记 | [琥珀旧纸与墨纹](../../client/src/main/resources/assets/meridian_progression/textures/gui/scroll/qi_notes.png) | [原始提示词](art-prompts/qi_notes_background_prompt.txt) |
| 内观残卷 | [靛蓝卷边与返照纹](../../client/src/main/resources/assets/meridian_progression/textures/gui/scroll/inner_vision.png) | [原始提示词](art-prompts/inner_vision_background_prompt.txt) |
| 聚气残卷 | [淡青纸面与气流纹](../../client/src/main/resources/assets/meridian_progression/textures/gui/scroll/gathering.png) | [原始提示词](art-prompts/gathering_background_prompt.txt) |
| 中焦残卷 | [朱砂铜色与周天纹](../../client/src/main/resources/assets/meridian_progression/textures/gui/scroll/middle_jiao.png) | [原始提示词](art-prompts/middle_jiao_background_prompt.txt) |

## 物品图标

四卷采用不同轮廓与配色，形态介于旧纸页和半展开卷轴之间。模型继续使用 `minecraft:item/generated`，纹理使用本仓库的 `meridian_progression:item/<物品 ID>`，背包与手持使用同一资源。

| 残卷 | 物品图标（32×32 RGBA） | 提示词 |
|---|---|---|
| 炁脉札记 | [折角旧纸](../../fabric/src/main/resources/assets/meridian_progression/textures/item/fragment_scroll.png) | [原始提示词](art-prompts/fragment_scroll_icon_prompt.txt) |
| 内观残卷 | [靛蓝绳折角纸页](../../fabric/src/main/resources/assets/meridian_progression/textures/item/inner_vision_scroll.png) | [原始提示词](art-prompts/inner_vision_scroll_icon_prompt.txt) |
| 聚气残卷 | [青带单卷边纸页](../../fabric/src/main/resources/assets/meridian_progression/textures/item/gathering_scroll.png) | [原始提示词](art-prompts/gathering_scroll_icon_prompt.txt) |
| 中焦残卷 | [朱绳双卷边短卷](../../fabric/src/main/resources/assets/meridian_progression/textures/item/middle_jiao_scroll.png) | [原始提示词](art-prompts/middle_jiao_scroll_icon_prompt.txt) |

原稿保留生成 alpha；聚气图标的品红底经色键去除。仅做裁边、最近邻缩至 30×30 范围、居中放入 32×32 透明画布，并将 alpha 二值化以符合像素风。不补画主体或背景，不采样原版纸张图片。所有最终资源已放入仓库；临时原稿不作为运行依赖。

2026-10-30 内观两张原稿同样使用 gen-image 的生成器，经 CPA 调用 `gpt-image-2`、high 质量、`style=none`。背景保持 1536×1024 RGB；图标去品红底后最近邻缩至 30×30 范围，居中放入 32×32 RGBA。只归档提示词与最终运行资源，原稿保留在交接工作目录，不进入 JAR。
