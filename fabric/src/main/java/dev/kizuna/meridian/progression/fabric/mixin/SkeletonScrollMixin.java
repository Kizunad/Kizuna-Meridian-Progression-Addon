package dev.kizuna.meridian.progression.fabric.mixin;

import dev.kizuna.meridian.progression.fabric.ScrollAcquisition;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSkeletonEntity.class)
public abstract class SkeletonScrollMixin {
    @Inject(method = "initialize", at = @At("RETURN"))
    private void progression$equipScroll(ServerWorldAccess world, LocalDifficulty difficulty,
            SpawnReason reason, EntityData data, NbtCompound nbt, CallbackInfoReturnable<EntityData> callback) {
        // 原版装备生成完毕后再赋予副手，保留原版主手弓与其他装备。
        ScrollAcquisition.equipSkeleton((AbstractSkeletonEntity) (Object) this);
    }
}
