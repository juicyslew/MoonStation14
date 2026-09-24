package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import net.minecraft.resources.ResourceKey;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Pure fixed-precision reagent arithmetic. Quantities are integer hundredths (cents),
 * matching SS14 FixedPoint2's minimum precision. Legacy numeric boundaries interpret
 * values as their canonical decimal representation and truncate toward zero; there
 * is no epsilon or nearest-cent rounding. Thus sub-cent amounts are intentionally lost.
 * The bounded range keeps conversion
 * and proportional arithmetic practical and is far beyond gameplay-sized solutions.
 * Runtime reagent storage and its transfer/capacity/reaction mutators use this
 * representation; external APIs and serialized schemas remain float-shaped.
 */
public final class ReagentUnits {
    /** Largest supported amount: 21,474,836.47 units (well above the 200,000-unit puddle cap). */
    public static final long MAX_CENTS = 2_147_483_647L;
    private static final BigInteger BIG_MAX_CENTS = BigInteger.valueOf(MAX_CENTS);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ReagentUnits() {
    }

    /** Converts finite nonnegative float units to cents, truncating sub-cent residue. */
    public static long fromFloat(float units) {
        if (!Float.isFinite(units) || units < 0f) {
            throw new IllegalArgumentException("units must be finite and nonnegative");
        }
        // Float.toString preserves the canonical decimal value callers see. A
        // float rounded to an exact cent by earlier arithmetic is inherently
        // indistinguishable from an intentionally exact-cent input here.
        return decimalToCents(new BigDecimal(Float.toString(units)));
    }

    /** Converts numeric persistent data to cents, truncating sub-cent legacy values. */
    public static long fromDouble(double units) {
        if (!Double.isFinite(units) || units < 0d) {
            throw new IllegalArgumentException("units must be finite and nonnegative");
        }
        return decimalToCents(BigDecimal.valueOf(units));
    }

    private static long decimalToCents(BigDecimal units) {
        BigInteger cents = units.multiply(HUNDRED).setScale(0, RoundingMode.DOWN).toBigIntegerExact();
        if (cents.compareTo(BIG_MAX_CENTS) > 0) {
            throw new IllegalArgumentException("units exceed maximum reagent quantity");
        }
        return cents.longValueExact();
    }

    /** Converts cents to float units. */
    public static float toFloat(long cents) {
        requireCents(cents, "cents");
        return cents / 100f;
    }

    /** Validates and returns an immutable snapshot ordered by resource location. */
    public static Map<ResourceKey<ReagentData>, Long> fromMap(Map<ResourceKey<ReagentData>, Float> values) {
        Objects.requireNonNull(values, "values");
        Map<ResourceKey<ReagentData>, Long> ordered = new LinkedHashMap<>();
        values.keySet().stream()
                .map(key -> Objects.requireNonNull(key, "reagent key"))
                .sorted((left, right) -> left.location().compareTo(right.location()))
                .forEach(key -> ordered.put(key, fromFloat(Objects.requireNonNull(values.get(key), "reagent amount"))));
        return Collections.unmodifiableMap(ordered);
    }

    /** Adds quantities with overflow detection and supported-range enforcement. */
    public static long total(Iterable<Long> quantities) {
        Objects.requireNonNull(quantities, "quantities");
        long sum = 0;
        for (Long quantity : quantities) {
            long checked = requireCents(Objects.requireNonNull(quantity, "quantity"), "quantity");
            try {
                sum = Math.addExact(sum, checked);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("reagent total overflow", exception);
            }
            if (sum > MAX_CENTS) {
                throw new IllegalArgumentException("reagent total exceeds maximum capacity");
            }
        }
        return sum;
    }

    /**
     * Proportionally removes exactly {@code requestedCents}; all source quantities
     * are nonnegative. Integer division remainder cents go to the first keys in
     * ResourceLocation order, making results independent of input map iteration.
     * The returned map contains amounts removed, including zero shares.
     */
    public static <K extends ResourceKey<?>> Map<K, Long> split(Map<K, Long> source, long requestedCents) {
        Objects.requireNonNull(source, "source");
        requireCents(requestedCents, "requestedCents");
        Map<K, Long> orderedSource = new LinkedHashMap<>();
        source.entrySet().stream()
                .peek(entry -> Objects.requireNonNull(entry.getKey(), "reagent key"))
                .sorted((left, right) -> left.getKey().location().compareTo(right.getKey().location()))
                .forEach(entry -> orderedSource.put(entry.getKey(), requireCents(
                        Objects.requireNonNull(entry.getValue(), "source amount"), "source amount")));
        long available = total(orderedSource.values());
        if (requestedCents > available) {
            throw new IllegalArgumentException("requested quantity exceeds source total");
        }

        Map<K, Long> removed = new LinkedHashMap<>();
        if (available == 0 || requestedCents == 0) {
            orderedSource.keySet().forEach(key -> removed.put(key, 0L));
            return Collections.unmodifiableMap(removed);
        }
        BigInteger denominator = BigInteger.valueOf(available);
        long distributed = 0;
        for (Map.Entry<K, Long> entry : orderedSource.entrySet()) {
            long share = BigInteger.valueOf(entry.getValue()).multiply(BigInteger.valueOf(requestedCents))
                    .divide(denominator).longValueExact();
            removed.put(entry.getKey(), share);
            distributed = Math.addExact(distributed, share);
        }
        long remainder = requestedCents - distributed;
        for (Map.Entry<K, Long> entry : orderedSource.entrySet()) {
            if (remainder == 0) break;
            long share = removed.get(entry.getKey());
            if (share < entry.getValue()) {
                removed.put(entry.getKey(), share + 1);
                remainder--;
            }
        }
        if (remainder != 0 || total(removed.values()) != requestedCents) {
            throw new IllegalStateException("could not distribute proportional reagent split");
        }
        return Collections.unmodifiableMap(removed);
    }

    /** Returns the portion of a request that fits in nonnegative free capacity. */
    public static long admit(long requestedCents, long freeCapacityCents) {
        requireCents(requestedCents, "requestedCents");
        requireCents(freeCapacityCents, "freeCapacityCents");
        return Math.min(requestedCents, freeCapacityCents);
    }

    /** Scales cents by a finite nonnegative efficacy, flooring intentional fractional-cent loss. */
    public static long scale(long cents, double efficacy) {
        requireCents(cents, "cents");
        if (!Double.isFinite(efficacy) || efficacy < 0d) {
            throw new IllegalArgumentException("efficacy must be finite and nonnegative");
        }
        double scaled = Math.floor(cents * efficacy);
        if (!Double.isFinite(scaled) || scaled > MAX_CENTS) {
            throw new IllegalArgumentException("scaled quantity exceeds maximum reagent quantity");
        }
        return (long) scaled;
    }

    private static long requireCents(long cents, String name) {
        if (cents < 0 || cents > MAX_CENTS) {
            throw new IllegalArgumentException(name + " must be between zero and " + MAX_CENTS);
        }
        return cents;
    }

    /** Validates a cent value and returns it unchanged. */
    public static long validateCents(long cents, String name) {
        return requireCents(cents, name);
    }
}
