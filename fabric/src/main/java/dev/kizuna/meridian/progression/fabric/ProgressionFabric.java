package dev.kizuna.meridian.progression.fabric;

import com.google.protobuf.Message;
import dev.kizuna.inventoryui.server.*;
import dev.kizuna.inventoryui.server.fabric.FabricServerModule;
import dev.kizuna.inventoryui.protocol.pb.WireProtocol.Capability;
import dev.kizuna.inventoryui.protocol.pb.WireProtocol.Direction;
import dev.kizuna.meridian.progression.ProgressionProtocol;
import dev.kizuna.meridian.progression.pb.ProgressionMessages.*;
import dev.kizuna.meridian.progression.server.ProgressionStore;
import dev.kizuna.meridian.progression.server.ReadingService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.*;

/** 物品和协议的服务端宿主；身体保存与修炼拦截由协作清单中的必需接线完成。 */
public final class ProgressionFabric implements ModInitializer, FabricServerModule {
    private static final Map<MinecraftServer, Runtime> RUNTIMES = new IdentityHashMap<>();

    @Override
    public void onInitialize() {
        ProgressionItems.register();
        ScrollAcquisition.registerLoot();
        ServerLifecycleEvents.SERVER_STOPPED.register(RUNTIMES::remove);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var runtime = RUNTIMES.get(server);
            if (runtime != null) {
                List.copyOf(runtime.sessions).forEach(ReadingSession::tick);
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(CommandManager.literal("meridian")
                        .then(CommandManager.literal("progression").requires(source -> source.hasPermissionLevel(2))
                                .then(command("unlock", true)).then(command("reset", false)))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> command(
            String name, boolean unlocked) {
        return CommandManager.literal(name)
                .executes(context -> change(context, List.of(context.getSource().getPlayerOrThrow()), unlocked))
                .then(CommandManager.argument("players", EntityArgumentType.players())
                        .executes(context -> change(context, EntityArgumentType.getPlayers(context, "players"), unlocked)));
    }

    private static int change(CommandContext<ServerCommandSource> context,
            Collection<ServerPlayerEntity> players, boolean unlocked) throws CommandSyntaxException {
        int changed = 0;
        for (var player : players) {
            try {
                // OP 不经过阅读门槛；身体宿主仍须接好，禁止调试命令写另一份状态。
                var runtime = runtime(player.getServer());
                runtime.service.setUnlocked(player.getUuid(), unlocked);
                for (var session : List.copyOf(runtime.sessions)) {
                    if (session.belongsTo(player)) {
                        session.invalidate();
                        session.synchronize();
                    }
                }
                changed++;
            } catch (IllegalStateException error) {
                context.getSource().sendError(Text.literal(error.getMessage()));
            }
        }
        int count = changed;
        context.getSource().sendFeedback(() -> Text.literal("已" + (unlocked ? "解锁" : "重置")
                + "残卷进度：" + count + " 名玩家"), true);
        return changed;
    }

    @Override
    public void register(MinecraftServer server, ServerModuleRegistry<ServerPlayerEntity> registry) {
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
        if (!server.isOnThread()) {
            throw new IllegalStateException("进度操作必须在服务器主线程执行");
        }
        return RUNTIMES.computeIfAbsent(server, ignored -> new Runtime());
    }

    /** Human 在 SERVER_STARTING 的主线程安装身体进度适配器；可早于模块登记。 */
    public static void installStore(MinecraftServer server, ProgressionStore store) {
        runtime(server).service.installStore(store);
    }

    /** 身体加载、重生或更改后由 Human 调用；未就绪连接在握手后主动请求同步。 */
    public static void publishProgress(ServerPlayerEntity player) {
        for (var session : List.copyOf(runtime(player.getServer()).sessions)) {
            if (session.belongsTo(player)) {
                session.synchronize();
            }
        }
    }

    /** 供人体主动修炼入口校验；OP 原有调试入口无需调用。 */
    public static boolean canCultivate(ServerPlayerEntity player) {
        return runtime(player.getServer()).service.progress(player.getUuid()).getQiNotesRead();
    }

    static void open(ServerPlayerEntity player, ScrollKind scroll) {
        // 卷种只由已注册物品的真实右键传入，连接就绪后创建本卷会话。
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        var session = runtime(player.getServer()).sessions.stream()
                .filter(candidate -> candidate.belongsTo(player)).findFirst();
        if (session.isEmpty()) {
            player.sendMessage(Text.translatable("message.meridian_progression.wait_connection"), false);
            return;
        }
        session.get().open(player, scroll);
    }

    private static final class Runtime {
        final ReadingService service = new ReadingService(Util::getMeasuringTimeMs);
        final Set<ReadingSession> sessions = new HashSet<>();
    }

    private static final class ReadingSession implements ServerModuleSession<ServerPlayerEntity> {
        private final Runtime runtime;
        private final ServerConnection<ServerPlayerEntity> connection;
        private UUID playerId;
        private ServerPlayerEntity readingPlayer;
        private String token;

        ReadingSession(Runtime runtime, ServerConnection<ServerPlayerEntity> connection) {
            this.runtime = runtime;
            this.connection = connection;
        }

        boolean belongsTo(ServerPlayerEntity player) {
            return connection.supports(ProgressionProtocol.EVENT) && connection.context() == player;
        }

        void open(ServerPlayerEntity player, ScrollKind scroll) {
            // 新卷覆盖旧凭据，不能把其他卷的已播放时间挪用到本卷。
            invalidate();
            playerId = player.getUuid();
            readingPlayer = player;
            var open = runtime.service.open(playerId, connection.id(), scroll);
            token = open.getSessionId();
            send(ProgressionEvent.newBuilder().setOpen(open).build());
        }

        @Override
        public boolean handle(ServerRequest<ServerPlayerEntity> request) {
            var player = connection.context();
            playerId = player.getUuid();
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
            if (result.getResult() == ReadingResult.UNLOCKED || result.getResult() == ReadingResult.ALREADY_UNLOCKED) {
                synchronize();
                if (result.getResult() == ReadingResult.UNLOCKED) {
                    String message = switch (result.getScroll()) {
                        case QI_NOTES -> "unlocked";
                        case GATHERING -> "gathering_unlocked";
                        case MIDDLE_JIAO -> "middle_jiao_unlocked";
                        default -> throw new IllegalStateException("解锁结果缺少残卷类型");
                    };
                    player.sendMessage(Text.translatable("message.meridian_progression." + message), false);
                }
                return true;
            }
            return result.getResult() == ReadingResult.CANCELLED;
        }

        void synchronize() {
            // 身体宿主为强制接线；缺失时由 service 明确抛出集成错误。
            send(ProgressionEvent.newBuilder().setProgress(
                    runtime.service.progress(connection.context().getUuid())).build());
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
