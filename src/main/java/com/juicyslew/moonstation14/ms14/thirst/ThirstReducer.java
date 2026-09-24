package com.juicyslew.moonstation14.ms14.thirst;

/** Pure upstream-inspired arithmetic and classifications for the thirst owner. */
public final class ThirstReducer {
    public static final float BASE_DECAY_PER_SECOND = 0.1f;
    public static final int INITIAL_MIN_INCLUSIVE = 310;
    public static final int INITIAL_MAX_EXCLUSIVE = 449;

    private ThirstReducer() { }

    public enum Level { OVER_HYDRATED, OKAY, THIRSTY, PARCHED, DEAD }

    /** The upstream threshold chooses the smallest threshold value greater than or equal to current. */
    public static Level classify(float thirst) {
        requireValid(thirst);
        if (thirst == 0f) return Level.DEAD;
        if (thirst <= 150f) return Level.PARCHED;
        if (thirst <= 300f) return Level.THIRSTY;
        if (thirst <= 450f) return Level.OKAY;
        return Level.OVER_HYDRATED;
    }

    /** Clamp a finite effect delta to the finite thirst range; malformed arithmetic fails without mutation. */
    public static Result satiate(float current, float factor, float scale) {
        if (!valid(current) || !Float.isFinite(factor) || !Float.isFinite(scale)) return Result.failed();
        float delta = factor * scale;
        float sum = current + delta;
        if (!Float.isFinite(delta) || !Float.isFinite(sum)) return Result.failed();
        float next = Math.max(ThirstComponent.MIN_THIRST, Math.min(ThirstComponent.MAX_THIRST, sum));
        return new Result(true, next, Float.compare(current, next) != 0);
    }

    /** Applies one elapsed second, using the level before decay exactly as upstream does. */
    public static Result decayOneSecond(float current) {
        if (!valid(current)) return Result.failed();
        float multiplier = switch (classify(current)) {
            case OVER_HYDRATED -> 1.2f;
            case OKAY -> 1f;
            case THIRSTY -> 0.8f;
            case PARCHED -> 0.6f;
            case DEAD -> 0f;
        };
        float next = Math.max(ThirstComponent.MIN_THIRST,
                current - BASE_DECAY_PER_SECOND * multiplier);
        return new Result(true, next, Float.compare(current, next) != 0);
    }

    /** Uses the supplied server random source at the caller's one-time initialization boundary. */
    public static int initialValue(int randomValue) {
        if (randomValue < INITIAL_MIN_INCLUSIVE || randomValue >= INITIAL_MAX_EXCLUSIVE)
            throw new IllegalArgumentException("initial thirst random value must be in [310, 449)");
        return randomValue;
    }

    private static boolean valid(float value) {
        return Float.isFinite(value) && value >= ThirstComponent.MIN_THIRST && value <= ThirstComponent.MAX_THIRST;
    }

    private static void requireValid(float value) {
        if (!valid(value)) throw new IllegalArgumentException("thirst must be finite and in [0, 600]");
    }

    public record Result(boolean valid, float after, boolean changed) {
        private static Result failed() { return new Result(false, Float.NaN, false); }
    }
}
