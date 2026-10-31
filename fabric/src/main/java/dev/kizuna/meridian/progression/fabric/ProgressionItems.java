package dev.kizuna.meridian.progression.fabric;

import dev.kizuna.backpack3d.item.ItemMetadata;
import dev.kizuna.meridian.items.ItemBinding;
import dev.kizuna.meridian.items.ItemEffect;
import dev.kizuna.meridian.items.ItemRegistry;
import dev.kizuna.meridian.progression.pb.ProgressionMessages.ScrollKind;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
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
    public static final ResourceLocation SCROLL_ID = new ResourceLocation("meridian_progression", "fragment_scroll");
    public static final ResourceLocation INNER_VISION_SCROLL_ID =
            new ResourceLocation("meridian_progression", "inner_vision_scroll");
    public static final ResourceLocation GROUP_ID = new ResourceLocation("meridian_progression", "progression");
    public static final ResourceLocation GATHERING_SCROLL_ID = new ResourceLocation("meridian_progression", "gathering_scroll");
    public static final ResourceLocation MIDDLE_JIAO_SCROLL_ID = new ResourceLocation("meridian_progression", "middle_jiao_scroll");
    public static final ResourceLocation READ = new ResourceLocation("meridian_progression", "read");
    private static final ResourceLocation EFFECT = new ResourceLocation("meridian_progression", "open_scroll");
    public static final Item SCROLL = new ScrollItem(ScrollKind.QI_NOTES);
    public static final Item INNER_VISION_SCROLL = new ScrollItem(ScrollKind.INNER_VISION);
    public static final Item GATHERING_SCROLL = new ScrollItem(ScrollKind.GATHERING);
    public static final Item MIDDLE_JIAO_SCROLL = new ScrollItem(ScrollKind.MIDDLE_JIAO);
    private static ItemRegistry<ServerPlayer> catalog;

    public static void register() {
        // 四件实际物品分别登记，同一只读效果由可信物品实例决定卷种。
        var bindings = new ArrayList<ItemBinding>();
        for (var entry : Map.of(SCROLL_ID, SCROLL, GATHERING_SCROLL_ID, GATHERING_SCROLL,
                MIDDLE_JIAO_SCROLL_ID, MIDDLE_JIAO_SCROLL, INNER_VISION_SCROLL_ID, INNER_VISION_SCROLL).entrySet()) {
            Registry.register(BuiltInRegistries.ITEM, entry.getKey(), entry.getValue());
            ItemMetadata.register(entry.getValue(), "item.meridian_progression."
                    + entry.getKey().getPath() + ".description", Map.of());
            bindings.add(new ItemBinding(new ResourceLocation("meridian_progression", entry.getKey().getPath() + "_read"),
                    entry.getKey(), READ, EFFECT, 0));
        }
        // 阅读是获准的只读宿主触发；登记效果不产生事务内副作用。
        ItemEffect<ServerPlayer> readOnly = (context, transaction) -> {};
        catalog = ItemRegistry.load("qi-notes-v1", List.of(new ItemRegistry.Contribution<>(
                "meridian_progression", bindings, Map.of(EFFECT, readOnly))));
        registerGroup();
    }

    private static void registerGroup() {
        // 修炼卷独占一页，按入口到具体修炼能力的顺序展示。
        CreativeModeTab group = FabricItemGroup.builder()
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

        private ScrollItem(ScrollKind scroll) {
            super(new Item.Properties().stacksTo(1));
            // 卷种固定在服务端注册物品上，不从玩家提供的 NBT 读取。
            this.scroll = scroll;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
            var stack = player.getItemInHand(hand);
            if (!world.isClientSide) {
                // 只允许真实右键经过已验证的零消耗登记启动，客户端没有打开命令。
                var binding = catalog.find(BuiltInRegistries.ITEM.getKey(stack.getItem()), READ).orElseThrow();
                if (binding.consumeCount() != 0 || !binding.effect().equals(EFFECT)) {
                    throw new IllegalStateException("残卷只读登记不一致");
                }
                ProgressionFabric.open((ServerPlayer) player, scroll);
            }
            return InteractionResultHolder.sidedSuccess(stack, world.isClientSide);
        }
    }

    private ProgressionItems() {}
}
