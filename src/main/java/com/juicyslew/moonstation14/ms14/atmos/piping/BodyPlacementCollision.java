package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Read-only collision preflight for newly introduced placement geometry, not selection outlines. */
public final class BodyPlacementCollision {
    private BodyPlacementCollision() { }

    static boolean deviceIntersects(ServerLevel level, LivingEntity body, BlockPos pos, BlockState placement) {
        return intersects(placement.getCollisionShape(level, pos, CollisionContext.of(body)), pos, body.getBoundingBox());
    }

    /** Collision preflight for a complete newly placed block shape. */
    public static boolean blockIntersects(Level level, LivingEntity body, BlockPos pos, BlockState placement) {
        return intersects(placement.getCollisionShape(level, pos, CollisionContext.of(body)), pos, body.getBoundingBox());
    }

    static boolean pipeIntersects(ServerLevel level, LivingEntity body, BlockPos pos, int lane) {
        return pipeIntersects(level, body.getBoundingBox(), pos, lane);
    }

    static boolean pipeIntersects(ServerLevel level, AABB bounds, BlockPos pos, int lane) {
        if (lane != VisualPipeBlockEntity.PRIMARY && lane != VisualPipeBlockEntity.SECONDARY
                && lane != VisualPipeBlockEntity.TERTIARY) return true;
        BlockState current = level.getBlockState(pos);
        VoxelShape before = current.getCollisionShape(level, pos, CollisionContext.empty());
        int low = lane == VisualPipeBlockEntity.PRIMARY ? 6 : lane == VisualPipeBlockEntity.SECONDARY ? 2 : 12;
        int high = lane == VisualPipeBlockEntity.PRIMARY ? 10 : low + 2;
        VoxelShape added = box(low, low, low, high, high, high);
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            // Never request/generate a chunk just to predict a connection.
            if (!level.isInWorldBounds(neighbor) || level.isOutsideBuildHeight(neighbor)
                    || !(level.getChunkSource().getChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4,
                    ChunkStatus.FULL, false) instanceof LevelChunk)) continue;
            BlockState neighborState = level.getBlockState(neighbor);
            if (!neighborState.is(ModBlocks.GAS_PIPE_VISUAL.get())
                    || !(level.getBlockEntity(neighbor) instanceof VisualPipeBlockEntity visual)
                    || !visual.hasLayer(lane)) continue;
            added = Shapes.or(added, arm(direction, low, high));
            VoxelShape existingNeighbor = neighborState.getCollisionShape(level, neighbor, CollisionContext.empty());
            VoxelShape newNeighborArm = arm(direction.getOpposite(), low, high);
            if (intersects(Shapes.join(newNeighborArm, existingNeighbor, BooleanOp.ONLY_FIRST), neighbor, bounds)) return true;
        }
        return intersects(Shapes.join(added, before, BooleanOp.ONLY_FIRST), pos, bounds);
    }

    static boolean concealedArmIntersects(ServerLevel level, LivingEntity body, BlockPos wall,
                                           Direction wallFace, int lane) {
        BlockPos exposed = wall.relative(wallFace);
        if (!level.hasChunkAt(exposed) || !level.getBlockState(exposed).is(ModBlocks.GAS_PIPE_VISUAL.get())
                || !(level.getBlockEntity(exposed) instanceof VisualPipeBlockEntity pipe)
                || !pipe.hasLayer(lane)) return false;
        int low = lane == VisualPipeBlockEntity.PRIMARY ? 6 : lane == VisualPipeBlockEntity.SECONDARY ? 2 : 12;
        int high = lane == VisualPipeBlockEntity.PRIMARY ? 10 : low + 2;
        return intersects(arm(wallFace.getOpposite(), low, high), exposed, body.getBoundingBox());
    }

    private static VoxelShape arm(Direction direction, int low, int high) {
        return switch (direction) {
            case NORTH -> box(low, low, 0, high, high, low);
            case SOUTH -> box(low, low, high, high, high, 16);
            case EAST -> box(high, low, low, 16, high, high);
            case WEST -> box(0, low, low, low, high, high);
            case UP -> box(low, high, low, high, 16, high);
            case DOWN -> box(low, 0, low, high, low, high);
        };
    }

    private static VoxelShape box(int x1, int y1, int z1, int x2, int y2, int z2) {
        return Shapes.box(x1 / 16.0, y1 / 16.0, z1 / 16.0, x2 / 16.0, y2 / 16.0, z2 / 16.0);
    }

    static boolean intersects(VoxelShape shape, BlockPos pos, AABB bounds) {
        for (AABB box : shape.toAabbs()) {
            if (box.minX + pos.getX() < bounds.maxX && box.maxX + pos.getX() > bounds.minX
                    && box.minY + pos.getY() < bounds.maxY && box.maxY + pos.getY() > bounds.minY
                    && box.minZ + pos.getZ() < bounds.maxZ && box.maxZ + pos.getZ() > bounds.minZ) return true;
        }
        return false;
    }
}
