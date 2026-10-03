package com.juicyslew.moonstation14.ms14.infrastructure.scanner;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Pure geometry over caller-provided, already-loaded scanner candidates. This does not query a world. */
public final class ScanGeometry {
    /** Provisional hard input cap; reject the entire request above this size, never reveal a partial scan. */
    public static final int MAX_CANDIDATES = 4096;
    /** Provisional hard output cap. */
    public static final int MAX_RESULTS = 512;

    private ScanGeometry() { }

    public enum Type { PIPE, CABLE }

    /** Immutable description of a caller-supplied candidate. */
    public record Candidate(Type type, Vec3 center) {
        public Candidate {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(center, "center");
            requireFinite(center.x, center.y, center.z, "candidate center");
        }
    }

    /** Immutable result; distanceSquared is measured from the supplied server-owned center. */
    public record Result(Candidate candidate, double distanceSquared) { }

    /**
     * Select candidates in an exact 3D sphere. The caller must provide an authoritative center and
     * candidates it has already safely loaded. World queries, observer authorization and item activation
     * are intentionally outside this class. Cable concealment remains governed by existing visual-only logic.
     */
    public static List<Result> select(Vec3 center, double radius, List<Candidate> candidates) {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(candidates, "candidates");
        requireFinite(center.x, center.y, center.z, "scan center");
        if (!Double.isFinite(radius) || radius <= 0) throw new IllegalArgumentException("radius must be finite and positive");
        if (candidates.size() > MAX_CANDIDATES) throw new IllegalArgumentException("candidate budget exceeded");

        double radiusSquared = radius * radius;
        if (!Double.isFinite(radiusSquared)) throw new IllegalArgumentException("radius is too large");
        List<Result> results = new ArrayList<>();
        Set<Candidate> seen = new HashSet<>();
        for (Candidate candidate : candidates) {
            Objects.requireNonNull(candidate, "candidate");
            Vec3 point = candidate.center();
            double dx = point.x - center.x;
            double dy = point.y - center.y;
            double dz = point.z - center.z;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared <= radiusSquared && seen.add(candidate)) {
                if (results.size() == MAX_RESULTS) throw new IllegalArgumentException("result budget exceeded");
                results.add(new Result(candidate, distanceSquared));
            }
        }
        results.sort(Comparator.comparingDouble(Result::distanceSquared)
                .thenComparing(result -> result.candidate().type())
                .thenComparingDouble(result -> result.candidate().center().x)
                .thenComparingDouble(result -> result.candidate().center().y)
                .thenComparingDouble(result -> result.candidate().center().z));
        return List.copyOf(results);
    }

    private static void requireFinite(double x, double y, double z, String name) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException(name + " coordinates must be finite");
        }
    }
}
