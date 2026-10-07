package com.juicyslew.moonstation14.ms14.containment;

import net.minecraft.core.BlockPos;

import java.util.Optional;
import java.util.UUID;

/** Immutable presentation contract for a body contained by a live owner. */
public record ContainedBodyView(UUID ownerIdentity, Optional<BlockPos> worldTarget, boolean cameraCollision) {
    public ContainedBodyView {
        java.util.Objects.requireNonNull(ownerIdentity);
        worldTarget = java.util.Objects.requireNonNull(worldTarget).map(BlockPos::immutable);
    }
}
