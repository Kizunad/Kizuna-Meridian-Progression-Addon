package dev.kizuna.meridian.progression;

import dev.kizuna.meridian.progression.pb.ProgressionMessages.ScrollKind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** 唯一正文及固定竖排布局；分辨率只改变缩放，不改变阅读时间。 */
public final class ScrollContent {
    public static final int ROWS = 14;
    public static final int VIEW_COLUMNS = 14;
    public static final int COLUMN_PITCH = 22;
    public static final int ROW_PITCH = 14;
    public static final double NORMAL_CHARACTERS_PER_SECOND = 4.0;
    public static final double FAST_FORWARD_MULTIPLIER = 4.0;
    public static final long OPENING_MS = 1_200;
    public static final long CLOSING_MS = 1_600;
    public static final ScrollContent QI_NOTES = load("qi-notes.txt");
    public static final ScrollContent GATHERING = load("gathering.txt");
    public static final ScrollContent MIDDLE_JIAO = load("middle-jiao.txt");

    private final String text;
    private final String revision;
    private final List<List<Glyph>> columns;

    private ScrollContent(String text, String revision) {
        // 每卷独立排版，正文列数决定本卷最快完整阅读时间。
        this.text = text;
        this.revision = revision;
        var result = new ArrayList<List<Glyph>>();
        for (String paragraph : text.stripTrailing().split("\\n\\n")) {
            var glyphs = parse(paragraph);
            for (int start = 0; start < glyphs.size(); start += ROWS) {
                result.add(List.copyOf(glyphs.subList(start, Math.min(start + ROWS, glyphs.size()))));
            }
            result.add(List.of());
        }
        columns = List.copyOf(result);
    }

    private static List<Glyph> parse(String paragraph) {
        // 标记只决定字迹样式，不能作为可读正文画出或随机补全。
        var glyphs = new ArrayList<Glyph>();
        boolean faint = false;
        for (int code : paragraph.codePoints().toArray()) {
            if (code == '〔') {
                if (faint) {
                    throw new IllegalStateException("正文淡字标记嵌套");
                }
                faint = true;
            } else if (code == '〕') {
                if (!faint) {
                    throw new IllegalStateException("正文淡字标记不配对");
                }
                faint = false;
            } else if (code != '\n') {
                glyphs.add(new Glyph(new String(Character.toChars(code)),
                        code == '▒' ? Style.STAIN : faint ? Style.FAINT : Style.INK));
            }
        }
        if (faint) {
            throw new IllegalStateException("正文淡字标记未闭合");
        }
        return glyphs;
    }

    private static ScrollContent load(String resource) {
        // 资源缺失或内容错误必须阻止启动，不能播放与服务端不同的替代正文。
        try (var stream = ScrollContent.class.getResourceAsStream("/meridian_progression/" + resource)) {
            if (stream == null) {
                throw new IllegalStateException("缺少残卷正文：" + resource);
            }
            byte[] bytes = stream.readAllBytes();
            return new ScrollContent(new String(bytes, StandardCharsets.UTF_8),
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("无法读取残卷：" + resource, failure);
        }
    }

    public static ScrollContent of(ScrollKind kind) {
        // 未声明的卷不能被当成札记，避免正文与服务器凭据错配。
        return switch (kind) {
            case QI_NOTES -> QI_NOTES;
            case GATHERING -> GATHERING;
            case MIDDLE_JIAO -> MIDDLE_JIAO;
            default -> throw new IllegalArgumentException("未知残卷类型：" + kind);
        };
    }

    public String text() { return text; }
    public String revision() { return revision; }
    public List<List<Glyph>> columns() { return columns; }

    public double scrollingMs() {
        // 开头和结尾各经过同一个视口；每列按完整行数计时，段落空列也参与匀速流动。
        return (columns.size() + VIEW_COLUMNS) * ROWS * 1_000.0 / NORMAL_CHARACTERS_PER_SECOND;
    }

    public double totalMs() { return OPENING_MS + scrollingMs() + CLOSING_MS; }
    public long minimumPlaybackMs() { return (long) Math.ceil(totalMs() / FAST_FORWARD_MULTIPLIER); }

    public enum Style { INK, STAIN, FAINT }
    public record Glyph(String text, Style style) {}
}
