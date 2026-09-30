package com.juicyslew.moonstation14.ms14.fire;

import java.util.Objects;

/** Pure fire-stack transitions; entity persistence belongs to {@link FireStackSystem}. */
public final class FireStackReducer {
    private FireStackReducer() {
    }

    public static FireStackComponent flammable(FireStackComponent current, float multiplier,
                                                Float multiplierOnExisting, float scale) {
        Objects.requireNonNull(current, "current");
        requireFinite(multiplier, "multiplier");
        if (current.stacks() != 0f && multiplierOnExisting != null) {
            requireFinite(multiplierOnExisting, "multiplierOnExisting");
            multiplier = multiplierOnExisting;
        }
        double next = (double) current.stacks() + (double) multiplier * requireFinite(scale, "scale");
        float clamped = clamp(next);
        if (current.ignited() && clamped <= 0f) {
            return FireStackComponent.EMPTY;
        }
        boolean ignited = current.ignited() && clamped > 0f;
        return new FireStackComponent(clamped, ignited);
    }

    public static FireStackComponent ignite(FireStackComponent current) {
        Objects.requireNonNull(current, "current");
        return current.stacks() > 0f && !current.ignited()
                ? new FireStackComponent(current.stacks(), true)
                : current;
    }

    public static FireStackComponent extinguish(FireStackComponent current, float adjustment, float scale) {
        Objects.requireNonNull(current, "current");
        requireFinite(adjustment, "adjustment");
        double baseline = current.ignited() ? 0d : current.stacks();
        float clamped = clamp(baseline + (double) adjustment * requireFinite(scale, "scale"));
        return new FireStackComponent(clamped, false);
    }

    /** Recovers negative stacks by exactly one stack per due drying interval. */
    public static FireStackComponent dry(FireStackComponent current) {
        Objects.requireNonNull(current, "current");
        if (current.stacks() >= 0f) {
            return current;
        }
        return new FireStackComponent(Math.min(0f, current.stacks() + 1f), false);
    }

    /** Applies one configured decay step to a lit stack, extinguishing at zero. */
    public static FireStackComponent fadeIgnited(FireStackComponent current, float fade) {
        Objects.requireNonNull(current, "current");
        requireFinite(fade, "fade");
        if (!current.ignited() || current.stacks() <= 0f) return current;
        float stacks = clamp((double) current.stacks() + fade);
        return stacks <= 0f ? FireStackComponent.EMPTY : new FireStackComponent(stacks, true);
    }

    private static float clamp(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("fire stack result must be finite");
        }
        return (float) Math.max(FireStackComponent.MIN_STACKS,
                Math.min(FireStackComponent.MAX_STACKS, value));
    }

    private static float requireFinite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }
}
