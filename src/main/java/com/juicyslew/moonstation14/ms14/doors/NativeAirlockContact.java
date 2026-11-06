package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeCollisionGeometry;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Immutable selected blocker plus a bounded airlock nominee from Sable's actual native collision solver. */
public record NativeAirlockContact(SubLevel frame, BlockPos selectedPos, BlockState selectedState,
                                   BlockPos storagePos, BlockState state,
                                   BlockPos rootPos, java.util.UUID rootControllerIdentity,
                                   double normalX, double normalY, double normalZ,
                                   NativeCollisionGeometry diagnostic,
                                   boolean horizontal, boolean vertical, boolean preMoveDoorWitness,
                                   AirlockBlockEntity.ClientView observedClientView) {
    public NativeAirlockContact {
        java.util.Objects.requireNonNull(frame);
        selectedPos = java.util.Objects.requireNonNull(selectedPos).immutable();
        java.util.Objects.requireNonNull(selectedState);
        storagePos = java.util.Objects.requireNonNull(storagePos).immutable();
        java.util.Objects.requireNonNull(state);
        rootPos = java.util.Objects.requireNonNull(rootPos).immutable();
    }
}
