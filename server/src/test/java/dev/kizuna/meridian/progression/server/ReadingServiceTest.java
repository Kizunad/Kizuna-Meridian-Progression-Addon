package dev.kizuna.meridian.progression.server;

import dev.kizuna.meridian.progression.ScrollContent;
import dev.kizuna.meridian.progression.ScrollPlayback;
import dev.kizuna.meridian.progression.pb.ProgressionMessages.*;
import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.Assert.*;

/** 使用序列化身体宿主夹具验证整个阅读协议与跨会话边界，不提供生产旁账。 */
public class ReadingServiceTest {
    private static final UUID PLAYER = UUID.randomUUID();
    private static final String CONNECTION = "connection-a";
    private final BodyFixture body = new BodyFixture();
    private final ReadingService service = connectedService();

    private ReadingService connectedService() {
        // 使用与生产一致的身体宿主接口，不提供客户端计时或解锁旁路。
        var result = new ReadingService();
        result.installStore(body);
        return result;
    }

    private static ReadingIntent finished(String token) {
        return ReadingIntent.newBuilder().setSessionId(token).setFinished(Finished.getDefaultInstance()).build();
    }

    private ReadingResult finish(String token) {
        return service.handle(PLAYER, CONNECTION, finished(token)).getResult();
    }

    @Test
    public void eachScrollImmediatelyPersistsOnlyItsOwnFlagOnOpen() throws Exception {
        // 未提交任何播放意图时即持久化；先打开新卷也不能替代札记门槛。
        for (ScrollKind kind : java.util.List.of(ScrollKind.GATHERING, ScrollKind.MIDDLE_JIAO, ScrollKind.QI_NOTES)) {
            ScrollContent content = ScrollContent.of(kind);
            var open = service.open(PLAYER, CONNECTION, kind);
            assertEquals(kind, open.message().getScroll());
            assertEquals(content.revision(), open.message().getContentRevision());
            assertEquals(ReadingResult.UNLOCKED, open.result());
            PlayerProgress progress = PlayerProgress.parseFrom(body.saved.get(PLAYER));
            assertEquals(kind == ScrollKind.QI_NOTES, progress.getQiNotesRead());
            assertTrue(progress.getGatheringScrollRead());
            assertEquals(kind != ScrollKind.GATHERING, progress.getMiddleJiaoScrollRead());
        }
        assertEquals("8053e3137b1a399d977b9a8dfb7a85cdde9a1c4125288bfef75e39b1dff2b362", ScrollContent.QI_NOTES.revision());
        assertEquals(94325, ScrollContent.QI_NOTES.minimumPlaybackMs());
        var reset = service.changeAll(PLAYER, ReadingService.ProgressChange.RESET);
        assertFalse(reset.getQiNotesRead() || reset.getGatheringScrollRead() || reset.getMiddleJiaoScrollRead());
    }

    @Test
    public void forgedAndUnopenedCompletionNeverUnlocks() {
        // 即使绕过阅读屏直接发送完成，仍须有可信右键产生的会话。
        assertEquals(ReadingResult.INVALID_SESSION, finish(UUID.randomUUID().toString()));
        assertFalse(service.progress(PLAYER).getQiNotesRead());
        assertEquals(0, body.writes);
    }

    @Test
    public void immediateCompletionOnlyClosesSessionAndCannotBeReplayed() {
        // 完成无需等待，但不能再次写入，也不能重放会话。
        var open = service.open(PLAYER, CONNECTION).message();
        assertEquals(ReadingResult.COMPLETED, finish(open.getSessionId()));
        assertEquals(ReadingResult.INVALID_SESSION, finish(open.getSessionId()));
        assertTrue(service.progress(PLAYER).getQiNotesRead());
        assertEquals(1, body.writes);
    }

    @Test
    public void failedBodyWriteDoesNotCreateASessionOrGrantProgress() {
        // 宿主保存失败不能留下可被客户端后续完成的成功凭据。
        body.failWrite = true;
        assertThrows(IllegalStateException.class, () -> service.open(PLAYER, CONNECTION));
        assertFalse(service.progress(PLAYER).getQiNotesRead());
        assertEquals(0, body.writes);
    }

