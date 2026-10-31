package dev.kizuna.meridian.progression.fabric;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** 直接检查游戏创造栏注册及显示顺序，不复制生产条目提供器。 */
public final class ProgressionCreativeGameTest {
    @GameTest
    public void cultivationTabContainsExactlyFourScrollsInUnlockOrder(GameTestHelper context) {
        // 构建真实创造栏显示条目，同时核对图标和物品注册身份。
        context.assertTrue(BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(ProgressionItems.GROUP_ID), "修炼创造栏未注册");
        var world = context.getLevel();
        var group = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(ProgressionItems.GROUP_ID);
        var display = new CreativeModeTab.ItemDisplayParameters(world.enabledFeatures(), true /* hasPermissions */,
                world.registryAccess());
        group.buildContents(display);
        var expected = List.of(ProgressionItems.SCROLL, ProgressionItems.INNER_VISION_SCROLL,
                ProgressionItems.GATHERING_SCROLL, ProgressionItems.MIDDLE_JIAO_SCROLL);
        var actual = group.getDisplayItems().stream().map(ItemStack::getItem).toList();
        context.assertTrue(actual.equals(expected), "修炼创造栏卷数或顺序不符：" + actual);
        context.assertTrue(group.getIconItem().is(ProgressionItems.SCROLL), "修炼创造栏图标不是残卷");
        context.assertTrue(BuiltInRegistries.ITEM.getValue(ProgressionItems.INNER_VISION_SCROLL_ID)
                == ProgressionItems.INNER_VISION_SCROLL, "内观残卷不能通过注册身份获取");
        context.succeed();
    }
}
