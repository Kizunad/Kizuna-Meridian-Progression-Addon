package dev.kizuna.meridian.progression.server;

import dev.kizuna.meridian.progression.ScrollContent;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 《真炁残卷》正文：竖排卷面上不夹阿拉伯数字和英文字母，也不出现分数、倍率、按键这些机制说法；
 * 污损字只是少数几处。
 */
public final class TrueQiScrollTextTest {
    private static final List<String> MECHANIC_WORDS = List.of(
            "分数", "倍率", "机制", "判定", "时窗", "冷却", "动作栏", "窗口", "界面", "按键",
            "右键", "左键", "主手", "副手", "玩家", "生物", "实体", "×", "%");
    private static final Pattern LATIN_OR_DIGIT = Pattern.compile("[A-Za-z0-9]");
    private static final int MAX_STAINS = 6;
    private static final String STAIN = "▒";

    @Test
    public void textReadsInWorld() {
        // 整卷正文逐个找机制说法、英文字母和阿拉伯数字。
        String text = ScrollContent.TRUE_QI.text();
        assertFalse("正文是空的", text.isBlank());
        List<String> found = new ArrayList<>();
        for (String word : MECHANIC_WORDS) {
            if (text.contains(word)) {
                found.add(word);
            }
        }
        if (LATIN_OR_DIGIT.matcher(text).find()) {
            found.add("英文字母或阿拉伯数字");
        }
        assertTrue("正文里有机制说法：" + found, found.isEmpty());
    }

    @Test
    public void stainsStayFew() {
        // 污损字只点缀几处，不能把正文盖掉。
        long stains = ScrollContent.TRUE_QI.columns().stream()
                .flatMap(List::stream)
                .filter(glyph -> glyph.text().equals(STAIN))
                .count();
        assertTrue("污损字太多：" + stains, stains > 0 && stains <= MAX_STAINS);
    }
}
