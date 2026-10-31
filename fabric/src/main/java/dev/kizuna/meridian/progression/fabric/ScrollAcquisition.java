package dev.kizuna.meridian.progression.fabric;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import java.util.Set;
import java.util.List;

/** 首轮占位概率；箱子每表独立抽一次，骷髅只在原版生成初始化时抽一次。 */
public final class ScrollAcquisition {
    public static final float CHEST_SCROLL_CHANCE = 0.12f;
    public static final float SKELETON_SCROLL_CHANCE = 0.03f;
    public static final float GUARANTEED_DROP_CHANCE = 2.0f;
    public static final float GATHERING_CHEST_CHANCE = 0.12f;
    public static final float MIDDLE_JIAO_CHEST_CHANCE = 0.12f;
    public static final float INNER_VISION_CHEST_CHANCE = 0.12f;
    private static final List<Item> SKELETON_SCROLLS = List.of(
            ProgressionItems.SCROLL, ProgressionItems.INNER_VISION_SCROLL);
    public static final Set<Identifier> GATHERING_LOOT_TABLES = Set.of(
            Identifier.fromNamespaceAndPath("minecraft", "chests/simple_dungeon"),
            Identifier.fromNamespaceAndPath("minecraft", "chests/abandoned_mineshaft"));
    public static final Set<Identifier> MIDDLE_JIAO_LOOT_TABLES = Set.of(
            Identifier.fromNamespaceAndPath("minecraft", "chests/desert_pyramid"),
            Identifier.fromNamespaceAndPath("minecraft", "chests/jungle_temple"));
    public static final Set<Identifier> LOOT_TABLES = Set.of(
            Identifier.fromNamespaceAndPath("minecraft", "chests/simple_dungeon"),
            Identifier.fromNamespaceAndPath("minecraft", "chests/abandoned_mineshaft"),
            Identifier.fromNamespaceAndPath("minecraft", "chests/desert_pyramid"),
            Identifier.fromNamespaceAndPath("minecraft", "chests/jungle_temple"));

    public static void registerLoot() {
        // 各卷各用独立池，数据包替换过的表不追加内容。
        LootTableEvents.MODIFY.register((key, table, source, registries) -> {
            // 26.2 事件按注册键传入战利品表，目标集合仍按资源 ID 比较。
            Identifier id = key.identifier();
            // 尊重数据包替换，只向原版内建目标表追加独立池。
            if (source.isBuiltin() && LOOT_TABLES.contains(id)) {
                addScrollPool(table, ProgressionItems.SCROLL, CHEST_SCROLL_CHANCE);
                addScrollPool(table, ProgressionItems.INNER_VISION_SCROLL, INNER_VISION_CHEST_CHANCE);
            }
            // 新卷获取点和概率均为命名占位，独立池不改变原札记抽取。
            if (source.isBuiltin() && GATHERING_LOOT_TABLES.contains(id)) {
                addScrollPool(table, ProgressionItems.GATHERING_SCROLL, GATHERING_CHEST_CHANCE);
            }
            if (source.isBuiltin() && MIDDLE_JIAO_LOOT_TABLES.contains(id)) {
                addScrollPool(table, ProgressionItems.MIDDLE_JIAO_SCROLL, MIDDLE_JIAO_CHEST_CHANCE);
            }
        });
    }

    private static void addScrollPool(LootTable.Builder table, Item scroll, float chance) {
        // 条件与条目分开构造，所有残卷共享同一种独立随机池结构。
        var condition = LootItemRandomChanceCondition.randomChance(chance);
        var entry = LootItem.lootTableItem(scroll);
        var pool = LootPool.lootPool()
                .when(condition)
                .add(entry);
        table.withPool(pool);
    }

    public static void equipSkeleton(AbstractSkeleton skeleton) {
        // 不覆盖已有副手；限定普通骷髅，重新读档不经过 initialize，不能反复抽取。
        if (skeleton.getType() == EntityTypes.SKELETON
                && skeleton.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()
                && skeleton.getRandom().nextFloat() < SKELETON_SCROLL_CHANCE) {
            Item scroll = SKELETON_SCROLLS.get(skeleton.getRandom().nextInt(SKELETON_SCROLLS.size()));
            skeleton.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(scroll));
            skeleton.setDropChance(EquipmentSlot.OFFHAND, GUARANTEED_DROP_CHANCE);
        }
    }

    private ScrollAcquisition() {}
}
