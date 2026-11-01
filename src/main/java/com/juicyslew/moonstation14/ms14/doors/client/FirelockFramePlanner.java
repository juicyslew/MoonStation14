package com.juicyslew.moonstation14.ms14.doors.client;

import com.juicyslew.moonstation14.ms14.doors.AirlockState;
import com.juicyslew.moonstation14.ms14.doors.FirelockController;
import com.juicyslew.moonstation14.ms14.doors.FirelockMotion;
import com.juicyslew.moonstation14.ms14.doors.prototype.FirelockData;

/** Chooses the single authored indicator overlay for a firelock snapshot. */
public final class FirelockFramePlanner {
    public record Travel(double lowerY, double upperY, double indicatorY) { }
    public record PresentationPlan(double openness,boolean cullBaseAirlock,boolean drawCover,Travel travel,Indicator indicator) { }
    public enum Indicator { DARK, PRESSURE_LIT, TEMPERATURE_LIT, BOTH_LIT, PRESSURE_PHASE0, PRESSURE_PHASE1,
        TEMPERATURE_PHASE0, TEMPERATURE_PHASE1, BOTH_P0_T0, BOTH_P0_T1, BOTH_P1_T0, BOTH_P1_T1 }
    private FirelockFramePlanner() { }
    /** The exact pure plan consumed by the attached renderer: motion, cull eligibility and warning overlay. */
    public static PresentationPlan plan(FirelockController.Presentation view,FirelockData data,double doubleTick){
        if(view==null||data==null||!Double.isFinite(doubleTick))return null;
        double q=FirelockMotion.openness(view,data,doubleTick);long now=(long)Math.floor(doubleTick);
        return plan(view.state(),q,view.authoritativeLatch(),now,view.pressureWarningStartedAt(),view.temperatureWarningStartedAt());
    }
    public static PresentationPlan plan(com.juicyslew.moonstation14.ms14.doors.FirelockState state,double q,boolean latchedClosed,
                                        long now,long pressureStartedAt,long temperatureStartedAt){
        if(state==null||!Double.isFinite(q))return null;q=Math.max(0,Math.min(1,q));
        Indicator selected=indicator(latchedClosed,q,state.powered(),state.pressureWarning(),state.temperatureWarning(),now,pressureStartedAt,temperatureStartedAt);
        return new PresentationPlan(q,FirelockRenderer.cullBaseAirlock(latchedClosed,q),q != 1.0,travel(q),selected);
    }
    public static Travel travel(double q) {
        if (!Double.isFinite(q)) throw new IllegalArgumentException("q must be finite");
        q = Math.max(0, Math.min(1, q));
        return new Travel(-q, q, q);
    }

    /** Indicator components are unshaded for an active warning, regardless of APC power. */
    public static boolean indicatorEmissive(com.juicyslew.moonstation14.ms14.doors.FirelockState state,
                                             PresentationPlan plan) {
        return state != null && (state.powered() || (plan != null && plan.indicator() != Indicator.DARK));
    }

    public static Indicator indicator(boolean latchedClosed, double q, boolean powered, boolean pressureWarning,
                                      boolean temperatureWarning, long now, long pressureStartedAt,
                                      long temperatureStartedAt) {
        if (!latchedClosed || q != 0.0) return Indicator.DARK;
        // Upstream warning overlays and their point light remain active while unpowered;
        // power gates door/alarm response, not the closed warning presentation.
        if (pressureWarning && temperatureWarning) {
            if (pressureStartedAt < 0 || temperatureStartedAt < 0) return Indicator.BOTH_LIT;
            int p = warningFramePhase(now, pressureStartedAt);
            int t = warningFramePhase(now, temperatureStartedAt);
            return switch (p * 2 + t) { case 0 -> Indicator.BOTH_P0_T0; case 1 -> Indicator.BOTH_P0_T1;
                case 2 -> Indicator.BOTH_P1_T0; default -> Indicator.BOTH_P1_T1; };
        }
        if (pressureWarning) return phase(now, pressureStartedAt, true);
        if (temperatureWarning) return phase(now, temperatureStartedAt, false);
        return Indicator.DARK;
    }

    /** SS14 pressure_unlit/temperature_unlit share an 0.8s + 0.4s (16 + 8 tick) cycle. */
    private static int warningFramePhase(long now, long started) {
        long tick = Math.floorMod(now - started, 24);
        return tick < 16 ? 0 : 1;
    }

    private static Indicator phase(long now, long started, boolean pressure) {
        if (started < 0) return pressure ? Indicator.PRESSURE_LIT : Indicator.TEMPERATURE_LIT;
        boolean phase = warningFramePhase(now, started) != 0;
        return pressure ? (phase ? Indicator.PRESSURE_PHASE1 : Indicator.PRESSURE_PHASE0)
                : (phase ? Indicator.TEMPERATURE_PHASE1 : Indicator.TEMPERATURE_PHASE0);
    }
}
