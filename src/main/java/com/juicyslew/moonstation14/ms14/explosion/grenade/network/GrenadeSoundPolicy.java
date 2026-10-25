package com.juicyslew.moonstation14.ms14.explosion.grenade.network;

import net.minecraft.resources.ResourceLocation;

/** Pure, mutually exclusive camera-distance selection for a single explosion and listener. */
public final class GrenadeSoundPolicy {
    public static final double NEAR_RADIUS = 32.0;
    public static final double FAR_RADIUS = 96.0;
    public static final float NEAR_GAIN = 0.5f;
    public static final float FAR_GAIN = 0.25f;

    private GrenadeSoundPolicy() { }

    public enum Mode { NONE, NEAR, FAR }
    public record Decision(Mode mode, float gain) { }

    public static Decision classify(ResourceLocation blastDimension, ResourceLocation cameraDimension,
                                    double blastX, double blastY, double blastZ,
                                    double cameraX, double cameraY, double cameraZ) {
        Decision none = new Decision(Mode.NONE, 0);
        if (blastDimension == null || !blastDimension.equals(cameraDimension)
                || !Double.isFinite(blastX) || !Double.isFinite(blastY) || !Double.isFinite(blastZ)
                || !Double.isFinite(cameraX) || !Double.isFinite(cameraY) || !Double.isFinite(cameraZ)) return none;
        double distance = Math.hypot(Math.hypot(blastX - cameraX, blastY - cameraY), blastZ - cameraZ);
        if (distance <= NEAR_RADIUS) return new Decision(Mode.NEAR, NEAR_GAIN);
        // The endpoint has zero gain, so omit its packet rather than asking OpenAL to play silence.
        if (distance >= FAR_RADIUS) return none;
        double fraction = (FAR_RADIUS - distance) / (FAR_RADIUS - NEAR_RADIUS);
        return new Decision(Mode.FAR, (float) (FAR_GAIN * fraction * fraction));
    }
}

