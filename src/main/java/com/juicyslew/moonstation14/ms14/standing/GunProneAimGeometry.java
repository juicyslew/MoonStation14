package com.juicyslew.moonstation14.ms14.standing;

/** Shared Minecraft model-unit geometry for the supported prone gun stance. */
public final class GunProneAimGeometry {
    public static final double TORSO_RADIANS = .4;
    public static final double TORSO_LENGTH_PIXELS = 12;
    public static final double NECK_RISE_PIXELS = TORSO_LENGTH_PIXELS * Math.sin(TORSO_RADIANS);
    public static final double EYE_EXTRA_PIXELS = 4;
    public static final double HEAD_HEIGHT_PIXELS = 8;
    public static final float EYE_HEIGHT = (float)(.125 + (NECK_RISE_PIXELS + EYE_EXTRA_PIXELS) / 16);
    public static final float HEIGHT = (float)(.125 + (NECK_RISE_PIXELS + HEAD_HEIGHT_PIXELS + 1) / 16);
    private GunProneAimGeometry() { }
}
