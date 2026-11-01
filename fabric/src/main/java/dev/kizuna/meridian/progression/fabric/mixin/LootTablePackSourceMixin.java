package dev.kizuna.meridian.progression.fabric.mixin;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import dev.kizuna.meridian.progression.fabric.ScrollAcquisition;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 记下每张战利品表胜出资源的包来源，供残卷注入把原版实验包与数据包区分开。
 * 注入点与 Fabric API 0.161.0+26.2（fabric-loot-api-v3 3.0.17）记录 LootTableSource 的
 * SimpleJsonResourceReloadListenerMixin 相同；上游 LootTableSource 能区分实验包后删除本 mixin。
 */
@Mixin(SimpleJsonResourceReloadListener.class)
public abstract class LootTablePackSourceMixin {
    @Inject(method = "scanDirectory(Lnet/minecraft/server/packs/resources/ResourceManager;"
            + "Lnet/minecraft/resources/FileToIdConverter;Lcom/mojang/serialization/DynamicOps;"
            + "Lcom/mojang/serialization/Codec;Ljava/util/Map;)V",
            at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/resources/FileToIdConverter;"
                    + "fileToId(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/resources/Identifier;"))
    private static <T> void progression$recordLootTablePack(ResourceManager manager, FileToIdConverter converter,
            DynamicOps<JsonElement> ops, Codec<T> codec, Map<Identifier, T> result, CallbackInfo callback,
            @Local(name = "entry") Map.Entry<Identifier, Resource> entry, @Local(name = "id") Identifier id) {
        // 同一方法还扫描谓词、物品修饰器等目录，只记战利品表。
        if (converter.prefix().equals(Registries.LOOT_TABLE.identifier().getPath())) {
            ScrollAcquisition.recordLootTablePack(id, entry.getValue().getFabricPackSource());
        }
    }
}
