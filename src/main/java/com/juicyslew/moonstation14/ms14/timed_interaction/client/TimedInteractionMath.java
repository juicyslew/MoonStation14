package com.juicyslew.moonstation14.ms14.timed_interaction.client;

import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;

/** Pure presentation geometry/timing shared by the renderer and focused tests. */
public final class TimedInteractionMath {
    public static final int WIDTH=24, HEIGHT=7, STACK_PIXELS=7;
    public static final double FIRST_PERSON_DISTANCE=.25, FIRST_PERSON_BOTTOM_FRACTION=.70, ACTOR_BAR_CLEARANCE=.30;
    private static final double FALLBACK_PROJECTION_Y_SCALE=1.0/Math.tan(Math.toRadians(35));
    public static final float ACTOR_SCALE=1f/32f, FIRST_PERSON_SCALE=ACTOR_SCALE/12f;
    public static final int FILL_LEFT=-10, FILL_TOP=0, FILL_HEIGHT=1;
    private TimedInteractionMath() { }
    public static float progress(long start, int duration, double estimatedTick) {
        if (duration <= 0) return 0;
        return (float) Math.max(0, Math.min(1, (estimatedTick-start)/(float)duration));
    }
    public static int fillPixels(float progress) { return Math.max(0, Math.min(20, (int)(20*Math.max(0,Math.min(1,progress))))); }
    public static int stackOffset(int index) { return Math.max(0,index)*STACK_PIXELS; }
    /** World-space vertical half extent of the view at the billboard anchor depth. */
    public static double firstPersonVerticalHalfExtent(double projectionYScale) {
        double scale=Double.isFinite(projectionYScale)&&Math.abs(projectionYScale)>1e-6
                ?Math.abs(projectionYScale):FALLBACK_PROJECTION_Y_SCALE;
        return FIRST_PERSON_DISTANCE/scale;
    }
    public static double firstPersonVerticalOffset(double projectionYScale) {
        return -firstPersonVerticalHalfExtent(projectionYScale)*FIRST_PERSON_BOTTOM_FRACTION;
    }
    /** Camera-relative world anchor for the controlled actor's first-person billboard. */
    public static net.minecraft.world.phys.Vec3 firstPersonAnchor(net.minecraft.world.phys.Vec3 camera,
            net.minecraft.world.phys.Vec3 forward, net.minecraft.world.phys.Vec3 up, int index,double projectionYScale) {
        double stack=stackOffset(index)*FIRST_PERSON_SCALE;
        return camera.add(forward.scale(FIRST_PERSON_DISTANCE)).add(up.scale(firstPersonVerticalOffset(projectionYScale)+stack));
    }
    public static int color(float progress, TimedOperation.Phase phase) {
        if (phase == TimedOperation.Phase.CANCELLED) return java.awt.Color.HSBtoRGB(0f,1f,.75f)|0xff000000;
        if (phase == TimedOperation.Phase.COMPLETED || progress >= 1f) return 0xff00ff00;
        float hue=(5f/18f)*Math.max(0,Math.min(1,progress));
        return java.awt.Color.HSBtoRGB(hue,1f,.75f)|0xff000000;
    }

    /** Cancellation fill flashes on initially, alternating every 2.5 ticks (0.125 seconds). */
    public static float cancellationFillAlpha(double terminalTick, double estimatedTick) {
        double elapsed=Math.max(0,estimatedTick-terminalTick);
        return elapsed%5.0<2.5?1f:0f;
    }

}