    @Test
    public void cancellingAnyScrollKeepsItsUnlockAndReopeningDoesNotWriteAgain() {
        // 三卷都允许立刻 Esc；重复右键只重新播放，不撤权或重复保存。
        for (ScrollKind kind : java.util.List.of(ScrollKind.QI_NOTES, ScrollKind.GATHERING, ScrollKind.MIDDLE_JIAO)) {
            var first = service.open(PLAYER, CONNECTION, kind).message();
            PlayerProgress unlocked = service.progress(PLAYER);
            assertEquals(ReadingResult.CANCELLED, service.handle(PLAYER, CONNECTION,
                    ReadingIntent.newBuilder().setSessionId(first.getSessionId())
                            .setCancelled(Cancelled.getDefaultInstance()).build()).getResult());
            assertEquals(ReadingResult.INVALID_SESSION, finish(first.getSessionId()));
            assertEquals(unlocked, service.progress(PLAYER));
            var second = service.open(PLAYER, CONNECTION, kind);
            assertEquals(ReadingResult.ALREADY_UNLOCKED, second.result());
        }
        assertEquals(3, body.writes);
    }

    @Test
    public void openingAgainInvalidatesOnlyTheOldToken() {
        // 重开替换播放会话，旧令牌不能消费新令牌。
        var old = service.open(PLAYER, CONNECTION).message();
        var current = service.open(PLAYER, CONNECTION).message();
        assertEquals(ReadingResult.INVALID_SESSION, finish(old.getSessionId()));
        assertEquals(ReadingResult.COMPLETED, finish(current.getSessionId()));
    }

    @Test
    public void playerAndConnectionIdentityCannotBeSubstituted() {
        // 凭据不能被另一玩家或连接消费，其他身体也不会获得解锁。
        var open = service.open(PLAYER, CONNECTION).message();
        UUID otherPlayer = UUID.randomUUID();
        assertEquals(ReadingResult.INVALID_SESSION, service.handle(otherPlayer, CONNECTION,
                finished(open.getSessionId())).getResult());
        assertFalse(service.progress(otherPlayer).getQiNotesRead());
        assertEquals(ReadingResult.INVALID_SESSION, service.handle(PLAYER, "other-connection",
                finished(open.getSessionId())).getResult());
        assertEquals(ReadingResult.COMPLETED, finish(open.getSessionId()));
    }

    @Test
    public void disconnectRevokesTokenButLateOldDisconnectDoesNotRevokeNewConnection() {
        // 断线只撤销播放凭据，右键授予的身体进度跨连接保留。
        var old = service.open(PLAYER, CONNECTION).message();
        service.forget(PLAYER, CONNECTION);
        assertEquals(ReadingResult.INVALID_SESSION, finish(old.getSessionId()));
        assertTrue(service.progress(PLAYER).getQiNotesRead());
        var current = service.open(PLAYER, "reconnected").message();
        service.forget(PLAYER, CONNECTION);
        assertEquals(ReadingResult.COMPLETED, service.handle(PLAYER, "reconnected",
                finished(current.getSessionId())).getResult());
    }

    @Test
    public void repeatReadingDoesNotSaveOrUnlockAgain() {
        // 首次右键才产生解锁提示依据；重复阅读无需再次写入身体。
        var first = service.open(PLAYER, CONNECTION);
        assertEquals(ReadingResult.UNLOCKED, first.result());
        var second = service.open(PLAYER, CONNECTION);
        assertEquals(ReadingResult.ALREADY_UNLOCKED, second.result());
        assertEquals(1, body.writes);
    }

    @Test
    public void savedBodySurvivesServiceRecreationWithoutPossessingScroll() {
        // 不完成播放、不保留物品也能从身体存档恢复首次右键所得权限。
        service.open(PLAYER, CONNECTION);
        var afterReload = connectedService();
        assertTrue(afterReload.progress(PLAYER).getQiNotesRead());
        assertFalse(afterReload.progress(UUID.randomUUID()).getQiNotesRead());
    }

