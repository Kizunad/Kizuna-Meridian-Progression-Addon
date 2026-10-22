package dev.kizuna.meridian.progression.server;

import dev.kizuna.meridian.progression.ScrollContent;
import dev.kizuna.meridian.progression.ScrollPlayback;
import dev.kizuna.meridian.progression.pb.ProgressionMessages.*;
import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

/** 使用序列化身体宿主夹具验证整个阅读协议与跨会话边界，不提供生产旁账。 */
public class ReadingServiceTest {
    private static final UUID PLAYER = UUID.randomUUID();
    private static final String CONNECTION = "connection-a";
    private final AtomicLong clock = new AtomicLong(1_000);
    private final BodyFixture body = new BodyFixture();
    private final ReadingService service = connectedService();

    private ReadingService connectedService() {
        var result = new ReadingService(clock::get);
        result.installStore(body);
        return result;
    }

    private static ReadingIntent finished(String token) {
        return ReadingIntent.newBuilder().setSessionId(token).setFinished(Finished.getDefaultInstance()).build();
    }

    private ReadingResult finish(String token) {
        return service.handle(PLAYER, CONNECTION, finished(token)).getResult();
    }

    private void waitMinimum() { clock.addAndGet(ScrollContent.QI_NOTES.minimumPlaybackMs()); }

    @Test
    public void eachScrollBindsItsOwnContentTimerAndIndependentBodyFlag() throws Exception {
        // 先读两卷新内容再读札记，顺序不能让一卷代替另一卷解锁。
        for (ScrollKind kind : java.util.List.of(ScrollKind.GATHERING, ScrollKind.MIDDLE_JIAO, ScrollKind.QI_NOTES)) {
            ScrollContent content = ScrollContent.of(kind);
            var open = service.open(PLAYER, CONNECTION, kind);
            assertEquals(kind, open.getScroll());
            assertEquals(content.revision(), open.getContentRevision());
            var playback = new ScrollPlayback(content);
            playback.advance(content.minimumPlaybackMs() - 1, true);
            assertFalse(playback.finished());
            clock.addAndGet(content.minimumPlaybackMs() - 1);
            assertEquals(ReadingResult.TOO_EARLY, finish(open.getSessionId()));
            open = service.open(PLAYER, CONNECTION, kind);
            clock.addAndGet(content.minimumPlaybackMs());
            playback.advance(1, true);
            assertTrue(playback.finished());
            var outcome = service.handle(PLAYER, CONNECTION, finished(open.getSessionId()));
            assertEquals(kind, outcome.getScroll());
            assertEquals(ReadingResult.UNLOCKED, outcome.getResult());
            PlayerProgress progress = PlayerProgress.parseFrom(body.saved.get(PLAYER));
            assertEquals(kind == ScrollKind.QI_NOTES, progress.getQiNotesRead());
            assertTrue(progress.getGatheringScrollRead());
            assertEquals(kind != ScrollKind.GATHERING, progress.getMiddleJiaoScrollRead());
        }
        assertEquals("8053e3137b1a399d977b9a8dfb7a85cdde9a1c4125288bfef75e39b1dff2b362", ScrollContent.QI_NOTES.revision());
        assertEquals(94325, ScrollContent.QI_NOTES.minimumPlaybackMs());
        var reset = service.setUnlocked(PLAYER, false);
        assertFalse(reset.getQiNotesRead() || reset.getGatheringScrollRead() || reset.getMiddleJiaoScrollRead());
    }

    @Test
    public void forgedAndUnopenedCompletionNeverUnlocks() {
        waitMinimum();
        assertEquals(ReadingResult.INVALID_SESSION, finish(UUID.randomUUID().toString()));
        assertFalse(service.progress(PLAYER).getQiNotesRead());
        assertEquals(0, body.writes);
    }

    @Test
    public void prematureCompletionConsumesTokenAndCannotBeReplayed() {
        var open = service.open(PLAYER, CONNECTION);
        clock.addAndGet(ScrollContent.QI_NOTES.minimumPlaybackMs() - 1);
        assertEquals(ReadingResult.TOO_EARLY, finish(open.getSessionId()));
        clock.incrementAndGet();
        assertEquals(ReadingResult.INVALID_SESSION, finish(open.getSessionId()));
        assertFalse(service.progress(PLAYER).getQiNotesRead());
    }

    @Test
    public void acceleratedPlaybackUnlocksExactlyAtServerBoundary() {
        var open = service.open(PLAYER, CONNECTION);
        waitMinimum();
        assertEquals(ReadingResult.UNLOCKED, finish(open.getSessionId()));
        assertTrue(service.progress(PLAYER).getQiNotesRead());
        assertEquals(1, body.writes);
    }

