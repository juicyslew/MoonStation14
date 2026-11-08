package com.juicyslew.moonstation14.ms14.tesla;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Pure upstream-compatible priority/probability admission and finite resistance chain. */
public final class TeslaLightningSelection {
    public record Candidate<T>(T value, int priority, double probability, double resistance) { }
    private TeslaLightningSelection() { }

    /** RobustRandom.Next(1, max): exclusive upper bound, with equal-bound max=1 yielding one. */
    public static int initialBoltCount(int authoredMaximum, Random random) {
        if (authoredMaximum < 1 || random == null) throw new IllegalArgumentException("invalid lightning maximum/random");
        return authoredMaximum == 1 ? 1 : random.nextInt(1, authoredMaximum);
    }

    /** Randomizes ties, sorts priority descending, and rolls until one candidate is admitted. */
    public static <T> Candidate<T> choose(List<Candidate<T>> candidates, Random random) {
        List<Candidate<T>> ordered = new ArrayList<>(candidates);
        java.util.Collections.shuffle(ordered, random);
        ordered.sort(Comparator.comparingInt(Candidate<T>::priority).reversed());
        for (Candidate<T> candidate : ordered)
            if (Double.isFinite(candidate.probability()) && random.nextDouble() < candidate.probability()) return candidate;
        return null;
    }

    /** Considers each candidate at most once in randomized priority order; misses do not use a bolt. */
    public static <T> List<Candidate<T>> chooseMany(List<Candidate<T>> candidates, int bolts, Random random) {
        if (bolts <= 0 || random == null) return List.of();
        List<Candidate<T>> ordered = new ArrayList<>(candidates);
        java.util.Collections.shuffle(ordered, random);
        ordered.sort(Comparator.comparingInt(Candidate<T>::priority).reversed());
        List<Candidate<T>> admitted = new ArrayList<>(Math.min(bolts, ordered.size()));
        for (Candidate<T> candidate : ordered) {
            if (Double.isFinite(candidate.probability()) && random.nextDouble() < candidate.probability()) {
                admitted.add(candidate);
                if (admitted.size() == bolts) break;
            }
        }
        return List.copyOf(admitted);
    }

    /** A successful strike emits one recursive bolt only when depth exceeds resistance. */
    public static boolean chains(int depth, double resistance) {
        return depth > 0 && Double.isFinite(resistance) && depth - resistance > 0;
    }
}