    @Test
    public void opResetRevokesExistingReadingAndCanUnlockWithoutWaiting() {
        // 管理员撤权后，旧画面的完成意图不能恢复已重置的权限。
        var old = service.open(PLAYER, CONNECTION).message();
        assertTrue(service.changeAll(PLAYER, ReadingService.ProgressChange.UNLOCK).getQiNotesRead());
        assertFalse(service.changeAll(PLAYER, ReadingService.ProgressChange.RESET).getQiNotesRead());
        assertEquals(ReadingResult.INVALID_SESSION, finish(old.getSessionId()));
        assertFalse(service.progress(PLAYER).getQiNotesRead());
        var current = service.open(PLAYER, CONNECTION);
        assertEquals(ReadingResult.UNLOCKED, current.result());
    }

    @Test
    public void protobufRoundTripsRealOpenFinishAndBodyState() throws Exception {
        // 网络仍用原字段携带卷种、正文修订和凭据，完成结果明确与解锁分离。
        var open = service.open(PLAYER, CONNECTION, ScrollKind.MIDDLE_JIAO).message();
        var event = ProgressionEvent.newBuilder().setOpen(open).build();
        assertEquals(event, ProgressionEvent.parseFrom(event.toByteArray()));
        var intent = finished(open.getSessionId());
        var outcome = service.handle(PLAYER, CONNECTION, ReadingIntent.parseFrom(intent.toByteArray()));
        assertEquals(ReadingResult.COMPLETED, ReadingOutcome.parseFrom(outcome.toByteArray()).getResult());
        assertEquals(ScrollKind.MIDDLE_JIAO, outcome.getScroll());
        assertEquals(service.progress(PLAYER), PlayerProgress.parseFrom(body.saved.get(PLAYER)));
    }

    @Test
    public void fixedLayoutPreservesMarkersAndFastestPlaybackDuration() {
        // 最短时间仅约束客户端动画，不再影响右键解锁。
        var content = ScrollContent.QI_NOTES;
        var glyphs = content.columns().stream().flatMap(java.util.Collection::stream).toList();
        assertEquals(content.text().codePoints().filter(code -> code == '▒').count(),
                glyphs.stream().filter(glyph -> glyph.style() == ScrollContent.Style.STAIN).count());
        assertEquals(20, glyphs.stream().filter(glyph -> glyph.style() == ScrollContent.Style.FAINT).count());
        assertTrue(glyphs.stream().noneMatch(glyph -> glyph.text().equals("〔") || glyph.text().equals("〕")));
        var fast = new ScrollPlayback();
        fast.advance(content.minimumPlaybackMs() - 1, true);
        assertFalse(fast.finished());
        fast.advance(1, true);
        assertTrue(fast.finished());
        assertEquals(0, fast.openness(), 0);
    }

    @Test
    public void frameRateAndResizingCannotChangePlaybackDuration() {
        var smallFrames = new ScrollPlayback();
        var largeFrame = new ScrollPlayback();
        for (int frame = 0; frame < 1_000; frame++) {
            smallFrames.advance(17, frame < 500);
        }
        largeFrame.advance(8_500, true);
        largeFrame.advance(8_500, false);
        assertEquals(largeFrame.elapsedMs(), smallFrames.elapsedMs(), 0);
        assertEquals(largeFrame.scrollColumns(), smallFrames.scrollColumns(), 0);
    }

    private static final class BodyFixture implements ProgressionStore {
        final Map<UUID, byte[]> saved = new HashMap<>();
        int writes;
        boolean failWrite;

        @Override
        public PlayerProgress read(UUID player) {
            try {
                return saved.containsKey(player) ? PlayerProgress.parseFrom(saved.get(player))
                        : PlayerProgress.newBuilder().setSchemaVersion(1).build();
            } catch (com.google.protobuf.InvalidProtocolBufferException error) {
                throw new IllegalStateException(error);
            }
        }

        @Override
        public void write(UUID player, PlayerProgress progress) {
            // 模拟事务在写入前失败，确保打开流程不会提前确认成功。
            if (failWrite) {
                throw new IllegalStateException("身体写入失败");
            }
            saved.put(player, progress.toByteArray());
            writes++;
        }
    }
}
