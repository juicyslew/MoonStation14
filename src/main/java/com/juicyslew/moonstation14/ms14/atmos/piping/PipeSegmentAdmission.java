package com.juicyslew.moonstation14.ms14.atmos.piping;

import net.minecraft.core.BlockPos;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Read-only validation seam for projecting a persisted, exposed pipe into a loaded world graph. */
public final class PipeSegmentAdmission {
    private PipeSegmentAdmission() { }

    public enum Decision { ACCEPTED, INERT }

    /** Contains only the identity and volume needed to construct a graph node; never exposes gas ownership. */
    public record Snapshot(PipeTopology.NodeKey node, UUID id, double volume) {
        public Snapshot {
            Objects.requireNonNull(node, "node");
            Objects.requireNonNull(id, "id");
            if (!Double.isFinite(volume) || volume <= 0.0) throw new IllegalArgumentException("volume must be positive and finite");
        }
    }

    public record Result(Decision decision, Optional<Snapshot> snapshot) {
        public Result {
            Objects.requireNonNull(decision, "decision");
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
            if ((decision == Decision.ACCEPTED) != snapshot.isPresent()) throw new IllegalArgumentException("decision and snapshot disagree");
        }
    }

    /**
     * Inputs are snapshots supplied by the loaded-world caller. The caller MUST supply a record from the exact
     * loaded chunk containing {@code position}; local x/z checks cannot prove chunk identity. The
     * {@code liveBlockMatches} flag must verify the exact loaded block and, when available, its block-entity UUID.
     * This admission seam is not proof of world state and cannot mint gas.
     */
    public static Result classify(int presentVisualLayerMask, BlockPos position, PipeChunkData.Record record,
                                  PipeGasInventory.Entry entry, boolean liveBlockMatches) {
        Objects.requireNonNull(position, "position");
        if (presentVisualLayerMask == 0 || (presentVisualLayerMask & ~7) != 0) return inert();
        if (record == null || entry == null || !liveBlockMatches || record.kind() != PipeTopology.Kind.EXPOSED)
            return inert();
        int bit = switch (record.layer()) {
            case PRIMARY -> VisualPipeBlockEntity.PRIMARY;
            case SECONDARY -> VisualPipeBlockEntity.SECONDARY;
            case TERTIARY -> VisualPipeBlockEntity.TERTIARY;
        };
        if ((presentVisualLayerMask & bit) == 0 || record.x() != (position.getX() & 15)
                || record.y() != position.getY() || record.z() != (position.getZ() & 15)) return inert();
        PipeTopology.NodeKey node = new PipeTopology.NodeKey(position, record.layer());
        if (!entry.active() || entry.version() <= 0 || !entry.node().equals(node)
                || !entry.incarnation().equals(record.id()) || Double.compare(entry.volume(), record.volume()) != 0
                || !Double.isFinite(entry.volume()) || entry.volume() <= 0.0) return inert();
        return new Result(Decision.ACCEPTED, Optional.of(new Snapshot(node, record.id(), record.volume())));
    }

    private static Result inert() { return new Result(Decision.INERT, Optional.empty()); }
}
