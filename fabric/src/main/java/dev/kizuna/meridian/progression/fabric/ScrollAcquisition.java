package dev.kizuna.meridian.progression.fabric;

import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.util.Identifier;
import java.util.Set;

/** 首轮占位概率；箱子每表独立抽一次，骷髅只在原版生成初始化时抽一次。 */
public final class ScrollAcquisition {
    public static final float CHEST_SCROLL_CHANCE = 0.12f;
    public static final float SKELETON_SCROLL_CHANCE = 0.03f;
    public static final float GUARANTEED_DROP_CHANCE = 2.0f;
    public static final float GATHERING_CHEST_CHANCE = 0.12f;
    public static final float MIDDLE_JIAO_CHEST_CHANCE = 0.12f;
    public static final Set<Identifier> GATHERING_LOOT_TABLES = Set.of(
            new Identifier("minecraft", "chests/simple_dungeon"),
            new Identifier("minecraft", "chests/abandoned_mineshaft"));
    public static final Set<Identifier> MIDDLE_JIAO_LOOT_TABLES = Set.of(
            new Identifier("minecraft", "chests/desert_pyramid"),
            new Identifier("minecraft", "chests/jungle_temple"));
    public static final Set<Identifier> LOOT_TABLES = Set.of(
            new Identifier("minecraft", "chests/simple_dungeon"),
            new Identifier("minecraft", "chests/abandoned_mineshaft"),
            new Identifier("minecraft", "chests/desert_pyramid"),
            new Identifier("minecraft", "chests/jungle_temple"));

    public static void registerLoot() {
        LootTableEvents.MODIFY.register((resources, manager, id, table, source) -> {
            // 尊重数据包替换，只向原版内建目标表追加独立池。
            if (source.isBuiltin() && LOOT_TABLES.contains(id)) {
                table.pool(LootPool.builder().conditionally(RandomChanceLootCondition.builder(CHEST_SCROLL_CHANCE))
                        .with(ItemEntry.builder(ProgressionItems.SCROLL)));
            }
            // 新卷获取点和概率均为命名占位，独立池不改变原札记抽取。
            if (source.isBuiltin() && GATHERING_LOOT_TABLES.contains(id)) {
                table.pool(LootPool.builder().conditionally(RandomChanceLootCondition.builder(GATHERING_CHEST_CHANCE))
                        .with(ItemEntry.builder(ProgressionItems.GATHERING_SCROLL)));
            }
            if (source.isBuiltin() && MIDDLE_JIAO_LOOT_TABLES.contains(id)) {
                table.pool(LootPool.builder().conditionally(RandomChanceLootCondition.builder(MIDDLE_JIAO_CHEST_CHANCE))
                        .with(ItemEntry.builder(ProgressionItems.MIDDLE_JIAO_SCROLL)));
            }
        });
    }

    public static void equipSkeleton(AbstractSkeletonEntity skeleton) {
        // 不覆盖已有副手；限定普通骷髅，重新读档不经过 initialize，不能反复抽取。
        if (skeleton.getType() == EntityType.SKELETON
                && skeleton.getEquippedStack(EquipmentSlot.OFFHAND).isEmpty()
                && skeleton.getRandom().nextFloat() < SKELETON_SCROLL_CHANCE) {
            skeleton.equipStack(EquipmentSlot.OFFHAND, new ItemStack(ProgressionItems.SCROLL));
            skeleton.setEquipmentDropChance(EquipmentSlot.OFFHAND, GUARANTEED_DROP_CHANCE);
        }
    }

    private ScrollAcquisition() {}
}
