package dev.kizuna.meridian.progression.fabric;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
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
public final class ProgressionGameTest implements FabricGameTest {
    private static final int LOOT_SEED_COUNT = 200;
    private static final int SKELETON_ATTEMPTS = 2_000;
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void registeredScrollAppearsInLootAndPreservesExistingOffhand(GameTestHelper context) {
        // 使用游戏实际注册表和已加载战利品表，不以独立夹具模拟注册成功。
        context.assertTrue(BuiltInRegistries.ITEM.get(ProgressionItems.SCROLL_ID) == ProgressionItems.SCROLL,
                "残卷未登记到游戏");
        context.assertTrue(ProgressionItems.SCROLL.getMaxStackSize() == 1, "残卷堆叠数错误");
        verifyLoot(context, ProgressionItems.SCROLL, ScrollAcquisition.LOOT_TABLES);
        // 每卷独立注册、独立池，原札记的掉落仍由上方回归保护。
        for (var entry : Map.of(ProgressionItems.GATHERING_SCROLL, ScrollAcquisition.GATHERING_LOOT_TABLES,
                ProgressionItems.MIDDLE_JIAO_SCROLL, ScrollAcquisition.MIDDLE_JIAO_LOOT_TABLES,
                ProgressionItems.INNER_VISION_SCROLL, ScrollAcquisition.LOOT_TABLES).entrySet()) {
            context.assertTrue(entry.getKey().getMaxStackSize() == 1, "新残卷堆叠数错误");
            verifyLoot(context, entry.getKey(), entry.getValue());
        }
        verifySkeleton(context);
        context.succeed();
    }

    private static void verifyLoot(GameTestHelper context, Item scroll, Set<ResourceLocation> tables) {
        // 从真正加载的箱子表生成掉落，固定种子范围确保用例可重现。
        var world = context.getLevel();
        var parameters = new LootParams.Builder(world)
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .create(LootContextParamSets.CHEST);
        for (var id : tables) {
            var table = world.getServer().getLootData().getLootTable(id);
            boolean found = false;
            for (long seed = 1; seed <= LOOT_SEED_COUNT; seed++) {
                found |= table.getRandomItems(parameters, seed).stream().anyMatch(stack -> stack.is(scroll));
            }
            context.assertTrue(found, "目标战利品没有残卷：" + id + " / " + BuiltInRegistries.ITEM.getKey(scroll));
        }
    }

    private static void verifySkeleton(GameTestHelper context) {
        // 普通骷髅生成路径可以携带残卷，已有副手不能被覆盖。
        var skeleton = EntityType.SKELETON.create(context.getLevel());
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
