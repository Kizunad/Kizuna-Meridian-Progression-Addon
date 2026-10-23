package dev.kizuna.meridian.progression.server;

import dev.kizuna.meridian.progression.ProgressionProtocol;
import dev.kizuna.meridian.progression.ScrollContent;
import dev.kizuna.meridian.progression.pb.ProgressionMessages.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 仅管理临时阅读凭据；持久进度始终归身体宿主，所有调用由宿主线程串行化。 */
public final class ReadingService {
    private final Map<UUID, Session> sessions = new HashMap<>();
    private ProgressionStore store;

    public void installStore(ProgressionStore store) {
        // 身体宿主每个服务器实例只接一次，避免运行中切换进度来源。
        if (this.store != null) {
            throw new IllegalStateException("身体进度宿主已接入");
        }
        this.store = Objects.requireNonNull(store);
    }

    public OpenedScroll open(UUID player, String connection) {
        // 原札记入口同样由可信物品右键调用，打开即取得本卷权限。
        return open(player, connection, ScrollKind.QI_NOTES);
    }

    public OpenedScroll open(UUID player, String connection, ScrollKind scroll) {
        // 只有可信宿主能打开残卷；先验证卷种并持久化，写入失败不能发出成功凭据。
        ScrollContent content = ScrollContent.of(scroll);
        ReadingResult result = unlock(player, scroll);
        String token = UUID.randomUUID().toString();
        sessions.put(player, new Session(token, connection, scroll));
        OpenScroll message = OpenScroll.newBuilder().setSessionId(token)
                .setContentRevision(content.revision()).setScroll(scroll).build();
        return new OpenedScroll(message, result);
    }

    public ReadingOutcome handle(UUID player, String connection, ReadingIntent intent) {
        // 卷种只能来自绑定当前凭据的服务端会话。
        var session = sessions.get(player);
        ReadingResult result;
        if (session == null || !session.connection.equals(connection)
                || !session.token.equals(intent.getSessionId())) {
            result = ReadingResult.INVALID_SESSION;
        } else {
            // 播放意图只结束会话，不修改身体进度；重置后旧凭据不能恢复授权。
            sessions.remove(player);
            result = switch (intent.getActionCase()) {
                case CANCELLED -> ReadingResult.CANCELLED;
                case FINISHED -> ReadingResult.COMPLETED;
                case SYNCHRONIZE, ACTION_NOT_SET -> ReadingResult.NOT_ALLOWED;
            };
        }
        var outcome = ReadingOutcome.newBuilder().setSessionId(intent.getSessionId()).setResult(result);
        if (result != ReadingResult.INVALID_SESSION) {
            outcome.setScroll(session.scroll);
        }
        return outcome.build();
    }

    private ReadingResult unlock(UUID player, ScrollKind scroll) {
        // 右键只授予对应卷种；从旧消息构建以保留其他两卷和未知字段。
        var previous = progress(player);
        boolean read = switch (scroll) {
            case QI_NOTES -> previous.getQiNotesRead();
            case GATHERING -> previous.getGatheringScrollRead();
            case MIDDLE_JIAO -> previous.getMiddleJiaoScrollRead();
            default -> throw new IllegalStateException("阅读会话卷种无效");
        };
        if (read) {
            return ReadingResult.ALREADY_UNLOCKED;
        }
        var updated = previous.toBuilder();
        switch (scroll) {
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

    /** 仅供可信宿主发送画面与首次提示；客户端不能上传此结果。 */
    public record OpenedScroll(OpenScroll message, ReadingResult result) {}

    private record Session(String token, String connection, ScrollKind scroll) {}
}
