package dev.kizuna.meridian.progression.fabric;

import dev.kizuna.backpack3d.item.ItemMetadata;
import dev.kizuna.meridian.items.ItemBinding;
import dev.kizuna.meridian.items.ItemEffect;
import dev.kizuna.meridian.items.ItemRegistry;
import dev.kizuna.meridian.progression.pb.ProgressionMessages.ScrollKind;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/** 具体内容归本附属；Items 只登记类型/触发，BS3D 只登记通用元数据。 */
public final class ProgressionItems {
    public static final Identifier SCROLL_ID = Identifier.fromNamespaceAndPath("meridian_progression", "fragment_scroll");
    public static final Identifier INNER_VISION_SCROLL_ID =
            Identifier.fromNamespaceAndPath("meridian_progression", "inner_vision_scroll");
    public static final Identifier GROUP_ID = Identifier.fromNamespaceAndPath("meridian_progression", "progression");
    public static final Identifier GATHERING_SCROLL_ID = Identifier.fromNamespaceAndPath("meridian_progression", "gathering_scroll");
    public static final Identifier MIDDLE_JIAO_SCROLL_ID = Identifier.fromNamespaceAndPath("meridian_progression", "middle_jiao_scroll");
    public static final Identifier READ = Identifier.fromNamespaceAndPath("meridian_progression", "read");
    private static final Identifier EFFECT = Identifier.fromNamespaceAndPath("meridian_progression", "open_scroll");
    public static final Item SCROLL = new ScrollItem(SCROLL_ID, ScrollKind.QI_NOTES);
    public static final Item INNER_VISION_SCROLL = new ScrollItem(INNER_VISION_SCROLL_ID, ScrollKind.INNER_VISION);
    public static final Item GATHERING_SCROLL = new ScrollItem(GATHERING_SCROLL_ID, ScrollKind.GATHERING);
    public static final Item MIDDLE_JIAO_SCROLL = new ScrollItem(MIDDLE_JIAO_SCROLL_ID, ScrollKind.MIDDLE_JIAO);
    private static ItemRegistry<ServerPlayer> catalog;

    public static void register() {
        // 四件实际物品分别登记，同一只读效果由可信物品实例决定卷种。
        var bindings = new ArrayList<ItemBinding>();
        for (var entry : Map.of(SCROLL_ID, SCROLL, GATHERING_SCROLL_ID, GATHERING_SCROLL,
                MIDDLE_JIAO_SCROLL_ID, MIDDLE_JIAO_SCROLL, INNER_VISION_SCROLL_ID, INNER_VISION_SCROLL).entrySet()) {
            Registry.register(BuiltInRegistries.ITEM, entry.getKey(), entry.getValue());
            ItemMetadata.register(entry.getValue(), "item.meridian_progression."
                    + entry.getKey().getPath() + ".description", Map.of());
            bindings.add(new ItemBinding(Identifier.fromNamespaceAndPath("meridian_progression", entry.getKey().getPath() + "_read"),
                    entry.getKey(), READ, EFFECT, 0));
        }
        // 阅读是获准的只读宿主触发；登记效果不产生事务内副作用。
        ItemEffect<ServerPlayer> readOnly = (context, transaction) -> {};
        List<ItemRegistry.Contribution<ServerPlayer>> contributions = List.of(new ItemRegistry.Contribution<>(
                "meridian_progression", List.copyOf(bindings), Map.of(EFFECT, readOnly)));
        // 26.2 单栈上限等物品默认组件在服务器数据加载后才绑定，目录校验要读它们，故每次服务器启动时加载。
        ServerLifecycleEvents.SERVER_STARTING.register(server -> catalog = ItemRegistry.load("qi-notes-v1", contributions));
        registerGroup();
    }

    private static void registerGroup() {
        // 修炼卷独占一页，按入口到具体修炼能力的顺序展示。
        CreativeModeTab group = FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.meridian_progression.progression"))
                .icon(() -> new ItemStack(SCROLL))
                .displayItems((context, entries) -> {
                    entries.accept(SCROLL);
                    entries.accept(INNER_VISION_SCROLL);
                    entries.accept(GATHERING_SCROLL);
                    entries.accept(MIDDLE_JIAO_SCROLL);
                })
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, GROUP_ID, group);
    }

    private static final class ScrollItem extends Item {
        private final ScrollKind scroll;

        private ScrollItem(Identifier id, ScrollKind scroll) {
            // 26.2 物品属性须在构造时带上注册键。
            super(new Item.Properties().stacksTo(1).setId(ResourceKey.create(Registries.ITEM, id)));
            // 卷种固定在服务端注册物品上，不从玩家提供的 NBT 读取。
            this.scroll = scroll;
        }

        @Override
        public InteractionResult use(Level world, Player player, InteractionHand hand) {
            var stack = player.getItemInHand(hand);
            if (!world.isClientSide()) {
                // 只允许真实右键经过已验证的零消耗登记启动，客户端没有打开命令。
                var binding = catalog.find(BuiltInRegistries.ITEM.getKey(stack.getItem()), READ).orElseThrow();
                if (binding.consumeCount() != 0 || !binding.effect().equals(EFFECT)) {
                    throw new IllegalStateException("残卷只读登记不一致");
                }
                ProgressionFabric.open((ServerPlayer) player, scroll);
                // 对应 1.20.1 的 sidedSuccess：服务端只确认这次使用，不再挥手。
                return InteractionResult.CONSUME;
            }
            return InteractionResult.SUCCESS;
        }
    }

    private ProgressionItems() {}
}
