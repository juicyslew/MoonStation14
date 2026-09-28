package com.juicyslew.moonstation14.ms14.atmos.visual;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure gas-overlay visibility policy adapted from SS14's gas prototypes and
 * {@code GasTileOverlaySystem.GetOpacity} (lines 163-200). SS14 renders white
 * overlay sprites with alpha; {@link Rgb} uses each prototype's UI color as a
 * Minecraft tint approximation, not as a copied SS14 rendering color.
 *
 * <p>Reference sources: {@code Resources/Prototypes/Atmospherics/gases.yml}
 * and {@code Content.Server/Atmos/EntitySystems/GasTileOverlaySystem.cs}.
 * SS14 visibility thresholds and gasOverlaySprite maxima are specified for a
 * 2.5 m^3 tile and both scale by cell volume: 5 moles for the default gases,
 * 7 for ammonia, and 12 for frezon at the reference volume. This preserves the
 * upstream volume-scaled visibility amounts.</p>
 *
 * <p>This opacity policy does not model ignition. For example, SS14's
 * {@code Resources/Prototypes/Atmospherics/reactions.yml} defines the
 * {@code TritiumFire} reaction with a 0.01 mole tritium and 0.01 mole oxygen
 * minimum at 373.149 K per 2.5 m^3 cell; a 1 mole Minecraft block corresponds
 * to 2.5 moles per SS14 tile by concentration, but reaction behavior also
 * depends on heat and oxygen.</p>
 */
public final class GasVisibility {
    public static final double REFERENCE_VOLUME_CUBIC_METERS = 2.5;
    public static final int QUANTIZATION_LEVELS = 20;

    /** Byte order used by {@link #packVisualChannels(Map, double)} (least significant first). */
    private static final List<GasType> VISIBLE_GASES = List.of(
            GasType.PLASMA, GasType.TRITIUM, GasType.WATER_VAPOR, GasType.AMMONIA, GasType.FREZON);

    private GasVisibility() {
    }

    /** RGB tint approximation based on the gas prototype's UI color. */
    public record Rgb(int red, int green, int blue) {
    }

    /** A visible gas and its Minecraft tint/quantized opacity. */
    public record Overlay(GasType gas, Rgb tint, int alphaByte) {
    }

    /** True only for the five gases with an SS14 gasOverlaySprite. */
    public static boolean hasOverlay(GasType gas) {
        Objects.requireNonNull(gas, "gas");
        return gas == GasType.PLASMA || gas == GasType.TRITIUM || gas == GasType.WATER_VAPOR
                || gas == GasType.AMMONIA || gas == GasType.FREZON;
    }

    /**
     * Returns prototype UI colors as RGB tint approximations. Non-overlay gases
     * have no tint and cause an empty result.
     */
    public static Optional<Rgb> tint(GasType gas) {
        Objects.requireNonNull(gas, "gas");
        return switch (gas) {
            case PLASMA -> Optional.of(new Rgb(0xFF, 0x33, 0x00));
            case TRITIUM -> Optional.of(new Rgb(0x13, 0xFF, 0x4B));
            case WATER_VAPOR -> Optional.of(new Rgb(0xBF, 0xFF, 0xFD));
            case AMMONIA -> Optional.of(new Rgb(0x56, 0x94, 0x1E));
            case FREZON -> Optional.of(new Rgb(0x3A, 0x75, 0x8C));
            default -> Optional.empty();
        };
    }

    /**
     * Computes an opacity byte for an amount of gas in a cell. The amount and
     * volume must be finite and nonnegative (volume must be greater than zero).
     * Amounts at or below the visible threshold return zero. Amounts above the
     * threshold use the original rounded 20-level quantization, which can still
     * yield zero until the first quantization boundary is crossed.
     */
    public static int alphaByte(GasType gas, double moles, double cellVolumeCubicMeters) {
        Objects.requireNonNull(gas, "gas");
        requireFiniteNonNegative(moles, "moles");
        requireFiniteNonNegative(cellVolumeCubicMeters, "cellVolumeCubicMeters");
        if (cellVolumeCubicMeters == 0.0) {
            throw new IllegalArgumentException("cellVolumeCubicMeters must be greater than zero");
        }
        if (!hasOverlay(gas)) {
            return 0;
        }

        double threshold = thresholdMoles(gas) * cellVolumeCubicMeters / REFERENCE_VOLUME_CUBIC_METERS;
        double maximum = maximumMoles(gas) * cellVolumeCubicMeters / REFERENCE_VOLUME_CUBIC_METERS;
        if (moles <= threshold) {
            return 0;
        }
        double normalized = Math.max(0.0, Math.min(1.0, (moles - threshold) / (maximum - threshold)));
        int level = (int) Math.round(normalized * (QUANTIZATION_LEVELS - 1));
        return level * 255 / (QUANTIZATION_LEVELS - 1);
    }

