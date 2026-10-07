package com.juicyslew.moonstation14.ms14.disposal.world;

import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.Objects;
import java.util.UUID;

/** Persistent ownership of a tube block cell and its host identity. */
public record DisposalTubeRecord(UUID id, BlockPos pos, String prototypeId, DisposalOrientation frame,
                                 boolean anchored, String routingTag, String routingFilter, long revision,
                                  HostKind hostKind, String sealedHostId, UUID hostGeneration) {
    public enum HostKind { EXPOSED, SEALED_WALL }
    public DisposalTubeRecord(UUID id, BlockPos pos, String prototypeId, Direction orientation, boolean anchored,
                              String routingTag, String routingFilter, long revision) {
        this(id,pos,prototypeId,new DisposalOrientation(orientation, orientation.getAxis()==Direction.Axis.Y ? Direction.NORTH : Direction.UP),anchored,routingTag,routingFilter,revision,HostKind.EXPOSED,"",null);
    }
    public DisposalTubeRecord {
        Objects.requireNonNull(id); Objects.requireNonNull(pos); Objects.requireNonNull(prototypeId); Objects.requireNonNull(frame); Objects.requireNonNull(hostKind);
        if (prototypeId.isBlank() || revision < 0 || hostKind == HostKind.SEALED_WALL && (sealedHostId == null || sealedHostId.isBlank() || hostGeneration == null)
                || hostKind == HostKind.EXPOSED && (hostGeneration != null || sealedHostId != null && !sealedHostId.isBlank())) throw new IllegalArgumentException("Invalid disposal tube record");
        pos=pos.immutable(); sealedHostId=Objects.requireNonNullElse(sealedHostId, "");
    }
    /** Compatibility convenience: local-forward direction. */
    public Direction orientation() { return frame.forward(); }
}
