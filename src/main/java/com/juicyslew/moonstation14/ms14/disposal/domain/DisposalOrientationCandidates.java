package com.juicyslew.moonstation14.ms14.disposal.domain;

import com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData;
import java.util.*;
import net.minecraft.core.Direction;

/** Semantic candidate frames, ordered to favor reciprocal connections with nearby tubes. */
public final class DisposalOrientationCandidates {
    private DisposalOrientationCandidates() { }

    public static List<DisposalOrientation> candidates(DisposalEntityData.Tube tube,
            DisposalOrientation preferred, Set<Direction> reciprocalNeighbourFaces) {
        Objects.requireNonNull(tube);
        Objects.requireNonNull(preferred);
        Objects.requireNonNull(reciprocalNeighbourFaces);
        Map<Object, DisposalOrientation> unique = new LinkedHashMap<>();
        for (Direction forward : Direction.values()) for (Direction up : Direction.values()) {
            if (forward.getAxis() == up.getAxis()) continue;
            var frame = new DisposalOrientation(forward, up);
            unique.merge(semanticKey(tube, frame), frame,
                    (a, b) -> closeness(b, preferred) > closeness(a, preferred) ? b : a);
        }
        var result = new ArrayList<>(unique.values());
        result.sort(Comparator.<DisposalOrientation>comparingInt(f -> score(tube, f, reciprocalNeighbourFaces)).reversed()
                .thenComparing(Comparator.comparingInt((DisposalOrientation f) -> closeness(f, preferred)).reversed())
                .thenComparingInt(f -> f.forward().ordinal()).thenComparingInt(f -> f.up().ordinal()));
        return List.copyOf(result);
    }

    private static Object semanticKey(DisposalEntityData.Tube tube, DisposalOrientation frame) {
        Set<DisposalDirection> ports = DisposalTubeRouting.connections(tube, frame);
        return switch (tube.kind()) {
            case STRAIGHT, BEND, TAGGER -> ports;
            case TRUNK -> DisposalDirection.fromMC(frame.world(tube.topology().directions().get(0).rotate(tube.rotationQuarters())));
            case ROUTER -> List.of(ports,
                    DisposalDirection.fromMC(frame.world(tube.topology().directions().get(0).rotate(tube.rotationQuarters()))),
                    DisposalDirection.fromMC(frame.world(tube.topology().directions().get(1).rotate(tube.rotationQuarters()))));
            default -> List.of(ports, DisposalDirection.fromMC(frame.world(tube.topology().directions().get(0).rotate(tube.rotationQuarters()))));
        };
    }

    private static int score(DisposalEntityData.Tube tube, DisposalOrientation frame, Set<Direction> faces) {
        var ports = DisposalTubeRouting.connections(tube, frame);
        int score = 0;
        for (Direction face : faces) if (ports.contains(DisposalDirection.fromMC(face))) score++;
        return score;
    }

    private static int closeness(DisposalOrientation a, DisposalOrientation b) {
        int same = 0;
        for (DisposalDirection local : DisposalDirection.values()) if (a.world(local) == b.world(local)) same++;
        return same;
    }
}

