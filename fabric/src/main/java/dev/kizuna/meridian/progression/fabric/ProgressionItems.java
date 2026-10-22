package dev.kizuna.meridian.progression.fabric;

import dev.kizuna.backpack3d.item.ItemMetadata;
import dev.kizuna.meridian.items.ItemBinding;
import dev.kizuna.meridian.items.ItemEffect;
import dev.kizuna.meridian.items.ItemRegistry;
import dev.kizuna.meridian.progression.pb.ProgressionMessages.ScrollKind;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.minecraft.entity.player.PlayerEntity;
import java.util.List;
import java.util.Map;

/** 具体内容归本附属；Items 只登记类型/触发，BS3D 只登记通用元数据。 */
public final class ProgressionItems {
    public static final Identifier SCROLL_ID = new Identifier("meridian_progression", "fragment_scroll");
    public static final Identifier GATHERING_SCROLL_ID = new Identifier("meridian_progression", "gathering_scroll");
    public static final Identifier MIDDLE_JIAO_SCROLL_ID = new Identifier("meridian_progression", "middle_jiao_scroll");
    public static final Identifier READ = new Identifier("meridian_progression", "read");
    private static final Identifier EFFECT = new Identifier("meridian_progression", "open_scroll");
    public static final Item SCROLL = new ScrollItem(ScrollKind.QI_NOTES);
    public static final Item GATHERING_SCROLL = new ScrollItem(ScrollKind.GATHERING);
    public static final Item MIDDLE_JIAO_SCROLL = new ScrollItem(ScrollKind.MIDDLE_JIAO);
    private static ItemRegistry<ServerPlayerEntity> catalog;

    public static void register() {
        // 三件实际物品分别登记，同一只读效果由可信物品实例决定卷种。
        var bindings = new java.util.ArrayList<ItemBinding>();
        for (var entry : Map.of(SCROLL_ID, SCROLL, GATHERING_SCROLL_ID, GATHERING_SCROLL,
                MIDDLE_JIAO_SCROLL_ID, MIDDLE_JIAO_SCROLL).entrySet()) {
            Registry.register(Registries.ITEM, entry.getKey(), entry.getValue());
            ItemMetadata.register(entry.getValue(), "item.meridian_progression."
                    + entry.getKey().getPath() + ".description", Map.of());
            bindings.add(new ItemBinding(new Identifier("meridian_progression", entry.getKey().getPath() + "_read"),
                    entry.getKey(), READ, EFFECT, 0));
        }
        // 阅读是获准的只读宿主触发；登记效果不产生事务内副作用。
        ItemEffect<ServerPlayerEntity> readOnly = (context, transaction) -> {};
        catalog = ItemRegistry.load("qi-notes-v1", List.of(new ItemRegistry.Contribution<>(
                "meridian_progression", bindings, Map.of(EFFECT, readOnly))));
    }

    private static final class ScrollItem extends Item {
        private final ScrollKind scroll;

        private ScrollItem(ScrollKind scroll) {
            super(new Item.Settings().maxCount(1));
            // 卷种固定在服务端注册物品上，不从玩家提供的 NBT 读取。
            this.scroll = scroll;
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
            var stack = player.getStackInHand(hand);
            if (!world.isClient) {
                // 只允许真实右键经过已验证的零消耗登记启动，客户端没有打开命令。
                var binding = catalog.find(Registries.ITEM.getId(stack.getItem()), READ).orElseThrow();
                if (binding.consumeCount() != 0 || !binding.effect().equals(EFFECT)) {
                    throw new IllegalStateException("残卷只读登记不一致");
                }
                ProgressionFabric.open((ServerPlayerEntity) player, scroll);
            }
            return TypedActionResult.success(stack, world.isClient);
        }
    }

    private ProgressionItems() {}
}
