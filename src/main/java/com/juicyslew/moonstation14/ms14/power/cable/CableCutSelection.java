package com.juicyslew.moonstation14.ms14.power.cable;

import java.util.Set;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.PowerTopology;

/** Pure choice of which tier, if any, a cutter may remove from one clicked face. */
public final class CableCutSelection {
    private CableCutSelection() { }

    public enum Refusal { NONE, MISSING, AMBIGUOUS, NO_VISIBLE_HIT }

    public record Result(CableTier tier, Refusal refusal) {
        public boolean allowed() { return tier != null; }
    }

    /** Authoritative visible-lane selection; every storage read is loaded-only and locally bounded. */
    public static Result selectAt(ServerLevel level, BlockPos host, Direction face, Vec3 hit) {
        if (level == null || host == null || face == null || hit == null
                || level.isOutsideBuildHeight(host) || !level.getWorldBorder().isWithinBounds(host)
                || !CableFacePresentation.isVisible(level, host, face))
            return new Result(null, Refusal.MISSING);
        EnumSet<CableTier> present = EnumSet.noneOf(CableTier.class);
        for (CableTier tier : CableTier.values())
            if (CableStorage.has(level, host, face, tier)) present.add(tier);
        if (present.isEmpty()) return new Result(null, Refusal.MISSING);

        CableTier selected = null;
        for (CableTier tier : present) {
            CableFaceNode node = new CableFaceNode(host, face, tier);
            List<CableFaceNode> neighbors = new ArrayList<>(8);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos neighborHost = host.offset(dx, dy, dz);
                    for (Direction neighborFace : Direction.values()) {
                        CableFaceNode candidate = new CableFaceNode(neighborHost, neighborFace, tier);
                        if (PowerTopology.cableEdge(node, candidate).isPresent()
                                && CableStorage.has(level, neighborHost, neighborFace, tier))
                            neighbors.add(candidate);
                    }
                }
            var geometry = CableCutGeometry.select(host, face, hit, face, List.of(tier),
                    PowerTopology.cableNeighbors(node, neighbors));
            if (geometry.isPresent() && (selected == null
                    || CableVisualGeometry.drawPriority(tier) > CableVisualGeometry.drawPriority(selected)))
                selected = geometry.orElseThrow();
        }
        return selected == null ? new Result(null, Refusal.NO_VISIBLE_HIT)
                : new Result(selected, Refusal.NONE);
    }

    public static Result select(Set<CableTier> existingTiers, CableTier selected) {
        if (selected == null) return new Result(null, Refusal.NO_VISIBLE_HIT);
        return existingTiers.contains(selected)
                ? new Result(selected, Refusal.NONE) : new Result(null, Refusal.MISSING);
    }
}

