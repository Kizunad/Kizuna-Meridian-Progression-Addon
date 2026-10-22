package dev.kizuna.meridian.progression.client;

import dev.kizuna.meridian.progression.ProgressionProtocol;
import dev.kizuna.meridian.progression.pb.ProgressionMessages.PlayerProgress;

/** 客户端只读投影；UI 必须等待权威同步，不能自行授予修炼权限。 */
public final class ClientProgression {
    private static PlayerProgress progress;
    private static final java.util.List<Runnable> listeners = new java.util.ArrayList<>();

    static void accept(PlayerProgress value) {
        // 权威投影替换后立即通知窗口，重置不能等待下一次菜单刷新。
        if (value.getSchemaVersion() != ProgressionProtocol.PROGRESS_SCHEMA) {
            throw new IllegalArgumentException("不支持的修炼进度版本");
        }
        progress = value;
        java.util.List.copyOf(listeners).forEach(Runnable::run);
    }

    static void clear() {
        // 断线撤销当前连接权限，不留下另一个世界的解锁状态。
        progress = null;
        java.util.List.copyOf(listeners).forEach(Runnable::run);
    }
    public static void onChanged(Runnable listener) {
        // UI 在模块登记时绑定一次，生命周期随整个客户端。
        listeners.add(java.util.Objects.requireNonNull(listener));
    }
    public static boolean canCultivate() {
        // 未同步与未读均不能开始修炼，但二者不混作持久状态。
        return progress != null && progress.getQiNotesRead();
    }
    public static void requireCultivation() {
        // 直接窗口工厂也执行检查，不能绕过主菜单的隐藏规则。
        if (!canCultivate()) {
            throw new IllegalStateException("尚未读完《炁脉札记》或进度未同步");
        }
    }
    public static boolean canGather() {
        // 聚气卷独立保存，入口仍位于札记解锁的修炼页。
        return canCultivate() && progress.getGatheringScrollRead();
    }

    public static boolean canUseMiddleJiao() {
        // 中焦和自动共用本卷权限，不读取手动冲关次数。
        return canCultivate() && progress.getMiddleJiaoScrollRead();
    }
    public static boolean synchronizedWithServer() { return progress != null; }

    public static PlayerProgress requireProgress() {
        // 未同步是连接状态，不冒充持久化的未读状态。
        if (progress == null) {
            throw new IllegalStateException("修炼进度尚未完成权威同步");
        }
        return progress;
    }

    private ClientProgression() {}
}
