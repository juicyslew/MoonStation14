package com.juicyslew.moonstation14.ms14.atmos.visual;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GasVisibilityTest {
    @Test
    void onlyTheFiveSpriteGasesHaveOverlays() {
        assertTrue(GasVisibility.hasOverlay(GasType.PLASMA));
        assertTrue(GasVisibility.hasOverlay(GasType.TRITIUM));
        assertTrue(GasVisibility.hasOverlay(GasType.WATER_VAPOR));
        assertTrue(GasVisibility.hasOverlay(GasType.AMMONIA));
        assertTrue(GasVisibility.hasOverlay(GasType.FREZON));
        assertFalse(GasVisibility.hasOverlay(GasType.OXYGEN));
        assertFalse(GasVisibility.hasOverlay(GasType.NITROGEN));
        assertFalse(GasVisibility.hasOverlay(GasType.CARBON_DIOXIDE));
        assertFalse(GasVisibility.hasOverlay(GasType.NITROUS_OXIDE));
        assertTrue(GasVisibility.tint(GasType.PLASMA).isPresent());
        assertTrue(GasVisibility.tint(GasType.NITROUS_OXIDE).isEmpty());
    }

    @Test
    void defaultGasThresholdAndFortyMoleLocalSaturationAreHandled() {
        assertEquals(0, GasVisibility.alphaByte(GasType.PLASMA, 0.249999, 2.5));
        assertEquals(0, GasVisibility.alphaByte(GasType.PLASMA, 0.25, 2.5));
        assertEquals(13, GasVisibility.alphaByte(GasType.PLASMA, 0.251, 2.5));
        assertEquals(255, GasVisibility.alphaByte(GasType.PLASMA, 100.0, 2.5));
        assertEquals(0, GasVisibility.alphaByte(GasType.NITROUS_OXIDE, 100.0, 2.5));
    }

    @Test
    void tritiumUsesFortyMolesPerBlockWithFaintOnsetAndMonotoneSaturation() {
        double[] amounts = {0.0, 0.1, 1.0, 10.0, 20.0, 39.9, 40.0, 50.0};
        int previous = 0;
        int[] expected = {0, 0, 13, 67, 120, 255, 255, 255};
        for (int i = 0; i < amounts.length; i++) {
            int alpha = GasVisibility.alphaByte(GasType.TRITIUM, amounts[i], 1.0);
            assertEquals(expected[i], alpha, "moles=" + amounts[i]);
            assertTrue(alpha >= previous, "alpha must be monotone at " + amounts[i] + " moles");
            previous = alpha;
        }
        int faintColumnPlane = GasVisibility.perPlaneAlphaByte(
                GasVisibility.alphaByte(GasType.TRITIUM, 1.0, 1.0), 3, 4);
        assertTrue(faintColumnPlane > 0);
        assertTrue(faintColumnPlane < 10);
    }

    @Test
    void ammoniaAndFrezonKeepTheirStartConcentrationsAndShareTheFortyMoleMaximum() {
        assertEquals(0, GasVisibility.alphaByte(GasType.AMMONIA, 0.8, 1.0));
        assertEquals(13, GasVisibility.alphaByte(GasType.AMMONIA, 0.801, 1.0));
        assertEquals(0, GasVisibility.alphaByte(GasType.FREZON, 0.24, 1.0));
        assertEquals(13, GasVisibility.alphaByte(GasType.FREZON, 0.241, 1.0));
        assertEquals(255, GasVisibility.alphaByte(GasType.AMMONIA, 40.0, 1.0));
        assertEquals(255, GasVisibility.alphaByte(GasType.FREZON, 40.0, 1.0));
        assertEquals(new GasVisibility.Rgb(0x56, 0x94, 0x1E), GasVisibility.tint(GasType.AMMONIA).orElseThrow());
        assertEquals(new GasVisibility.Rgb(0x3A, 0x75, 0x8C), GasVisibility.tint(GasType.FREZON).orElseThrow());
    }

    @Test
    void thresholdsAndFortyMoleMaximumScaleWithCellVolume() {
        assertEquals(0, GasVisibility.alphaByte(GasType.PLASMA, 0.1, 1.0));
        assertEquals(255, GasVisibility.alphaByte(GasType.PLASMA, 100.0, 2.5));
        assertEquals(GasVisibility.alphaByte(GasType.PLASMA, 100.0, 2.5),
                GasVisibility.alphaByte(GasType.PLASMA, 40.0, 1.0));
        assertEquals(0, GasVisibility.alphaByte(GasType.AMMONIA, 0.8, 1.0));
        assertEquals(0, GasVisibility.alphaByte(GasType.FREZON, 0.24, 1.0));
    }

    @Test
    void alphaLevelsAreQuantizedMonotonicallyAndTinyAmountsDoNotFlash() {
        int previous = 0;
        boolean[] levelsSeen = new boolean[GasVisibility.QUANTIZATION_LEVELS];
        for (int step = 0; step <= 100; step++) {
            int alpha = GasVisibility.alphaByte(GasType.PLASMA, 0.1 + 39.9 * step / 100.0, 1.0);
            assertTrue(alpha >= previous);
            assertTrue(alpha >= 0 && alpha <= 255);
            if (alpha > 0) {
                levelsSeen[(int) Math.round(alpha / 255.0 * (GasVisibility.QUANTIZATION_LEVELS - 1))] = true;
            }
            previous = alpha;
        }
        assertEquals(20, GasVisibility.QUANTIZATION_LEVELS);
        for (int level = 1; level < levelsSeen.length; level++) {
            assertTrue(levelsSeen[level], "quantized alpha level " + level + " should occur");
        }
        assertEquals(13, GasVisibility.alphaByte(GasType.PLASMA, Math.nextUp(0.25), 2.5));
        assertTrue(GasVisibility.overlay(GasType.PLASMA, 0.0, 2.5).isEmpty());
        assertTrue(GasVisibility.overlay(GasType.PLASMA, Math.nextUp(0.25), 2.5).isPresent());
    }

    @Test
    void perPlaneAlphaApproximatesColumnOpacityAndQuadRadiusNeverCrossesCellFaces() {
        int lowTritiumAlpha = GasVisibility.alphaByte(GasType.TRITIUM, 0.5, 1.0);
        int lowPerPlane = GasVisibility.perPlaneAlphaByte(lowTritiumAlpha, 3, 4);
        assertTrue(lowPerPlane > 0 && lowPerPlane < 10, "low tritium remains visible without becoming opaque");
        assertEquals(0, GasVisibility.perPlaneAlphaByte(0, 3, 4));
        assertTrue(GasVisibility.perPlaneAlphaByte(255, 3, 4) > lowPerPlane);
        assertTrue(GasVisibility.perPlaneAlphaByte(255, 3, 4) <= 16);
        int previous = 0;
        for (int alpha = 0; alpha <= 255; alpha++) {
            int perPlane = GasVisibility.perPlaneAlphaByte(alpha, 3, 4);
            assertTrue(perPlane >= previous);
            assertTrue(perPlane <= 255);
            assertTrue(GasVisibility.quadRadius(alpha) <= .48F);
            previous = perPlane;
        }
        assertEquals(.43F, GasVisibility.quadRadius(0));
        assertEquals(.48F, GasVisibility.quadRadius(255));
    }

    @Test
    void overlayResultsOnlyContainPresentNonzeroGasesAndPackFiveBytes() {
        Map<GasType, Double> amounts = new HashMap<>();
        amounts.put(GasType.PLASMA, 100.0);
        amounts.put(GasType.AMMONIA, 100.0);
        amounts.put(GasType.OXYGEN, 100.0);
        Map<GasType, GasVisibility.Overlay> visible = GasVisibility.overlays(amounts, 2.5);

        assertEquals(2, visible.size());
        assertEquals(255, visible.get(GasType.PLASMA).alphaByte());
        assertFalse(visible.containsKey(GasType.OXYGEN));
        assertThrows(UnsupportedOperationException.class,
                () -> visible.put(GasType.FREZON, new GasVisibility.Overlay(
                        GasType.FREZON, GasVisibility.tint(GasType.FREZON).orElseThrow(), 1)));
        assertEquals(0x00_FF_00_00_FFL, GasVisibility.packVisualChannels(amounts, 2.5));
        assertEquals(0L, GasVisibility.packVisualChannels(Map.of(), 2.5));
        assertEquals(100.0, amounts.get(GasType.OXYGEN));
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> GasVisibility.alphaByte(GasType.PLASMA, -1.0, 2.5));
        assertThrows(IllegalArgumentException.class, () -> GasVisibility.alphaByte(GasType.PLASMA, Double.NaN, 2.5));
        assertThrows(IllegalArgumentException.class, () -> GasVisibility.alphaByte(GasType.PLASMA, Double.POSITIVE_INFINITY, 2.5));
        assertThrows(IllegalArgumentException.class, () -> GasVisibility.alphaByte(GasType.PLASMA, 1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> GasVisibility.alphaByte(GasType.PLASMA, 1.0, Double.POSITIVE_INFINITY));
        assertThrows(NullPointerException.class, () -> GasVisibility.alphaByte(null, 1.0, 2.5));
        assertThrows(NullPointerException.class, () -> GasVisibility.overlays(null, 2.5));
    }
}
