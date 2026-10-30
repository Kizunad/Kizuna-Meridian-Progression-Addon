package dev.kizuna.meridian.progression.server;

import dev.kizuna.meridian.progression.ProgressionProtocol;
import dev.kizuna.meridian.progression.ScrollContent;
import dev.kizuna.meridian.progression.pb.ProgressionMessages.Finished;
import dev.kizuna.meridian.progression.pb.ProgressionMessages.PlayerProgress;
import dev.kizuna.meridian.progression.pb.ProgressionMessages.ReadingIntent;
import dev.kizuna.meridian.progression.pb.ProgressionMessages.ReadingResult;
import dev.kizuna.meridian.progression.pb.ProgressionMessages.ScrollKind;
import dev.kizuna.meridian.progression.server.ReadingService.ProgressChange;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 新卷沿用即时授权与身体持久化，旧消息缺少字段时不能被视为已读。 */
public final class InnerVisionReadingTest {
    private static final String CONNECTION = "inner-vision-test";
    private static final byte[] LEGACY_THREE_SCROLLS = {8, 1, 16, 1, 24, 1, 32, 1};
    private final UUID player = UUID.randomUUID();
    private PlayerProgress saved = PlayerProgress.newBuilder()
            .setSchemaVersion(ProgressionProtocol.PROGRESS_SCHEMA)
            .build();

    @Test
    public void innerVisionUnlocksImmediatelyAndSingleResetPreservesOtherScrolls() {
        // 新卷可先读，重复打开不重复解锁；单卷重置不撤销札记和修炼能力。
        var service = service();
        var opened = service.open(player, CONNECTION, ScrollKind.INNER_VISION);
        assertEquals(ReadingResult.UNLOCKED, opened.result());
        assertEquals(ScrollContent.INNER_VISION.revision(), opened.message().getContentRevision());
        assertTrue(saved.getInnerVisionScrollRead());
        assertFalse(saved.getQiNotesRead());
        assertEquals(ReadingResult.ALREADY_UNLOCKED,
                service.open(player, CONNECTION, ScrollKind.INNER_VISION).result());
        verifyReset(service);
    }

    private void verifyReset(ReadingService service) {
        // 单卷重置只清本卷和播放凭据，整组重置再清所有授权。
        service.changeAll(player, ProgressChange.UNLOCK);
        var reading = service.open(player, CONNECTION, ScrollKind.INNER_VISION);
        service.changeScroll(player, ScrollKind.INNER_VISION, ProgressChange.RESET);
        assertFalse(saved.getInnerVisionScrollRead());
        assertTrue(saved.getQiNotesRead() && saved.getGatheringScrollRead() && saved.getMiddleJiaoScrollRead());
        var finished = ReadingIntent.newBuilder()
                .setSessionId(reading.message().getSessionId())
                .setFinished(Finished.getDefaultInstance())
                .build();
        assertEquals(ReadingResult.INVALID_SESSION, service.handle(player, CONNECTION, finished).getResult());
        service.changeScroll(player, ScrollKind.INNER_VISION, ProgressChange.UNLOCK);
        assertTrue(saved.getInnerVisionScrollRead());
        service.changeAll(player, ProgressChange.RESET);
        assertFalse(saved.getInnerVisionScrollRead() || saved.getQiNotesRead()
                || saved.getGatheringScrollRead() || saved.getMiddleJiaoScrollRead());
    }

    @Test
    public void oldThreeScrollBytesDecodeAsUnreadAndCanBeUpgraded() throws Exception {
        // 固定旧 wire 字节没有字段 5；加载不升级版本，也不凭其他三卷授予内观。
        saved = PlayerProgress.parseFrom(LEGACY_THREE_SCROLLS);
        assertEquals(ProgressionProtocol.PROGRESS_SCHEMA, saved.getSchemaVersion());
        assertFalse(saved.getInnerVisionScrollRead());
        var service = service();
        assertEquals(ReadingResult.UNLOCKED, service.open(player, CONNECTION, ScrollKind.INNER_VISION).result());
        var restored = PlayerProgress.parseFrom(saved.toByteArray());
        assertTrue(restored.getInnerVisionScrollRead() && restored.getQiNotesRead()
                && restored.getGatheringScrollRead() && restored.getMiddleJiaoScrollRead());
    }

    private ReadingService service() {
        // 夹具只保留宿主写入的消息，不复制阅读规则或授权计算。
        var result = new ReadingService();
        result.installStore(new ProgressionStore() {
            @Override
            public PlayerProgress read(UUID playerId) {
                // 服务端读取同一持久消息。
                return saved;
            }

            @Override
            public void write(UUID playerId, PlayerProgress progress) {
                // 权威写入直接替换消息，供序列化回归核对。
                saved = progress;
            }
        });
        return result;
    }
}
