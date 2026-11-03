package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.Objects;
import java.util.UUID;

/** A client-supplied candidate target, never authoritative gameplay evidence. */
public sealed interface MeleeTargetNomination permits MeleeTargetNomination.Entity, MeleeTargetNomination.Block {
    record Entity(UUID entityUUID) implements MeleeTargetNomination {
        public Entity { Objects.requireNonNull(entityUUID); }
    }

    record Block(BlockPos pos, Direction face, Vec3 hitpoint, int expectedBlockStateID) implements MeleeTargetNomination {
        public static Block fromHit(net.minecraft.world.phys.BlockHitResult hit,
                                    net.minecraft.world.level.block.state.BlockState state) {
            Objects.requireNonNull(hit); Objects.requireNonNull(state);
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK)
                throw new IllegalArgumentException("block nomination requires a block hit");
            return new Block(hit.getBlockPos(), hit.getDirection(), hit.getLocation(),
                    net.minecraft.world.level.block.Block.getId(state));
        }
        public Block {
            Objects.requireNonNull(pos); Objects.requireNonNull(face); Objects.requireNonNull(hitpoint);
            pos = pos.immutable();
            if (expectedBlockStateID < 0 || !Double.isFinite(hitpoint.x) || !Double.isFinite(hitpoint.y) || !Double.isFinite(hitpoint.z)
                    || hitpoint.x < pos.getX() - 1 || hitpoint.x > pos.getX() + 2
                    || hitpoint.y < pos.getY() - 1 || hitpoint.y > pos.getY() + 2
                    || hitpoint.z < pos.getZ() - 1 || hitpoint.z > pos.getZ() + 2)
                throw new IllegalArgumentException("invalid block nomination");
        }
    }
}