    @Test
    public void cancelNeverCountsAndReopeningStartsANewTimer() {
        var first = service.open(PLAYER, CONNECTION);
        waitMinimum();
        assertEquals(ReadingResult.CANCELLED, service.handle(PLAYER, CONNECTION,
                ReadingIntent.newBuilder().setSessionId(first.getSessionId())
                        .setCancelled(Cancelled.getDefaultInstance()).build()).getResult());
        assertEquals(ReadingResult.INVALID_SESSION, finish(first.getSessionId()));
        var second = service.open(PLAYER, CONNECTION);
        assertEquals(ReadingResult.TOO_EARLY, finish(second.getSessionId()));
        assertEquals(0, body.writes);
    }

    @Test
    public void openingAgainInvalidatesOnlyTheOldToken() {
        var old = service.open(PLAYER, CONNECTION);
        waitMinimum();
        var current = service.open(PLAYER, CONNECTION);
        assertEquals(ReadingResult.INVALID_SESSION, finish(old.getSessionId()));
        waitMinimum();
        assertEquals(ReadingResult.UNLOCKED, finish(current.getSessionId()));
    }

    @Test
    public void playerAndConnectionIdentityCannotBeSubstituted() {
        var open = service.open(PLAYER, CONNECTION);
        waitMinimum();
        assertEquals(ReadingResult.INVALID_SESSION, service.handle(UUID.randomUUID(), CONNECTION,
                finished(open.getSessionId())).getResult());
        assertEquals(ReadingResult.INVALID_SESSION, service.handle(PLAYER, "other-connection",
                finished(open.getSessionId())).getResult());
        assertEquals(ReadingResult.UNLOCKED, finish(open.getSessionId()));
    }

    @Test
    public void disconnectRevokesTokenButLateOldDisconnectDoesNotRevokeNewConnection() {
        var old = service.open(PLAYER, CONNECTION);
        service.forget(PLAYER, CONNECTION);
        waitMinimum();
        assertEquals(ReadingResult.INVALID_SESSION, finish(old.getSessionId()));
        var current = service.open(PLAYER, "reconnected");
        service.forget(PLAYER, CONNECTION);
        waitMinimum();
        assertEquals(ReadingResult.UNLOCKED, service.handle(PLAYER, "reconnected",
                finished(current.getSessionId())).getResult());
    }

    @Test
    public void repeatReadingDoesNotSaveOrUnlockAgain() {
        var first = service.open(PLAYER, CONNECTION);
        waitMinimum();
        assertEquals(ReadingResult.UNLOCKED, finish(first.getSessionId()));
        var second = service.open(PLAYER, CONNECTION);
        waitMinimum();
        assertEquals(ReadingResult.ALREADY_UNLOCKED, finish(second.getSessionId()));
        assertEquals(1, body.writes);
    }

    @Test
    public void savedBodySurvivesServiceRecreationWithoutPossessingScroll() {
        service.setUnlocked(PLAYER, true);
        var afterReload = connectedService();
        assertTrue(afterReload.progress(PLAYER).getQiNotesRead());
        assertFalse(afterReload.progress(UUID.randomUUID()).getQiNotesRead());
    }

    @Test
    public void opResetRevokesExistingReadingAndCanUnlockWithoutWaiting() {
        var old = service.open(PLAYER, CONNECTION);
        assertTrue(service.setUnlocked(PLAYER, true).getQiNotesRead());
        assertFalse(service.setUnlocked(PLAYER, false).getQiNotesRead());
        waitMinimum();
        assertEquals(ReadingResult.INVALID_SESSION, finish(old.getSessionId()));
        assertFalse(service.progress(PLAYER).getQiNotesRead());
        var current = service.open(PLAYER, CONNECTION);
        waitMinimum();
        assertEquals(ReadingResult.UNLOCKED, finish(current.getSessionId()));
    }

    @Test
    public void protobufRoundTripsRealOpenFinishAndBodyState() throws Exception {
        var open = service.open(PLAYER, CONNECTION);
        var event = ProgressionEvent.newBuilder().setOpen(open).build();
        assertEquals(event, ProgressionEvent.parseFrom(event.toByteArray()));
        var intent = finished(open.getSessionId());
        waitMinimum();
        var outcome = service.handle(PLAYER, CONNECTION, ReadingIntent.parseFrom(intent.toByteArray()));
        assertEquals(ReadingResult.UNLOCKED, ReadingOutcome.parseFrom(outcome.toByteArray()).getResult());
        assertEquals(service.progress(PLAYER), PlayerProgress.parseFrom(body.saved.get(PLAYER)));
    }

    @Test
    public void fixedLayoutPreservesMarkersAndFastestTimelineMatchesServerMinimum() {
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
            saved.put(player, progress.toByteArray());
            writes++;
        }
    }
}
