package com.juicyslew.moonstation14.ms14.construction.client;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConstructionGuidanceDwellTest {
    @Test void productionAdmissionAllowsUnfocusedOverlayButRejectsSearchAndModalScreens() {
        assertTrue(ConstructionGuidanceAdmission.allowed(false,true,true,false));
        assertFalse(ConstructionGuidanceAdmission.allowed(false,true,true,true));
        assertFalse(ConstructionGuidanceAdmission.allowed(false,true,false,false));
        assertFalse(ConstructionGuidanceAdmission.allowed(true,false,false,false));
    }
    private static ConstructionGuidanceDwell.Target target(int x, long epoch) {
        return new ConstructionGuidanceDwell.Target(Level.OVERWORLD, new BlockPos(x, 64, 0), UUID.fromString("00000000-0000-0000-0000-000000000001"), epoch);
    }

    @Test void requiresContinuousDwellAndRearmsOnAnyTargetIdentityChange() {
        assertEquals(500_000_000L, ConstructionGuidanceDwell.DWELL_NANOS);
        var dwell = new ConstructionGuidanceDwell();
        var a = target(1, 1);
        assertFalse(dwell.update(a, 0));
        assertFalse(dwell.update(a, ConstructionGuidanceDwell.DWELL_NANOS - 1));
        assertTrue(dwell.update(a, ConstructionGuidanceDwell.DWELL_NANOS));
        assertFalse(dwell.update(a, ConstructionGuidanceDwell.DWELL_NANOS + 1));

        assertFalse(dwell.update(target(2, 1), 10_000_000_000L));
        assertFalse(dwell.update(target(2, 1), 10_000_000_000L + ConstructionGuidanceDwell.DWELL_NANOS - 1));
        assertTrue(dwell.update(target(2, 1), 10_000_000_000L + ConstructionGuidanceDwell.DWELL_NANOS));
        assertFalse(dwell.update(target(2, 2), 20_000_000_000L));
    }

    @Test void guidanceMetricsKeepWrappedRowsInsidePanelWithBorderClearance() {
        for (int rows = 1; rows <= ConstructionHudRenderer.GUIDANCE_MAX_LINES; rows++) {
            int physicalLineHeight = ConstructionHudRenderer.guidancePhysicalLineHeight(9);
            assertEquals(5, physicalLineHeight);
            var metrics = ConstructionHudRenderer.guidanceMetrics(physicalLineHeight, rows);
            assertEquals(6, metrics.rowStride());
            int lastGlyphBottom = metrics.bodyStart() + (rows - 1) * metrics.rowStride() + 5;
            assertTrue(lastGlyphBottom <= metrics.panelHeight() - 2,
                    "row " + rows + " must clear the lower border");
            assertTrue(metrics.bodyStart() > metrics.dividerY());
        }
    }

    @Test void guidanceFontSplitRowsFeedTheSameProductionMetrics() {
        var font = new MachineSearchWidgetTest.DeterministicFont() {
            @Override public java.util.List<net.minecraft.util.FormattedCharSequence> split(net.minecraft.network.chat.FormattedText text, int width) {
                return java.util.List.of(net.minecraft.util.FormattedCharSequence.EMPTY, net.minecraft.util.FormattedCharSequence.EMPTY,
                        net.minecraft.util.FormattedCharSequence.EMPTY);
            }
        };
        var wrapped = ConstructionHudRenderer.wrap(font,
                new net.minecraft.network.chat.Component[]{net.minecraft.network.chat.Component.literal("synthetic long requirement")}, 20, 4);
        assertEquals(3, wrapped.size());
        var metrics = ConstructionHudRenderer.guidanceMetrics(
                ConstructionHudRenderer.guidancePhysicalLineHeight(9), wrapped.size());
        assertEquals(30, metrics.panelHeight());
    }

    @Test void lookingAwayResetsAndLateRepliesMustMatchCurrentTarget() {
        var dwell = new ConstructionGuidanceDwell();
        var a = target(1, 1);
        dwell.update(a, 100);
        assertFalse(dwell.update(null, 200));
        assertFalse(dwell.update(a, 300));
        assertFalse(dwell.accepts(a, null));
        assertFalse(dwell.accepts(a, target(1, 2)));
        assertTrue(dwell.accepts(a, a));
    }
}
