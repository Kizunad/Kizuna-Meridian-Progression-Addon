package dev.kizuna.meridian.progression;

/** 实时时间积分，不受帧率、窗口重排或服务器 tick 速度影响。 */
public final class ScrollPlayback {
    private final ScrollContent content;
    private double elapsedMs;

    public ScrollPlayback() {
        // 原有札记调用仍使用同一正文和时间。
        this(ScrollContent.QI_NOTES);
    }

    public ScrollPlayback(ScrollContent content) {
        // 会话选定的正文贯穿整次播放，窗口重排不能换卷或重计时。
        this.content = java.util.Objects.requireNonNull(content);
    }

    public void advance(long realElapsedMs, boolean fastForward) {
        // 只累计单调时钟增量；空格同时加速展开、正文与合拢。
        if (realElapsedMs < 0) {
            throw new IllegalArgumentException("播放时间不能倒退");
        }
        elapsedMs = Math.min(content.totalMs(), elapsedMs
                + realElapsedMs * (fastForward ? ScrollContent.FAST_FORWARD_MULTIPLIER : 1.0));
    }

    public double elapsedMs() { return elapsedMs; }
    public boolean finished() {
        // 结尾合拢完成才发送本卷完成意图。
        return elapsedMs >= content.totalMs();
    }

    public double totalMs() {
        // 音效和画面共享本卷时长，避免新卷合拢声音错位。
        return content.totalMs();
    }

    public double scrollColumns() {
        // 按本卷列数裁切正文滚动，不影响所有卷共用的字速。
        return Math.max(0, Math.min(content.scrollingMs(),
                elapsedMs - ScrollContent.OPENING_MS))
                * ScrollContent.NORMAL_CHARACTERS_PER_SECOND / (ScrollContent.ROWS * 1_000.0);
    }

    public float openness() {
        // 本卷末尾独立计算合拢，仍沿用统一开合时长。
        return (float) Math.max(0, Math.min(1, Math.min(elapsedMs / ScrollContent.OPENING_MS,
                (content.totalMs() - elapsedMs) / ScrollContent.CLOSING_MS)));
    }
}
