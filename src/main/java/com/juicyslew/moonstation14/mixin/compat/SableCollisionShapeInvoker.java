package com.juicyslew.moonstation14.mixin.compat;

import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Narrow access to Sable's native shape selection, including its scaffolding policy. */
@Mixin(value = SubLevelEntityCollision.class, remap = false)
public interface SableCollisionShapeInvoker {
    @Invoker(value = "getSubLevelEntityCollisionShape", remap = false)
    static VoxelShape ms14$collisionShape(Entity entity, Vector3dc boundsCenter, Pose3dc subLevelPose,
            BlockState state, LevelAccelerator level, BlockPos pos, LevelReusedVectors sink) {
        throw new AssertionError("Mixin invoker was not transformed");
    }
}
