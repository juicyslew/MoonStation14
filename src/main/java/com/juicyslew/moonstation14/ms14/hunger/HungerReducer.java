package com.juicyslew.moonstation14.ms14.hunger;

/** Pure finite float arithmetic and transition planning for hunger. */
public final class HungerReducer {
    public static final float BASE_DECAY_PER_SECOND = 0.01666666666f;
    public static final int INITIAL_MIN_INCLUSIVE = 110;
    public static final int INITIAL_MAX_EXCLUSIVE = 150;
    private HungerReducer() { }

    public enum Level { OVERFED, OKAY, PECKISH, STARVING, DEAD }

    public static Level classify(float hunger) {
        requireValid(hunger);
        if (hunger == 0f) return Level.DEAD;
        if (hunger >= 200f) return Level.OVERFED;
        if (hunger >= 150f) return Level.OKAY;
        if (hunger >= 100f) return Level.PECKISH;
        if (hunger >= 50f) return Level.STARVING;
        return Level.STARVING;
    }

    public static Result decayOneSecond(float current) {
        if (!valid(current)) return Result.failed();
        float multiplier = switch (classify(current)) {
            case OVERFED -> 1.2f;
            case OKAY -> 1f;
            case PECKISH -> 0.8f;
            case STARVING, DEAD -> 0.6f;
        };
        float rate = BASE_DECAY_PER_SECOND * multiplier;
        float next = Math.max(0f, Math.min(200f, current - rate));
        return new Result(true, current, next, Float.compare(current, next) != 0);
    }

    public static int initialValue(int randomValue) {
        if (randomValue < INITIAL_MIN_INCLUSIVE || randomValue >= INITIAL_MAX_EXCLUSIVE)
            throw new IllegalArgumentException("initial hunger random value must be in [110, 150)");
        return randomValue;
    }

    private static boolean valid(float value) { return Float.isFinite(value) && value >= 0f && value <= 200f; }
    private static void requireValid(float value) {
        if (!valid(value)) throw new IllegalArgumentException("hunger must be finite and in [0, 200]");
    }
    public static Result satiate(float current, float factor, float scale) {
        if (!valid(current)
                || !Float.isFinite(factor) || !Float.isFinite(scale)) return Result.failed();
        float delta = factor * scale;
        float sum = current + delta;
        if (!Float.isFinite(delta) || !Float.isFinite(sum)) return Result.failed();
        float next = Math.max(0f, Math.min(200f, sum));
        return new Result(true, current, next, Float.compare(current, next) != 0);
    }
    public record Result(boolean valid, float before, float after, boolean changed) {
        private static Result failed() { return new Result(false, Float.NaN, Float.NaN, false); }
    }
}
