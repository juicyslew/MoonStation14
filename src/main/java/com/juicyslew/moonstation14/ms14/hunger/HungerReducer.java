package com.juicyslew.moonstation14.ms14.hunger;

import com.juicyslew.moonstation14.ms14.character.components.HungerPrototypeComponent;

/** Pure finite float arithmetic and transition planning for hunger. */
public final class HungerReducer {
    public static final float BASE_DECAY_PER_SECOND = 0.01666666666f;
    public static final int INITIAL_MIN_INCLUSIVE = 110;
    public static final int INITIAL_MAX_EXCLUSIVE = 150;
    private HungerReducer() { }

    public enum Level {
        OVERFED, OKAY, PECKISH, STARVING, DEAD;

        /** Zero nutrition remains the hunger system's terminal band, but is presented as starvation. */
        public String displayLabel() { return this == DEAD ? STARVING.name() : name(); }
    }

    public static Level classify(float hunger) {
        return classify(hunger, HungerPrototypeComponent.DEFAULT);
    }

    public static Level classify(float hunger, HungerPrototypeComponent p) {
        requireValid(hunger);
        if (hunger <= p.dead()) return Level.DEAD;
        if (hunger <= p.starving()) return Level.STARVING;
        if (hunger <= p.peckish()) return Level.PECKISH;
        if (hunger < p.overfed()) return Level.OKAY;
        return Level.OVERFED;
    }

    public static Result decayOneSecond(float current) {
        if (!valid(current)) return Result.failed();
        return decayOneSecond(current, HungerPrototypeComponent.DEFAULT);
    }
    public static Result decayOneSecond(float current, HungerPrototypeComponent p) {
        if (!valid(current)) return Result.failed();
        float multiplier = switch (classify(current, p)) {
            case OVERFED -> 1.2f; case OKAY -> 1f; case PECKISH -> 0.8f; case STARVING, DEAD -> 0.6f;
        };
        float next = Math.max(p.dead(), Math.min(p.overfed(), current - p.decay() * multiplier));
        return new Result(true, current, next, Float.compare(current, next) != 0);
    }

    public static int initialValue(int randomValue) {
        return initialValue(randomValue, HungerPrototypeComponent.DEFAULT);
    }
    public static int initialValue(int randomValue, HungerPrototypeComponent policy) {
        int minimum = (int) policy.peckish() + 10;
        int maximum = (int) policy.okay();
        if (maximum <= minimum) {
            if (randomValue != minimum) throw new IllegalArgumentException("initial hunger must equal " + minimum);
            return minimum;
        }
        if (randomValue < minimum || randomValue >= maximum) throw new IllegalArgumentException("initial hunger outside resolved range");
        return randomValue;
    }

    private static boolean valid(float value) { return Float.isFinite(value) && value >= 0f && value <= 200f; }
    private static void requireValid(float value) {
        if (!valid(value)) throw new IllegalArgumentException("hunger must be finite and in [0, 200]");
    }
    public static Result satiate(float current, float factor, float scale) {
        return satiate(current, factor, scale, HungerPrototypeComponent.DEFAULT);
    }
    public static Result satiate(float current, float factor, float scale, HungerPrototypeComponent policy) {
        if (!valid(current)
                || !Float.isFinite(factor) || !Float.isFinite(scale)) return Result.failed();
        float delta = factor * scale;
        float sum = current + delta;
        if (!Float.isFinite(delta) || !Float.isFinite(sum)) return Result.failed();
        float next = Math.max(policy.dead(), Math.min(policy.overfed(), sum));
        return new Result(true, current, next, Float.compare(current, next) != 0);
    }
    public record Result(boolean valid, float before, float after, boolean changed) {
        private static Result failed() { return new Result(false, Float.NaN, Float.NaN, false); }
    }
}

