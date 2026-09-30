package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.juicyslew.moonstation14.ms14.fire.FireStackComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/** Pure TileFireEvent-style stack target; entity eligibility and persistence belong to FireStackSystem. */
public final class HotspotEntityExposure {
    private static final double FIRE_THRESHOLD_KELVIN = 373.15;

    private HotspotEntityExposure() { }

    public static float target(double temperatureKelvin) {
        if (!Double.isFinite(temperatureKelvin) || temperatureKelvin <= FIRE_THRESHOLD_KELVIN) return 0f;
        double stacks = Math.log((temperatureKelvin - FIRE_THRESHOLD_KELVIN) / 100.0) / Math.log(2.0) + 1.0;
        return (float) Math.min(FireStackComponent.MAX_STACKS, Math.max(0.0, stacks));
    }

    public static float increase(double temperatureKelvin, float currentStacks) {
        return Math.max(0f, target(temperatureKelvin) - currentStacks);
    }

    /** Cursor is an enumeration offset, not a tick number: repeated sparse fires still rotate.
     * The adapter must return at most limit matching occupants in stable enumeration order. */
    public static <T> Selection<T> select(int cursor, int limit,
            BiFunction<Predicate<T>, Integer, List<T>> query) {
        if (limit <= 0) throw new IllegalArgumentException("Exposure limit must be positive");
        int skip = Math.max(0, cursor);
        int[] seen = {0};
        List<T> tail = query.apply(ignored -> seen[0]++ >= skip, limit);
        if (tail.size() > limit) throw new IllegalArgumentException("Unbounded exposure query");
        if (tail.size() == limit) return new Selection<>(List.copyOf(tail),
                (int) Math.min(Integer.MAX_VALUE, (long) skip + limit));
        if (skip == 0) return new Selection<>(List.copyOf(tail), 0);
        List<T> head = query.apply(ignored -> true, limit - tail.size());
        if (head.size() > limit - tail.size()) throw new IllegalArgumentException("Unbounded exposure query");
        List<T> selected = new ArrayList<>(tail);
        selected.addAll(head);
        return new Selection<>(List.copyOf(selected), head.size());
    }

    public record Selection<T>(List<T> occupants, int nextCursor) { }
}
