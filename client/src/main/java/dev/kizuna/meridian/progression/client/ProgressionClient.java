package dev.kizuna.meridian.progression.client;

import dev.kizuna.inventoryui.client.*;
import dev.kizuna.inventoryui.protocol.UiMessageRouter;
import dev.kizuna.inventoryui.protocol.UiWire;
import dev.kizuna.inventoryui.registry.FrameworkCatalog;
import dev.kizuna.meridian.progression.ProgressionProtocol;
import dev.kizuna.meridian.progression.ScrollContent;
import dev.kizuna.meridian.progression.pb.ProgressionMessages.*;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** 不登记额外通道或修炼页，只接入框架的必需消息模块和残卷过场。 */
public final class ProgressionClient implements ClientModule {
    private ClientRuntime runtime;
    private long generation;

    @Override
    public FrameworkCatalog.Module definition() {
        return FrameworkCatalog.Module.of(ProgressionProtocol.MODULE, Set.of("kiui:core"));
    }

    @Override
    public void register(ClientBindings bindings) {
        bindings.message(new UiWire.Contract<>(new UiMessageRouter.Contract<>(ProgressionProtocol.MODULE,
                ProgressionProtocol.INTENT, ProgressionProtocol.VERSION, UiMessageRouter.Direction.CLIENT_TO_SERVER,
                ReadingIntent.class, null), UiWire.Codec.protobuf(ReadingIntent.getDefaultInstance()), true));
        bindings.message(new UiWire.Contract<>(new UiMessageRouter.Contract<>(ProgressionProtocol.MODULE,
                ProgressionProtocol.EVENT, ProgressionProtocol.VERSION, UiMessageRouter.Direction.SERVER_TO_CLIENT,
                ProgressionEvent.class, this::receive), UiWire.Codec.protobuf(ProgressionEvent.getDefaultInstance()), true));
    }

    @Override
    public void protocolReady(ClientRuntime runtime) {
        this.runtime = runtime;
        generation++;
        ClientProgression.clear();
        send(ReadingIntent.newBuilder().setSynchronize(Synchronize.getDefaultInstance()).build(), generation);
    }

    @Override
    public void disconnected(ClientRuntime runtime) {
        // 先撤销代数再关闭屏幕，removed 不得把旧凭据发送到下一条连接。
        generation++;
        this.runtime = null;
        ClientProgression.clear();
        var client = Minecraft.getInstance();
        if (client.screen instanceof ScrollScreen screen) {
            screen.dismiss();
        }
    }

    private void receive(ProgressionEvent event) {
        // 解锁随右键后的 progress 发布；播放完成或取消仅负责关闭对应画面。
        var client = Minecraft.getInstance();
        switch (event.getEventCase()) {
            case OPEN -> {
                var open = event.getOpen();
                ScrollContent content = ScrollContent.of(open.getScroll());
                if (!content.revision().equals(open.getContentRevision())) {
                    send(ReadingIntent.newBuilder().setSessionId(open.getSessionId())
                            .setCancelled(Cancelled.getDefaultInstance()).build(), generation);
                    message("revision_mismatch");
                    return;
                }
                long source = generation;
                client.setScreen(new ScrollScreen(open.getSessionId(), content, intent -> send(intent, source)));
            }
            case PROGRESS -> ClientProgression.accept(event.getProgress());
            case OUTCOME -> {
                var outcome = event.getOutcome();
                if (client.screen instanceof ScrollScreen screen
                        && screen.sessionId().equals(outcome.getSessionId())) {
                    screen.dismiss();
                }
                switch (outcome.getResult()) {
                    case HOST_NOT_CONNECTED -> {
                        // 必需宿主接线错误必须暴露，不能以只读或未读状态继续运行。
                        throw new IllegalStateException("修炼进度缺少必需身体宿主");
                    }
                    case INVALID_SESSION, NOT_ALLOWED -> message("invalid_session");
                    default -> { /* 成功提示由服务端只发送一次；取消无需额外提示。 */ }
                }
            }
            case EVENT_NOT_SET -> throw new IllegalArgumentException("残卷事件为空");
        }
    }

    private void send(ReadingIntent intent, long source) {
        if (runtime == null || source != generation) {
            return;
        }
        // 路由超时使用 Inventory UI tick 的墙钟；正文播放另用客户端单调时间。
        runtime.protocol().request(ProgressionProtocol.INTENT, intent, System.currentTimeMillis())
                .thenAccept(outcome -> {
                    // RESULT 只确认路由受理，解锁以 ProgressionEvent 的权威状态为准。
                    if (!outcome.accepted() && !outcome.code().equals("REJECTED")) {
                        Minecraft.getInstance().execute(() -> {
                            if (source == generation && runtime != null) {
                                message("transport_error");
                            }
                        });
                    }
                });
    }

    private static void message(String key) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.meridian_progression." + key), false);
        }
    }
}
