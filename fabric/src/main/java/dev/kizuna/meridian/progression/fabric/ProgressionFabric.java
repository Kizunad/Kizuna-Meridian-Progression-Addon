package dev.kizuna.meridian.progression.fabric;

import com.google.protobuf.Message;
import dev.kizuna.inventoryui.server.ServerModuleRegistry;
import dev.kizuna.inventoryui.server.ServerModuleSession;
import dev.kizuna.inventoryui.server.ServerConnection;
import dev.kizuna.inventoryui.server.ServerRequest;
import dev.kizuna.inventoryui.server.fabric.FabricServerModule;
import dev.kizuna.inventoryui.protocol.pb.WireProtocol.Capability;
import dev.kizuna.inventoryui.protocol.pb.WireProtocol.Direction;
import dev.kizuna.meridian.fabric.MeridianCommands;
import dev.kizuna.meridian.progression.ProgressionProtocol;
import dev.kizuna.meridian.progression.pb.ProgressionMessages.ReadingIntent;
import dev.kizuna.meridian.progression.pb.ProgressionMessages.ReadingOutcome;
import dev.kizuna.meridian.progression.pb.ProgressionMessages.ReadingResult;
import dev.kizuna.meridian.progression.pb.ProgressionMessages.ProgressionEvent;
import dev.kizuna.meridian.progression.pb.ProgressionMessages.ScrollKind;
import dev.kizuna.meridian.progression.server.ProgressionStore;
import dev.kizuna.meridian.progression.server.ReadingService;
import dev.kizuna.meridian.progression.server.ReadingService.ProgressChange;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 物品和协议的服务端宿主；身体保存与修炼拦截由协作清单中的必需接线完成。 */
public final class ProgressionFabric implements ModInitializer, FabricServerModule {
    private static final Map<MinecraftServer, Runtime> RUNTIMES = new IdentityHashMap<>();
    private static final int OP_PERMISSION_LEVEL = 2;

    private enum ProgressTarget { ALL, INNER_VISION }