    /** Converts the SS14 tile alpha into a per-plane alpha for a multi-plane Minecraft column. */
    public static int perPlaneAlphaByte(int ss14Alpha, int planes, int columnHeight) {
        if (ss14Alpha < 0 || ss14Alpha > 255) throw new IllegalArgumentException("ss14Alpha must be in [0,255]");
        if (planes <= 0) throw new IllegalArgumentException("planes must be positive");
        if (columnHeight <= 0) throw new IllegalArgumentException("columnHeight must be positive");
        if (ss14Alpha == 0) return 0;
        double normalized = ss14Alpha / 255.0;
        double columnOpacity = Math.min(0.85 * normalized, 0.85);
        double perCellOpacity = 1.0 - Math.pow(1.0 - columnOpacity, 1.0 / columnHeight);
        double perPlaneOpacity = 1.0 - Math.pow(1.0 - perCellOpacity, 1.0 / planes);
        return Math.max(1, Math.min(255, (int) Math.round(perPlaneOpacity * 255)));
    }

    /** Keeps crossed planes inset from all six cell faces to prevent wall bleed. */
    public static float quadRadius(int alpha) {
        if (alpha < 0 || alpha > 255) throw new IllegalArgumentException("alpha must be in [0,255]");
        return Math.min(0.48F, .43F + alpha / 255F * .12F);
    }

    /** Returns the overlay only when the gas is present and its quantized opacity is nonzero. */
    public static Optional<Overlay> overlay(GasType gas, double moles, double cellVolumeCubicMeters) {
        int alpha = alphaByte(gas, moles, cellVolumeCubicMeters);
        if (moles == 0.0 || alpha == 0) {
            return Optional.empty();
        }
        return Optional.of(new Overlay(gas, tint(gas).orElseThrow(), alpha));
    }

    /**
     * Produces only present, nonzero overlays; the returned map is immutable
     * and does not retain or mutate the supplied amounts map.
     */
    public static Map<GasType, Overlay> overlays(Map<GasType, Double> molesByGas,
                                                 double cellVolumeCubicMeters) {
        Objects.requireNonNull(molesByGas, "molesByGas");
        EnumMap<GasType, Overlay> result = new EnumMap<>(GasType.class);
        for (GasType gas : VISIBLE_GASES) {
            Double moles = molesByGas.get(gas);
            if (moles != null) {
                overlay(gas, moles, cellVolumeCubicMeters).ifPresent(value -> result.put(gas, value));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Packs five alpha bytes into a 40-bit long in {@link #VISIBLE_GASES}
     * order, with plasma in the least-significant byte. Missing/invisible
     * gases contribute zero.
     */
    public static long packVisualChannels(Map<GasType, Double> molesByGas, double cellVolumeCubicMeters) {
        Map<GasType, Overlay> visible = overlays(molesByGas, cellVolumeCubicMeters);
        long packed = 0L;
        for (int i = 0; i < VISIBLE_GASES.size(); i++) {
            Overlay overlay = visible.get(VISIBLE_GASES.get(i));
            if (overlay != null) {
                packed |= (long) overlay.alphaByte() << (i * Byte.SIZE);
            }
        }
        return packed;
    }

    private static double thresholdMoles(GasType gas) {
        return switch (gas) {
            case AMMONIA -> 2.0;
            case FREZON -> 0.6;
            default -> 0.25;
        };
    }

    private static double maximumMoles(GasType gas) {
        return switch (gas) {
            case AMMONIA -> 7.0;
            case FREZON -> 12.0;
            default -> 5.0;
        };
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and nonnegative");
        }
    }
}
