package dev.kizuna.meridian.progression.server;

import dev.kizuna.meridian.progression.pb.ProgressionMessages.PlayerProgress;
import java.util.UUID;

/** 人体宿主必须在服务器主线程将进度读写到当前玩家身体，随身体保存。 */
public interface ProgressionStore {
    /** 新身体返回 schema_version=1、qi_notes_read=false；不接受未知 schema。 */
    PlayerProgress read(UUID player);

    /** 原子替换身体中的进度并标脏；成功返回前保证身体接受变更，不改动其他身体字段。 */
    void write(UUID player, PlayerProgress progress);
}