    @Override
    public void onInitialize() {
        // 先注册真实内容，再把命令与连接生命周期接到同一权威阅读服务。
        ProgressionItems.register();
        ScrollAcquisition.registerLoot();
        ServerLifecycleEvents.SERVER_STOPPED.register(RUNTIMES::remove);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var runtime = RUNTIMES.get(server);
            if (runtime != null) {
                List.copyOf(runtime.sessions).forEach(ReadingSession::tick);
            }
        });
        // /meridian progression unlock|reset [<targets>] [inner_vision] 登记在 Framework 的注册点，整条要求权限 2。
        MeridianCommands.register(MeridianCommands.Branch.PROGRESSION, "unlock", OP_PERMISSION_LEVEL,
                (node, context) -> command(node, ProgressChange.UNLOCK));
        MeridianCommands.register(MeridianCommands.Branch.PROGRESSION, "reset", OP_PERMISSION_LEVEL,
                (node, context) -> command(node, ProgressChange.RESET));
    }

    private static void command(LiteralArgumentBuilder<CommandSourceStack> node, ProgressChange change) {
        // 省略目标时作用于执行玩家；整组与单卷共用可选目标。inner_vision 是字面量，
        // 精确匹配时优先于玩家名，单卷子命令不会被误当成玩家名。
        MeridianCommands.optionalPlayers(node, (parent, targets) -> parent
                .executes(context -> change(context, targets.resolve(context), change, ProgressTarget.ALL))
                .then(Commands.literal("inner_vision")
                        .executes(context -> change(context, targets.resolve(context), change,
                                ProgressTarget.INNER_VISION))));
    }

    private static int change(CommandContext<CommandSourceStack> context,
            Collection<ServerPlayer> players, ProgressChange change, ProgressTarget target) {
        // 管理命令复用身体事务；只修改指定范围，不维护另一份授权状态。
        int changed = 0;
        for (var player : players) {
            try {
                // OP 不经过阅读门槛；身体宿主仍须接好，禁止调试命令写另一份状态。
                var runtime = runtime(player.level().getServer());
                if (target == ProgressTarget.INNER_VISION) {
                    runtime.service.changeScroll(player.getUUID(), ScrollKind.INNER_VISION, change);
                } else {
                    runtime.service.changeAll(player.getUUID(), change);
                }
                for (var session : List.copyOf(runtime.sessions)) {
                    if (session.belongsTo(player)) {
                        session.invalidate();
                        session.synchronize();
                    }
                }
                changed++;
            } catch (IllegalStateException error) {
                context.getSource().sendFailure(Component.literal(error.getMessage()));
            }
        }
        int count = changed;
        String action = change == ProgressChange.UNLOCK ? "解锁" : "重置";
        String scope = target == ProgressTarget.ALL ? "全部残卷" : "内观残卷";
        context.getSource().sendSuccess(() -> Component.literal("已" + action
                + scope + "进度：" + count + " 名玩家"), true /* broadcastToOps */);
        return changed;
    }

    @Override
    public void register(MinecraftServer server, ServerModuleRegistry<ServerPlayer> registry) {
        var runtime = runtime(server);
        registry.register(ProgressionProtocol.MODULE, Map.of(
                ProgressionProtocol.INTENT, capability(ReadingIntent.getDefaultInstance(), Direction.CLIENT_TO_SERVER),
                ProgressionProtocol.EVENT, capability(ProgressionEvent.getDefaultInstance(), Direction.SERVER_TO_CLIENT)),
                connection -> {
                    // 工厂发生在握手结束前；此处不能读取玩家或发送事件。
                    var session = new ReadingSession(runtime, connection);
                    runtime.sessions.add(session);
                    return session;
                });
    }

    private static Capability capability(Message type, Direction direction) {
        return Capability.newBuilder().setModule(ProgressionProtocol.MODULE)
                .setVersion(ProgressionProtocol.VERSION).setRequired(true)
                .setPayloadType(type.getDescriptorForType().getFullName()).setDirection(direction).build();
    }

    private static Runtime runtime(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("进度操作必须在服务器主线程执行");
        }
        return RUNTIMES.computeIfAbsent(server, ignored -> new Runtime());
    }

    /** Human 在 SERVER_STARTING 的主线程安装身体进度适配器；可早于模块登记。 */
    public static void installStore(MinecraftServer server, ProgressionStore store) {
        runtime(server).service.installStore(store);
    }

    /** 身体加载、重生或更改后由 Human 调用；未就绪连接在握手后主动请求同步。 */
    public static void publishProgress(ServerPlayer player) {
        for (var session : List.copyOf(runtime(player.level().getServer()).sessions)) {
            if (session.belongsTo(player)) {
                session.synchronize();
            }
        }
    }

    /** 供人体主动修炼入口校验；OP 原有调试入口无需调用。 */
    public static boolean canCultivate(ServerPlayer player) {
        return runtime(player.level().getServer()).service.progress(player.getUUID()).getQiNotesRead();
    }

    public static boolean canInspect(ServerPlayer player) {
        // 修炼页与内观卷同时授权，和身体订阅及客户端展示采用同一语义。
        var progress = runtime(player.level().getServer()).service.progress(player.getUUID());
        return progress.getQiNotesRead() && progress.getInnerVisionScrollRead();
    }

    static void open(ServerPlayer player, ScrollKind scroll) {
        // 卷种只由已注册物品的真实右键传入，连接就绪后创建本卷会话。
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        var session = runtime(player.level().getServer()).sessions.stream()
                .filter(candidate -> candidate.belongsTo(player)).findFirst();
        if (session.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.meridian_progression.wait_connection"));
            return;
        }
        session.get().open(player, scroll);
    }

    private static final class Runtime {
        final ReadingService service = new ReadingService();
        final Set<ReadingSession> sessions = new HashSet<>();
    }

    private static final class ReadingSession implements ServerModuleSession<ServerPlayer> {
        private final Runtime runtime;
        private final ServerConnection<ServerPlayer> connection;
        private UUID playerId;
        private ServerPlayer readingPlayer;
        private String token;

        ReadingSession(Runtime runtime, ServerConnection<ServerPlayer> connection) {
            this.runtime = runtime;
            this.connection = connection;
        }

        boolean belongsTo(ServerPlayer player) {
            return connection.supports(ProgressionProtocol.EVENT) && connection.context() == player;
        }

        void open(ServerPlayer player, ScrollKind scroll) {
            // 真实右键先写身体并同步权限，阅读画面及后续关闭不影响解锁事实。
            invalidate();
            playerId = player.getUUID();
            readingPlayer = player;
            var open = runtime.service.open(playerId, connection.id(), scroll);
            token = open.message().getSessionId();
            synchronize();
            send(ProgressionEvent.newBuilder().setOpen(open.message()).build());
            if (open.result() == ReadingResult.UNLOCKED) {
                String message = switch (scroll) {
                    case QI_NOTES -> "unlocked";
                    case GATHERING -> "gathering_unlocked";
                    case MIDDLE_JIAO -> "middle_jiao_unlocked";
                    case INNER_VISION -> "inner_vision_unlocked";
                    default -> throw new IllegalStateException("解锁结果缺少残卷类型");
                };
                player.sendSystemMessage(Component.translatable("message.meridian_progression." + message),
                        scroll == ScrollKind.INNER_VISION /* overlay */);
            }
        }

        @Override
        public boolean handle(ServerRequest<ServerPlayer> request) {
            // 客户端只能同步投影或结束当前播放，不能通过 finished 授予权限。
            var player = connection.context();
            playerId = player.getUUID();
            var intent = request.payload(ReadingIntent.getDefaultInstance());
            if (intent.hasSynchronize()) {
                synchronize();
                return true;
            }
            // 重生后的实体不能完成前一具身体的阅读，死亡/旁观时也不能提交。
            if (readingPlayer != player || !player.isAlive() || player.isSpectator()) {
                invalidate();
                outcome(intent.getSessionId(), ReadingResult.NOT_ALLOWED);
                return false;
            }
            var result = runtime.service.handle(playerId, connection.id(), intent);
            if (Objects.equals(token, intent.getSessionId())) {
                token = null;
                readingPlayer = null;
            }
            send(ProgressionEvent.newBuilder().setOutcome(result).build());
            return result.getResult() == ReadingResult.COMPLETED || result.getResult() == ReadingResult.CANCELLED;
        }

        void synchronize() {
            // 身体宿主为强制接线；缺失时由 service 明确抛出集成错误。
            send(ProgressionEvent.newBuilder().setProgress(
                    runtime.service.progress(connection.context().getUUID())).build());
        }

        void tick() {
            if (readingPlayer != null && (!belongsTo(readingPlayer)
                    || !readingPlayer.isAlive() || readingPlayer.isSpectator())) {
                invalidate();
            }
        }

        void invalidate() {
            if (playerId != null) {
                runtime.service.forget(playerId, connection.id());
            }
            if (token != null && connection.supports(ProgressionProtocol.EVENT)) {
                outcome(token, ReadingResult.CANCELLED);
            }
            token = null;
            readingPlayer = null;
        }

        private void outcome(String id, ReadingResult result) {
            send(ProgressionEvent.newBuilder().setOutcome(ReadingOutcome.newBuilder()
                    .setSessionId(id).setResult(result)).build());
        }

        private void send(ProgressionEvent event) { connection.send(ProgressionProtocol.EVENT, event); }

        @Override
        public void close() {
            // close 不发包，不再访问已失效连接的玩家上下文。
            if (playerId != null) {
                runtime.service.forget(playerId, connection.id());
            }
            runtime.sessions.remove(this);
        }
    }
}
