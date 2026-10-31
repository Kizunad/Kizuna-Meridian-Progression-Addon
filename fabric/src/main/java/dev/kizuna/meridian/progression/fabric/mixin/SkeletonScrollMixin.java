package dev.kizuna.meridian.progression.fabric.mixin;

import dev.kizuna.meridian.progression.fabric.ScrollAcquisition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSkeleton.class)
public abstract class SkeletonScrollMixin {
    @Inject(method = "finalizeSpawn", at = @At("RETURN"))
    private void progression$equipScroll(ServerLevelAccessor world, DifficultyInstance difficulty,
            MobSpawnType reason, SpawnGroupData data, CompoundTag nbt, CallbackInfoReturnable<SpawnGroupData> callback) {
        // 原版装备生成完毕后再赋予副手，保留原版主手弓与其他装备。
        ScrollAcquisition.equipSkeleton((AbstractSkeleton) (Object) this);
    }
}
