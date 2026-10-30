package dev.kizuna.meridian.progression.fabric;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

import java.util.List;

/** 直接检查游戏创造栏注册及显示顺序，不复制生产条目提供器。 */
public final class ProgressionCreativeGameTest implements FabricGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void cultivationTabContainsExactlyFourScrollsInUnlockOrder(TestContext context) {
        // 构建真实创造栏显示条目，同时核对图标和物品注册身份。
        context.assertTrue(Registries.ITEM_GROUP.containsId(ProgressionItems.GROUP_ID), "修炼创造栏未注册");
        var world = context.getWorld();
        var group = Registries.ITEM_GROUP.get(ProgressionItems.GROUP_ID);
        var display = new ItemGroup.DisplayContext(world.getEnabledFeatures(), true /* hasPermissions */,
                world.getRegistryManager());
        group.updateEntries(display);
        var expected = List.of(ProgressionItems.SCROLL, ProgressionItems.INNER_VISION_SCROLL,
                ProgressionItems.GATHERING_SCROLL, ProgressionItems.MIDDLE_JIAO_SCROLL);
        var actual = group.getDisplayStacks().stream().map(ItemStack::getItem).toList();
        context.assertTrue(actual.equals(expected), "修炼创造栏卷数或顺序不符：" + actual);
        context.assertTrue(group.getIcon().isOf(ProgressionItems.SCROLL), "修炼创造栏图标不是残卷");
        context.assertTrue(Registries.ITEM.get(ProgressionItems.INNER_VISION_SCROLL_ID)
                == ProgressionItems.INNER_VISION_SCROLL, "内观残卷不能通过注册身份获取");
        context.complete();
    }
}
