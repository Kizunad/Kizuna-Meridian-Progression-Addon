package dev.kizuna.meridian.progression.fabric;

import static dev.kizuna.meridian.progression.fabric.ProgressionGameTestTicks.DEFAULT_TIMEOUT;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * 「修行」进度页的根、修炼线和功法线开头：父子照设计稿，根一进世界就给，三卷各自完成，跟类人交过手才完成「道上的人」。
 * 真炁残卷挂在「炼炁成真」下面，拿到这一卷就完成，不用先破入真炁。
 * 开脉、境界那三条由 Human 的测试推进。
 */
public final class ProgressionAdvancementGameTest {
    private static final Identifier ROOT = id("root");
    private static final Identifier NOTES = id("notes");
    private static final Identifier INNER_VISION = id("inner_vision");
    private static final Identifier GATHERING = id("gathering");
    private static final Identifier MIDDLE_JIAO = id("middle_jiao");
    private static final Identifier TRUE_QI = id("true_qi");
    private static final Identifier TRUE_QI_SCROLL = id("true_qi_scroll");
    private static final Identifier CULTIVATORS = id("cultivators");
    private static final float HIT = 1.0F;
    private static final BlockPos MOB_POS = new BlockPos(1, 1, 1);

    @GameTest(maxTicks = DEFAULT_TIMEOUT)
    public void treeLoadsWithDesignedParents(GameTestHelper context) {
        // 根没有父、用石质背景、不弹提示；其余每条挂在设计稿写的父下面。
        Map<Identifier, Identifier> parents = new LinkedHashMap<>();
        parents.put(NOTES, ROOT);
        parents.put(INNER_VISION, NOTES);
        parents.put(GATHERING, NOTES);
        parents.put(MIDDLE_JIAO, NOTES);
        parents.put(id("first_meridian"), GATHERING);
        parents.put(id("eight_meridians"), id("first_meridian"));
        parents.put(TRUE_QI, id("eight_meridians"));
        parents.put(TRUE_QI_SCROLL, TRUE_QI);
        parents.put(CULTIVATORS, ROOT);
        AdvancementHolder root = advancement(context, ROOT);
        DisplayInfo rootDisplay = root.value().display().orElseThrow();
        context.assertTrue(root.value().parent().isEmpty(), "根进度有父");
        context.assertTrue(rootDisplay.getBackground().isPresent(), "根进度没有背景");
        context.assertFalse(rootDisplay.shouldShowToast(), "根进度会弹提示");
        parents.forEach((child, parent) -> context.assertTrue(
                advancement(context, child).value().parent().equals(Optional.of(parent)), child + " 的父不是 " + parent));
        context.succeed();
    }

    @GameTest(maxTicks = DEFAULT_TIMEOUT)
    public void rootCompletesOnFirstPlayerTick(GameTestHelper context) {
        // 根用原版 tick：玩家在世界里走过一 tick 就有。
        ServerPlayer player = player(context);
        context.assertFalse(done(context, player, ROOT), "玩家还没走过一 tick 就完成了根");
        context.succeedWhen(() -> {
            context.assertTrue(done(context, player, ROOT), "玩家在世界里没完成根");
            release(player);
        });
    }

    @GameTest(maxTicks = DEFAULT_TIMEOUT)
    public void eachScrollCompletesOnlyItsOwnAdvancement(GameTestHelper context) {
        // 拿到札记只完成札记，三卷都还没完成；再拿聚气卷，只多完成聚气。
        ServerPlayer player = player(context);
        try {
            give(player, ProgressionItems.SCROLL);
            context.assertTrue(done(context, player, NOTES), "拿到札记没完成炁脉札记");
            for (Identifier scroll : List.of(INNER_VISION, GATHERING, MIDDLE_JIAO)) {
                context.assertFalse(done(context, player, scroll), "拿到札记就完成了 " + scroll);
            }
            give(player, ProgressionItems.GATHERING_SCROLL);
            context.assertTrue(done(context, player, GATHERING), "拿到聚气卷没完成聚气");
            context.assertFalse(done(context, player, INNER_VISION), "拿到聚气卷完成了内观");
            context.assertFalse(done(context, player, MIDDLE_JIAO), "拿到聚气卷完成了中焦");
        } finally {
            release(player);
        }
        context.succeed();
    }

    @GameTest(maxTicks = DEFAULT_TIMEOUT)
    public void trueQiScrollCompletesOnlyItsOwnAdvancement(GameTestHelper context) {
        // 拿到中焦卷不算；拿到真炁残卷才完成它，「炼炁成真」要靠境界，不因这一卷完成。
        ServerPlayer player = player(context);
        try {
            give(player, ProgressionItems.MIDDLE_JIAO_SCROLL);
            context.assertFalse(done(context, player, TRUE_QI_SCROLL), "拿到中焦卷就完成了真炁残卷");
            give(player, ProgressionItems.TRUE_QI_SCROLL);
            context.assertTrue(done(context, player, TRUE_QI_SCROLL), "拿到真炁残卷没完成真炁残卷");
            context.assertFalse(done(context, player, TRUE_QI), "拿到真炁残卷就完成了炼炁成真");
        } finally {
            release(player);
        }
        context.succeed();
    }

    @GameTest(maxTicks = DEFAULT_TIMEOUT)
    public void hittingVillagerCompletesCultivators(GameTestHelper context) {
        // 打僵尸不算；打村民算跟道上的人交过手。
        ServerPlayer player = player(context);
        try {
            hit(context, player, context.spawn(EntityTypes.ZOMBIE, MOB_POS));
            context.assertFalse(done(context, player, CULTIVATORS), "打僵尸完成了道上的人");
            hit(context, player, context.spawn(EntityTypes.VILLAGER, MOB_POS));
            context.assertTrue(done(context, player, CULTIVATORS), "打村民没完成道上的人");
        } finally {
            release(player);
        }
        context.succeed();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("meridian_progression", "cultivation/" + path);
    }

    private static ServerPlayer player(GameTestHelper context) {
        // 生存模式的测试玩家。
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static void hit(GameTestHelper context, ServerPlayer player, LivingEntity target) {
        // 走原版受伤入口，伤害来源是这名玩家的近战；打完就清掉。
        target.hurtServer(context.getLevel(), player.damageSources().playerAttack(player), HIT);
        target.discard();
    }

    private static void give(ServerPlayer player, Item item) {
        // 和捡起来一样进背包；背包变动在菜单同步时报给进度。
        player.getInventory().add(new ItemStack(item));
        player.inventoryMenu.broadcastChanges();
    }

    private static void release(ServerPlayer player) {
        // 测试玩家下线，免得留在同一服务器的后续测试里。
        player.level().getServer().getPlayerList().remove(player);
    }

    private static AdvancementHolder advancement(GameTestHelper context, Identifier id) {
        // 进度文件必须已加载，缺了直接报错而不是当作未完成。
        AdvancementHolder advancement = context.getLevel().getServer().getAdvancements().get(id);
        if (advancement == null) {
            throw new AssertionError("进度没有加载：" + id);
        }
        return advancement;
    }

    private static boolean done(GameTestHelper context, ServerPlayer player, Identifier id) {
        return player.getAdvancements().getOrStartProgress(advancement(context, id)).isDone();
    }
}
