package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.doors.AirlockBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.BlockCollisions")
public abstract class BlockCollisionsAirlockPassageMixin {
    @Unique @javax.annotation.Nullable private Entity moonstation14$actor;
    @Inject(method = "<init>(Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;ZLjava/util/function/BiFunction;)V", at = @At("TAIL"), require = 1)
    private void moonstation14$captureEntity(net.minecraft.world.level.CollisionGetter getter, Entity entity,
            net.minecraft.world.phys.AABB box, boolean suffocating,
            java.util.function.BiFunction<BlockState, BlockPos, VoxelShape> predicate, CallbackInfo ci) {
        this.moonstation14$actor = entity;
    }
    @Redirect(method = "computeNext()Ljava/lang/Object;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;"), require = 1)
    private VoxelShape moonstation14$actorAirlockShape(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext context) {
        return com.juicyslew.moonstation14.ms14.doors.AirlockPassagePolicy
                .physicalShape(moonstation14$actor, state, getter, pos, context);
    }
}
