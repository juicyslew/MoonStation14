package com.juicyslew.moonstation14.ms14.doors.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FirelockFramePlannerTest {
    @Test void selectedIndicatorEmissiveAdmissionPreservesPowerForDarkAndWarningIndependentOfPower() {
        for (boolean powered : new boolean[]{false, true}) {
            var darkState = new com.juicyslew.moonstation14.ms14.doors.FirelockState(
                    com.juicyslew.moonstation14.ms14.doors.AirlockState.DoorPhase.CLOSED, powered, false, false, false, 0, 0, 0);
            var dark = FirelockFramePlanner.plan(darkState, 0, true, 0, -1, -1);
            assertEquals(powered, FirelockFramePlanner.indicatorEmissive(darkState, dark));
            for (boolean pressure : new boolean[]{false, true}) for (boolean temperature : new boolean[]{false, true}) {
                if (!pressure && !temperature) continue;
                var warningState = new com.juicyslew.moonstation14.ms14.doors.FirelockState(
                        com.juicyslew.moonstation14.ms14.doors.AirlockState.DoorPhase.CLOSED, powered, false, pressure, temperature, 0, 0, 0);
                var warning = FirelockFramePlanner.plan(warningState, 0, true, 0, 0, 0);
                assertTrue(FirelockFramePlanner.indicatorEmissive(warningState, warning));
            }
            var moving = FirelockFramePlanner.plan(darkState, .5, true, 0, 0, 0);
            assertEquals(powered, FirelockFramePlanner.indicatorEmissive(darkState, moving));
            var open = FirelockFramePlanner.plan(darkState, 1, true, 0, 0, 0);
            assertEquals(powered, FirelockFramePlanner.indicatorEmissive(darkState, open));
        }
    }
    @Test void travelAndUnlatchedClosedRemainDarkAndOnlyLatchedZeroQCullsBase() {
        assertEquals(FirelockFramePlanner.Indicator.DARK, FirelockFramePlanner.indicator(true,.5,true,true,false,16,0,-1));
        assertEquals(FirelockFramePlanner.Indicator.DARK, FirelockFramePlanner.indicator(false,0,true,true,false,16,0,-1));
        assertTrue(FirelockRenderer.cullBaseAirlock(true,0));
        assertFalse(FirelockRenderer.cullBaseAirlock(false,0));
        assertFalse(FirelockRenderer.cullBaseAirlock(true,.001));
    }

    @Test void independentWarningClocksUseSharedSixteenPlusEightTickFrameCycle() {
        assertEquals(FirelockFramePlanner.Indicator.BOTH_P0_T0,
                FirelockFramePlanner.indicator(true,0,true,true,true,0,0,0));
        assertEquals(FirelockFramePlanner.Indicator.BOTH_P0_T0,
                FirelockFramePlanner.indicator(true,0,true,true,true,8,0,0));
        assertEquals(FirelockFramePlanner.Indicator.BOTH_P1_T1,
                FirelockFramePlanner.indicator(true,0,true,true,true,16,0,0));
        assertEquals(FirelockFramePlanner.Indicator.BOTH_P1_T1,
                FirelockFramePlanner.indicator(true,0,true,true,true,23,0,0));
        assertEquals(FirelockFramePlanner.Indicator.BOTH_P0_T0,
                FirelockFramePlanner.indicator(true,0,true,true,true,24,0,0));
        assertEquals(FirelockFramePlanner.Indicator.BOTH_P0_T1,
                FirelockFramePlanner.indicator(true,0,true,true,true,16,8,0));
        assertEquals(FirelockFramePlanner.Indicator.BOTH_P0_T1,
                FirelockFramePlanner.indicator(true,0,true,true,true,8,0,16));
        assertEquals(FirelockFramePlanner.Indicator.PRESSURE_PHASE0,
                FirelockFramePlanner.indicator(true,0,true,true,false,15,0,-1));
        assertEquals(FirelockFramePlanner.Indicator.PRESSURE_PHASE1,
                FirelockFramePlanner.indicator(true,0,true,true,false,16,0,-1));
        assertEquals(FirelockFramePlanner.Indicator.TEMPERATURE_PHASE1,
                FirelockFramePlanner.indicator(true,0,true,false,true,23,0,0));
        assertEquals(FirelockFramePlanner.Indicator.TEMPERATURE_PHASE0,
                FirelockFramePlanner.indicator(true,0,true,false,true,24,0,0));
        assertEquals(FirelockFramePlanner.Indicator.PRESSURE_LIT,
                FirelockFramePlanner.indicator(true,0,true,true,false,16,-1,-1));
        assertEquals(FirelockFramePlanner.Indicator.PRESSURE_PHASE1,
                FirelockFramePlanner.indicator(true,0,false,true,false,16,0,-1));
        assertEquals(FirelockFramePlanner.Indicator.TEMPERATURE_PHASE0,
                FirelockFramePlanner.indicator(true,0,false,false,true,0,0,0));
        assertEquals(FirelockFramePlanner.Indicator.DARK,
                FirelockFramePlanner.indicator(true,0,false,false,false,16,-1,-1));
    }

    @Test void r4TravelIsOneFullBlockAtQEndpoints() {
        for (double q : new double[]{0,.5,1}) {
            FirelockFramePlanner.Travel travel = FirelockFramePlanner.travel(q);
            assertEquals(-q, travel.lowerY(), 0);
            assertEquals(q, travel.upperY(), 0);
            assertEquals(q, travel.indicatorY(), 0);
        }
    }

    @Test void onlyCanonicalFullyOpenPlanOmitsCoverDraws() {
        var state = new com.juicyslew.moonstation14.ms14.doors.FirelockState(
                com.juicyslew.moonstation14.ms14.doors.AirlockState.DoorPhase.OPEN,
                true, false, false, false, 0, 0, 0);
        for (double q : new double[]{0, .5, Math.nextDown(1.0), 1.0}) {
            var plan = FirelockFramePlanner.plan(state, q, false, 0, -1, -1);
            assertNotNull(plan);
            assertEquals(q != 1.0, plan.drawCover(), "cover visibility at openness " + q);
            assertEquals(q, plan.openness(), 0);
            assertEquals(q, plan.travel().upperY(), 0);
            assertEquals(-q, plan.travel().lowerY(), 0);
            assertEquals(q, plan.travel().indicatorY(), 0);
        }
        // At q=1 the authored leaves coincide with the clipping planes; do not let
        // inclusive boundary clipping leak those zero-area remnants into rendering.
        assertFalse(FirelockFramePlanner.plan(state, 1, false, 0, -1, -1).drawCover());
        assertFalse(FirelockRenderer.cullBaseAirlock(false, 1));
        assertTrue(FirelockRenderer.cullBaseAirlock(true, 0));
        assertFalse(FirelockRenderer.cullBaseAirlock(true, .5));
    }

    @Test void firelockRenderBoundsPadBothHorizontalAxesForEveryFacing() {
        var pos = new net.minecraft.core.BlockPos(7,12,-3);
        var bounds = FirelockRenderer.renderBounds(pos);
        assertEquals(7-.25/16,bounds.minX,1e-9);
        assertEquals(8+.25/16,bounds.maxX,1e-9);
        assertEquals(-3-.25/16,bounds.minZ,1e-9);
        assertEquals(-2+.25/16,bounds.maxZ,1e-9);
        assertEquals(12,bounds.minY,1e-9);
        assertEquals(14,bounds.maxY,1e-9);
    }
}
