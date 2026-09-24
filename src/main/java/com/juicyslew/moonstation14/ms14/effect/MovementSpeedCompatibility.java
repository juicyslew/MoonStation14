package com.juicyslew.moonstation14.ms14.effect;

import com.juicyslew.moonstation14.MoonStation14;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Admission and diagnostics for the single Minecraft movement-speed value. */
public final class MovementSpeedCompatibility {
    private static final Set<Shape> WARNED_UNEQUAL_SHAPES = ConcurrentHashMap.newKeySet();
    private static volatile Consumer<String> warningSink = message -> MoonStation14.LOGGER.warn("{}", message);

    private MovementSpeedCompatibility() {
    }

    public static boolean supports(float walk, float sprint) {
        return walk == sprint;
    }

    /**
     * Warns once for each normalized pair.  The sink is injectable so tests
     * can assert degradation without intercepting the global logger.
     */
    public static void warnUnequalOnce(float walk, float sprint) {
        Shape shape = new Shape(normalizeBits(walk), normalizeBits(sprint));
        if (WARNED_UNEQUAL_SHAPES.add(shape)) {
            warningSink.accept("MovementSpeedModifier is unsupported when walk and sprint multipliers differ: "
                    + walk + " vs " + sprint);
        }
    }

    public static void setWarningSink(Consumer<String> sink) {
        warningSink = java.util.Objects.requireNonNull(sink, "sink");
    }

    public static void resetWarnings() {
        WARNED_UNEQUAL_SHAPES.clear();
    }

    public static void resetWarningSink() {
        warningSink = message -> MoonStation14.LOGGER.warn("{}", message);
    }

    private static int normalizeBits(float value) {
        return value == 0f ? 0 : Float.floatToIntBits(value);
    }

    private record Shape(int walkBits, int sprintBits) {
    }
}
