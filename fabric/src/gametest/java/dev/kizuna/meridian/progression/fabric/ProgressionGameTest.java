package dev.kizuna.meridian.progression.fabric;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.Vec3d;

/** 真实注册与战利品回归；阅读和持久化另由完整客户端闭环验证。 */
public final class ProgressionGameTest implements FabricGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void registeredScrollAppearsInLootAndPreservesExistingOffhand(TestContext context) {
        // 使用游戏实际注册表和已加载战利品表，不以独立夹具模拟注册成功。
        context.assertTrue(Registries.ITEM.get(ProgressionItems.SCROLL_ID) == ProgressionItems.SCROLL,
                "残卷未登记到游戏");
        context.assertTrue(ProgressionItems.SCROLL.getMaxCount() == 1, "残卷堆叠数错误");
        var world = context.getWorld();
        var parameters = new LootContextParameterSet.Builder(world)
                .add(LootContextParameters.ORIGIN, Vec3d.ZERO).build(LootContextTypes.CHEST);
        for (var id : ScrollAcquisition.LOOT_TABLES) {
            var table = world.getServer().getLootManager().getLootTable(id);
            boolean found = false;
            for (long seed = 1; seed <= 200; seed++) {
                found |= table.generateLoot(parameters, seed).stream().anyMatch(stack -> stack.isOf(ProgressionItems.SCROLL));
            }
            context.assertTrue(found, "目标战利品没有残卷：" + id);
        }
        // 两卷新内容独立注册、独立池，原札记的掉落仍由上方回归保护。
        for (var entry : java.util.Map.of(ProgressionItems.GATHERING_SCROLL, ScrollAcquisition.GATHERING_LOOT_TABLES,
                ProgressionItems.MIDDLE_JIAO_SCROLL, ScrollAcquisition.MIDDLE_JIAO_LOOT_TABLES).entrySet()) {
            context.assertTrue(entry.getKey().getMaxCount() == 1, "新残卷堆叠数错误");
            for (var id : entry.getValue()) {
                var table = world.getServer().getLootManager().getLootTable(id);
                boolean found = false;
                for (long seed = 1; seed <= 200; seed++) {
                    found |= table.generateLoot(parameters, seed).stream().anyMatch(stack -> stack.isOf(entry.getKey()));
                }
                context.assertTrue(found, "目标战利品没有新残卷：" + id);
            }
        }

        // 普通骷髅生成路径可以携带残卷，已有副手不能被覆盖。
        var skeleton = EntityType.SKELETON.create(world);
        skeleton.getRandom().setSeed(1);
        boolean equipped = false;
        for (int seed = 1; seed <= 200; seed++) {
            skeleton.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            ScrollAcquisition.equipSkeleton(skeleton);
            equipped |= skeleton.getOffHandStack().isOf(ProgressionItems.SCROLL);
        }
        context.assertTrue(equipped, "普通骷髅未获得残卷");
        skeleton.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        ScrollAcquisition.equipSkeleton(skeleton);
        context.assertTrue(skeleton.getOffHandStack().isOf(Items.SHIELD), "已有副手被覆盖");
        context.complete();
    }
}
