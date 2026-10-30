package dev.kizuna.meridian.progression.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.kizuna.meridian.progression.ScrollContent;
import dev.kizuna.meridian.progression.ScrollPlayback;
import dev.kizuna.meridian.progression.pb.ProgressionMessages.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import java.util.function.Consumer;

/** 横向手卷过场；逻辑布局固定，窗口变化仅缩放，不重置阅读。 */
public final class ScrollScreen extends Screen {
    private static final int LOGICAL_WIDTH = 420;
    private static final int LOGICAL_HEIGHT = 270;
    private static final int TEXT_LEFT = 56;
    private static final int TEXT_RIGHT = TEXT_LEFT + ScrollContent.VIEW_COLUMNS * ScrollContent.COLUMN_PITCH;
    private static final int BACKGROUND_WIDTH = 1536;
    private static final int BACKGROUND_HEIGHT = 1024;
    private final String sessionId;
    private final Consumer<ReadingIntent> outbound;
    private final ScrollContent content;
    private final ScrollPlayback playback;
    private final Identifier background;
    private long lastFrameMs = Util.getMeasuringTimeMs();
    private boolean terminal;
    private ScrollAudio audio;

    public ScrollScreen(String sessionId, Consumer<ReadingIntent> outbound) {
        // 原札记界面保持可直接构造，网络入口总是传入已校验的卷种。
        this(sessionId, ScrollContent.QI_NOTES, outbound);
    }

    public ScrollScreen(String sessionId, ScrollContent content, Consumer<ReadingIntent> outbound) {
        super(Text.translatable("screen.meridian_progression.scroll"));
        // 正文、播放时序与生成背景按同一卷绑定；播放本身不再决定解锁。
        this.sessionId = sessionId;
        this.content = content;
        this.playback = new ScrollPlayback(content);
        this.outbound = outbound;
        String texture;
        if (content == ScrollContent.QI_NOTES) {
            texture = "qi_notes";
        } else if (content == ScrollContent.GATHERING) {
            texture = "gathering";
        } else if (content == ScrollContent.MIDDLE_JIAO) {
            texture = "middle_jiao";
        } else if (content == ScrollContent.INNER_VISION) {
            texture = "inner_vision";
        } else {
            throw new IllegalArgumentException("残卷缺少对应背景");
        }
        this.background = new Identifier("meridian_progression", "textures/gui/scroll/" + texture + ".png");
    }

    public String sessionId() { return sessionId; }
    @Override
    public boolean shouldPause() { return false; }

