package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.mixin.compat.SableCollisionShapeInvoker;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedBlockGetter;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;

/** Per-query bridge into Sable's native block collision-shape selection. */
public final class NativeFrameShapeQuery {
    private final Entity entity;
    private final Pose3dc framePose;
    private final LevelAccelerator guardedLevel;
    private final LevelReusedVectors scratch = new LevelReusedVectors();
    private final Vector3d boundsCenter;

    public NativeFrameShapeQuery(Level level, Entity entity, Pose3dc framePose, LoadedBlockGetter guardedBlocks) {
        this(level, entity, framePose, guardedBlocks, entity.getBoundingBox());
    }

    /** Query at an explicit candidate volume without moving the entity. */
    public NativeFrameShapeQuery(Level level, Entity entity, Pose3dc framePose, LoadedBlockGetter guardedBlocks,
            net.minecraft.world.phys.AABB candidateBounds) {
        this.entity = entity;
        this.framePose = framePose;
        if (EntitySubLevelUtil.getCustomEntityOrientation(entity, 0.0f) != null)
            throw new LoadedBlockGetter.UnknownChunk();
        this.guardedLevel = guardedBlocks.asNativeCollisionAccess();
        this.scratch.entityUpDirection.set(0.0, 1.0, 0.0);
        if (candidateBounds == null || !Double.isFinite(candidateBounds.minX) || !Double.isFinite(candidateBounds.minY)
                || !Double.isFinite(candidateBounds.minZ) || !Double.isFinite(candidateBounds.maxX)
                || !Double.isFinite(candidateBounds.maxY) || !Double.isFinite(candidateBounds.maxZ)
                || candidateBounds.getXsize() <= 0 || candidateBounds.getYsize() <= 0 || candidateBounds.getZsize() <= 0)
            throw new LoadedBlockGetter.UnknownChunk();
        var box = candidateBounds;
        this.boundsCenter = new Vector3d((box.minX + box.maxX) * .5, (box.minY + box.maxY) * .5,
                (box.minZ + box.maxZ) * .5);
    }

    public VoxelShape shape(BlockPos pos, BlockState state) {
        return SableCollisionShapeInvoker.ms14$collisionShape(entity, boundsCenter, framePose,
                state, guardedLevel, pos, scratch);
    }
}
