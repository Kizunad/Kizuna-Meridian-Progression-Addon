package dev.kizuna.meridian.progression.fabric;

import static dev.kizuna.meridian.progression.fabric.ProgressionGameTestTicks.DEFAULT_TIMEOUT;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.Set;

/** 真实注册与战利品回归；阅读和持久化另由完整客户端闭环验证。 */
public final class ProgressionGameTest {
    private static final int LOOT_SEED_COUNT = 200;
    private static final int SKELETON_ATTEMPTS = 2_000;
    @GameTest(maxTicks = DEFAULT_TIMEOUT)
    public void registeredScrollAppearsInLootAndPreservesExistingOffhand(GameTestHelper context) {
        // 使用游戏实际注册表和已加载战利品表，不以独立夹具模拟注册成功。
        context.assertTrue(BuiltInRegistries.ITEM.getValue(ProgressionItems.SCROLL_ID) == ProgressionItems.SCROLL,
                "残卷未登记到游戏");
        context.assertTrue(ProgressionItems.SCROLL.getDefaultMaxStackSize() == 1, "残卷堆叠数错误");
        verifyLoot(context, ProgressionItems.SCROLL, ScrollAcquisition.LOOT_TABLES);
        // 每卷独立注册、独立池，原札记的掉落仍由上方回归保护。
        for (var entry : Map.of(ProgressionItems.GATHERING_SCROLL, ScrollAcquisition.GATHERING_LOOT_TABLES,
                ProgressionItems.MIDDLE_JIAO_SCROLL, ScrollAcquisition.MIDDLE_JIAO_LOOT_TABLES,
                ProgressionItems.INNER_VISION_SCROLL, ScrollAcquisition.LOOT_TABLES).entrySet()) {
            context.assertTrue(entry.getKey().getDefaultMaxStackSize() == 1, "新残卷堆叠数错误");
            verifyLoot(context, entry.getKey(), entry.getValue());
        }
        verifySkeleton(context);
        context.succeed();
    }

    private static void verifyLoot(GameTestHelper context, Item scroll, Set<Identifier> tables) {
        // 从真正加载的箱子表生成掉落，固定种子范围确保用例可重现。
        var world = context.getLevel();
        var parameters = new LootParams.Builder(world)
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .create(LootContextParamSets.CHEST);
        for (var id : tables) {
            var table = world.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
            boolean found = false;
            for (long seed = 1; seed <= LOOT_SEED_COUNT; seed++) {
                found |= table.getRandomItems(parameters, seed).stream().anyMatch(stack -> stack.is(scroll));
            }
            context.assertTrue(found, "目标战利品没有残卷：" + id + " / " + BuiltInRegistries.ITEM.getKey(scroll));
        }
    }

    private static void verifySkeleton(GameTestHelper context) {
        // 普通骷髅生成路径可以携带残卷，已有副手不能被覆盖。
        // 只构造实例、不加入世界；26.2 构造须给生成原因，这里等同指令召唤。
        var skeleton = EntityTypes.SKELETON.create(context.getLevel(), EntitySpawnReason.COMMAND);
        skeleton.getRandom().setSeed(1);
        boolean equipped = false;
        boolean innerVisionEquipped = false;
        for (int seed = 1; seed <= SKELETON_ATTEMPTS; seed++) {
            skeleton.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            ScrollAcquisition.equipSkeleton(skeleton);
            var carried = skeleton.getOffhandItem();
            equipped |= carried.is(ProgressionItems.SCROLL);
            innerVisionEquipped |= carried.is(ProgressionItems.INNER_VISION_SCROLL);
        }
        context.assertTrue(equipped, "普通骷髅未获得残卷");
        context.assertTrue(innerVisionEquipped, "普通骷髅未获得内观残卷");
        skeleton.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        ScrollAcquisition.equipSkeleton(skeleton);
        context.assertTrue(skeleton.getOffhandItem().is(Items.SHIELD), "已有副手被覆盖");
    }
}