    @Override
    protected void init() {
        // init 也会因 resize 调用；音效和播放时间只能初始化一次。
        if (audio == null) {
            audio = new ScrollAudio(client.getSoundManager());
            audio.start();
            lastFrameMs = Util.getMeasuringTimeMs();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 画面按单调时间推进；展开裁切和缩放不改变正文或生成纹理的比例。
        long now = Util.getMeasuringTimeMs();
        boolean fast = client.isWindowFocused()
                && InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_SPACE);
        playback.advance(Math.max(0, now - lastFrameMs), fast);
        lastFrameMs = now;
        // 音效沿用同一播放进度；空格和窗口重排不会重新触发展开或合拢。
        audio.update(playback);
        if (playback.finished()) {
            // 直到合拢淡出结束才报告完成；removed 不得接着发送取消。
            terminal = true;
            outbound.accept(ReadingIntent.newBuilder().setSessionId(sessionId)
                    .setFinished(Finished.getDefaultInstance()).build());
            client.setScreen(null);
            return;
        }
        float open = playback.openness();
        context.fill(0, 0, width, height, color(0x000000, 0.67f * open));
        float scale = Math.min((width - 16.0f) / LOGICAL_WIDTH, (height - 16.0f) / LOGICAL_HEIGHT);
        float originX = (width - LOGICAL_WIDTH * scale) / 2;
        float originY = (height - LOGICAL_HEIGHT * scale) / 2;
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(originX, originY, 0);
        matrices.scale(scale, scale, 1);
        int left = Math.round(210 - 186 * open);
        int right = Math.round(210 + 186 * open);
        context.enableScissor(Math.round(originX + left * scale), Math.round(originY + 8 * scale),
                Math.round(originX + right * scale), Math.round(originY + 250 * scale));
        parchment(context, open);
        context.disableScissor();
        // Scissor 使用屏幕坐标，裁切同时覆盖手卷展开边缘与固定文字视口。
        if (right - left > 8) {
            context.enableScissor(Math.round(originX + Math.max(left + 4, TEXT_LEFT) * scale),
                    Math.round(originY + 30 * scale),
                    Math.round(originX + Math.min(right - 4, TEXT_RIGHT) * scale),
                    Math.round(originY + 230 * scale));
            var columns = content.columns();
            for (int column = 0; column < columns.size(); column++) {
                int x = TEXT_LEFT + (int) Math.round((playback.scrollColumns() - column - 1) * ScrollContent.COLUMN_PITCH);
                if (x < TEXT_LEFT - ScrollContent.COLUMN_PITCH || x >= TEXT_RIGHT) {
                    continue;
                }
                var glyphs = columns.get(column);
                for (int row = 0; row < glyphs.size(); row++) {
                    glyph(context, glyphs.get(row), x, 32 + row * ScrollContent.ROW_PITCH, column, row, open);
                }
            }
            context.disableScissor();
        }
        matrices.pop();
        if (open > 0.1f) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("screen.meridian_progression.controls"), width / 2,
                    Math.min(height - 12, Math.round(originY + 255 * scale)), color(0xDDCCAA, open));
        }
    }

    private void parchment(DrawContext context, float alpha) {
        // 纸面、卷边和纹样全部来自本卷生成图，只做展开裁切与淡入淡出。
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(24, 8, 0);
        matrices.scale(372.0f / BACKGROUND_WIDTH, 242.0f / BACKGROUND_HEIGHT, 1);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1, 1, 1, alpha);
        try {
            context.drawTexture(background, 0, 0, 0, 0,
                    BACKGROUND_WIDTH, BACKGROUND_HEIGHT, BACKGROUND_WIDTH, BACKGROUND_HEIGHT);
        } finally {
            // 后续文字和其他界面不能继承背景透明度或矩阵。
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.disableBlend();
            matrices.pop();
        }
    }

    private void glyph(DrawContext context, ScrollContent.Glyph glyph, int x, int y, int column, int row, float alpha) {
        // TextRenderer 会将极低 alpha 当作不透明；淡字中心也须越过该阈值，避免闪字。
        if (alpha < 0.07f) {
            return;
        }
        if (glyph.style() == ScrollContent.Style.STAIN) {
            // 无法辨认处只画不规则洇墨；形状种子固定，所有残卷保持相同模糊位置。
            int seed = column * 47 + row * 13;
            for (int band = 0; band < 7; band++) {
                int inset = Math.abs(3 - band) / 2;
                context.fill(x + inset - 1, y + band + 1, x + 9 - inset, y + band + 2,
                        color(0x514735, alpha * (0.17f + Math.floorMod(seed + band, 3) * 0.05f)));
            }
            context.fill(x + 2, y + 3, x + 7, y + 7, color(0x30291F, alpha * 0.28f));
        } else if (glyph.style() == ScrollContent.Style.FAINT) {
            for (int[] offset : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                context.drawText(textRenderer, glyph.text(), x + offset[0], y + offset[1],
                        color(0x4D4130, Math.max(0.02f, alpha * 0.075f)), false);
            }
            context.drawText(textRenderer, glyph.text(), x, y, color(0x332B21, alpha * 0.36f), false);
        } else {
            context.drawText(textRenderer, glyph.text(), x, y, color(0x332B21, alpha * 0.94f), false);
        }
    }

    private static int color(int rgb, float alpha) {
        return Math.max(0, Math.min(255, Math.round(alpha * 255))) << 24 | rgb;
    }

    public void dismiss() {
        // 服务器取消、死亡或断线属于终态，不能由 removed 反发旧请求。
        terminal = true;
        client.setScreen(null);
    }

    @Override
    public void removed() {
        // Esc、服务器撤销和正常读完共用清理，先静音再通知服务器。
        if (audio != null) {
            audio.stop();
        }
        if (!terminal) {
            terminal = true;
            outbound.accept(ReadingIntent.newBuilder().setSessionId(sessionId)
                    .setCancelled(Cancelled.getDefaultInstance()).build());
        }
    }
}
