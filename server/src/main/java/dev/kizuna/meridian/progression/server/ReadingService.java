package dev.kizuna.meridian.progression.server;

import dev.kizuna.meridian.progression.ProgressionProtocol;
import dev.kizuna.meridian.progression.ScrollContent;
import dev.kizuna.meridian.progression.pb.ProgressionMessages.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/** 仅管理临时阅读凭据；持久进度始终归身体宿主，所有调用由宿主线程串行化。 */
public final class ReadingService {
    private final LongSupplier clockMs;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private ProgressionStore store;

    public ReadingService(LongSupplier clockMs) {
        this.clockMs = Objects.requireNonNull(clockMs);
    }

    public void installStore(ProgressionStore store) {
        // 身体宿主每个服务器实例只接一次，避免运行中切换进度来源。
        if (this.store != null) {
            throw new IllegalStateException("身体进度宿主已接入");
        }
        this.store = Objects.requireNonNull(store);
    }

    public OpenScroll open(UUID player, String connection) {
        // 原札记入口保留明确的内容身份，已有宿主调用语义不变。
        return open(player, connection, ScrollKind.QI_NOTES);
    }

    public OpenScroll open(UUID player, String connection, ScrollKind scroll) {
        // 重开覆盖旧令牌；客户端不能自行开始计时或把上一次阅读拼接到本次。
        ScrollContent content = ScrollContent.of(scroll);
        String token = UUID.randomUUID().toString();
        sessions.put(player, new Session(token, connection, clockMs.getAsLong(), scroll));
        return OpenScroll.newBuilder().setSessionId(token)
                .setContentRevision(content.revision()).setScroll(scroll).build();
    }

    public ReadingOutcome handle(UUID player, String connection, ReadingIntent intent) {
        // 卷种只能来自绑定当前凭据的服务端会话。
        var session = sessions.get(player);
        ReadingResult result;
        if (session == null || !session.connection.equals(connection)
                || !session.token.equals(intent.getSessionId())) {
            result = ReadingResult.INVALID_SESSION;
        } else {
            // 完成、取消和非法动作都结算本凭据，提前请求不能稍后重放解锁。
            sessions.remove(player);
            result = switch (intent.getActionCase()) {
                case CANCELLED -> ReadingResult.CANCELLED;
                case FINISHED -> finish(player, session);
                case SYNCHRONIZE, ACTION_NOT_SET -> ReadingResult.NOT_ALLOWED;
            };
        }
        var outcome = ReadingOutcome.newBuilder().setSessionId(intent.getSessionId()).setResult(result);
        if (result != ReadingResult.INVALID_SESSION) {
            outcome.setScroll(session.scroll);
        }
        return outcome.build();
    }

    private ReadingResult finish(UUID player, Session session) {
        // 时间由服务器单调时钟提供，绝不信任客户端提交的时长或加速状态。
        long elapsed = clockMs.getAsLong() - session.openedMs;
        if (elapsed < ScrollContent.of(session.scroll).minimumPlaybackMs()) {
            return ReadingResult.TOO_EARLY;
        }
        var previous = progress(player);
        boolean read = switch (session.scroll) {
            case QI_NOTES -> previous.getQiNotesRead();
            case GATHERING -> previous.getGatheringScrollRead();
            case MIDDLE_JIAO -> previous.getMiddleJiaoScrollRead();
            default -> throw new IllegalStateException("阅读会话卷种无效");
        };
        if (read) {
            return ReadingResult.ALREADY_UNLOCKED;
        }
        var updated = previous.toBuilder();
        switch (session.scroll) {
            case QI_NOTES -> updated.setQiNotesRead(true);
            case GATHERING -> updated.setGatheringScrollRead(true);
            case MIDDLE_JIAO -> updated.setMiddleJiaoScrollRead(true);
            default -> throw new IllegalStateException("阅读会话卷种无效");
        }
        store.write(player, updated.build());
        return ReadingResult.UNLOCKED;
    }

    public PlayerProgress progress(UUID player) {
        // 不创建另一份进度旁账；缺少必需接线或未知版本明确拒绝。
        if (store == null) {
            throw new IllegalStateException("身体进度宿主尚未接线");
        }
        var value = Objects.requireNonNull(store.read(player));
        if (value.getSchemaVersion() != ProgressionProtocol.PROGRESS_SCHEMA) {
            throw new IllegalStateException("不支持的身体进度版本：" + value.getSchemaVersion());
        }
        return value;
    }

    public PlayerProgress setUnlocked(UUID player, boolean unlocked) {
        // OP 重置先撤销旧阅读凭据，防止屏幕关闭后旧完成请求恢复解锁。
        sessions.remove(player);
        var value = progress(player).toBuilder().setQiNotesRead(unlocked)
                .setGatheringScrollRead(unlocked).setMiddleJiaoScrollRead(unlocked).build();
        store.write(player, value);
        return value;
    }

    public void forget(UUID player, String connection) {
        // 旧连接的迟到清理不能清除玩家重连后新建的阅读。
        sessions.computeIfPresent(player, (id, session) -> session.connection.equals(connection) ? null : session);
    }

    private record Session(String token, String connection, long openedMs, ScrollKind scroll) {}
}
