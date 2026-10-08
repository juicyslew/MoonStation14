package com.juicyslew.moonstation14.ms14.doors.client;

import com.juicyslew.moonstation14.ms14.doors.AirlockState;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;

/** Pure per-controller mapping from synchronized presentation state to source sprite selection. */
final class AirlockFramePlanner {
    record Plan(String base, String glow, boolean glowVisible, boolean boltOverlay, boolean panelOverlay) { }

    private static final double[] FRAME_DELAYS = { .1, .1, .07, .07, .07, .2 };
    private static final double SOURCE_DURATION = .61;

    private AirlockFramePlanner() { }

    static Plan plan(AirlockState.DoorPhase phase, double elapsedSeconds,
                     double openOne, double openTwo, double closeOne, double closeTwo,
                     boolean powered, boolean boltOverlay, boolean panelOverlay) {
        String base;
        String glow;
        switch (phase) {
            case OPEN -> { base = "open"; glow = "open_unlit"; }
            case OPENING, OPENING_PASSABLE, CLOSING, CLOSING_PASSABLE -> {
                boolean opening = phase == AirlockState.DoorPhase.OPENING || phase == AirlockState.DoorPhase.OPENING_PASSABLE;
                double first = opening ? openOne : closeOne;
                double second = opening ? openTwo : closeTwo;
                double elapsed = elapsedSeconds;
                if (phase == AirlockState.DoorPhase.OPENING_PASSABLE || phase == AirlockState.DoorPhase.CLOSING_PASSABLE)
                    elapsed += first;
                double duration = first + second;
                double progress = duration > 0 ? Math.max(0, Math.min(1, elapsed / duration)) : 1;
                String sequence = opening ? "opening" : "closing";
                base = "frames/" + sequence + "_" + frame(progress);
                glow = "frames/" + sequence + "_unlit_" + frame(progress);
            }
            case DENYING -> { base = "closed"; glow = "closed_unlit"; }
            default -> { base = "closed"; glow = "closed_unlit"; }
        }
        return new Plan(base, glow, powered && phase != AirlockState.DoorPhase.OPEN && !boltOverlay,
                boltOverlay, panelOverlay);
    }

    static int frame(double progress) {
        double remaining = Math.max(0, Math.min(1, progress)) * SOURCE_DURATION;
        for (int i = 0; i < FRAME_DELAYS.length; i++) {
            remaining -= FRAME_DELAYS[i];
            if (remaining < 0) return i;
        }
        return FRAME_DELAYS.length - 1;
    }

    /** Transforms the source front normal through the same pose used for vertex positions. */
    static Vector3f frontNormal(PoseStack.Pose pose) {
        return pose.normal().transform(new Vector3f(0, 0, 1)).normalize();
    }
}
