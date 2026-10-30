package dev.kizuna.meridian.progression.client;

import dev.kizuna.meridian.progression.ProgressionProtocol;
import dev.kizuna.meridian.progression.pb.ProgressionMessages.PlayerProgress;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 检视必须同时拥有修炼页与内观卷，连接清理和单卷重置即时撤权。 */
public final class ClientProgressionTest {
    @After
    public void disconnect() {
        // 静态投影不能跨测试连接保留权限。
        ClientProgression.clear();
    }

    @Test
    public void inspectionRequiresBothScrollsAndStopsOnResetOrDisconnect() {
        // 独立内观卷和独立札记都不足以打开检视，其他修炼卷不替代它。
        ClientProgression.clear();
        assertFalse(ClientProgression.canInspect());
        var innerOnly = PlayerProgress.newBuilder()
                .setSchemaVersion(ProgressionProtocol.PROGRESS_SCHEMA)
                .setInnerVisionScrollRead(true /* value */)
                .build();
        ClientProgression.accept(innerOnly);
        assertFalse(ClientProgression.canInspect());
        var all = innerOnly.toBuilder()
                .setQiNotesRead(true /* value */)
                .setGatheringScrollRead(true /* value */)
                .setMiddleJiaoScrollRead(true /* value */)
                .build();
        ClientProgression.accept(all);
        assertTrue(ClientProgression.canInspect());
        ClientProgression.accept(all.toBuilder().setInnerVisionScrollRead(false /* value */).build());
        assertFalse(ClientProgression.canInspect());
        assertTrue(ClientProgression.canGather() && ClientProgression.canUseMiddleJiao());
        ClientProgression.accept(all);
        ClientProgression.clear();
        assertFalse(ClientProgression.canInspect());
    }
}
