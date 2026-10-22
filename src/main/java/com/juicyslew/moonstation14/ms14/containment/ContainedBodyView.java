package com.juicyslew.moonstation14.ms14.containment;

import net.minecraft.core.BlockPos;

import java.util.Optional;
import java.util.UUID;

/** Immutable presentation contract for a body contained by a live owner. */
public record ContainedBodyView(UUID ownerIdentity, Optional<BlockPos> worldTarget, boolean cameraCollision,
                                Optional<RenderParent> renderParent) {
    public record RenderParent(int entityId, UUID entityUuid, net.minecraft.world.phys.Vec3 bodyFeetOffset) {
        public RenderParent {
            if (entityId < 0) throw new IllegalArgumentException("render parent entity id must be nonnegative");
            java.util.Objects.requireNonNull(entityUuid);
            java.util.Objects.requireNonNull(bodyFeetOffset);
            if (!Double.isFinite(bodyFeetOffset.x) || !Double.isFinite(bodyFeetOffset.y) || !Double.isFinite(bodyFeetOffset.z))
                throw new IllegalArgumentException("render parent offset must be finite");
        }
    }
    public ContainedBodyView {
        java.util.Objects.requireNonNull(ownerIdentity);
        worldTarget = java.util.Objects.requireNonNull(worldTarget).map(BlockPos::immutable);
        renderParent = java.util.Objects.requireNonNull(renderParent);
    }
    public ContainedBodyView(UUID ownerIdentity, Optional<BlockPos> worldTarget, boolean cameraCollision) {
        this(ownerIdentity, worldTarget, cameraCollision, Optional.empty());
    }
}

