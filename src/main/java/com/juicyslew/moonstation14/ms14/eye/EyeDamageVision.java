package com.juicyslew.moonstation14.ms14.eye;

/** Pure threshold fog projection; it never owns or installs a vanilla effect. */
public final class EyeDamageVision {
    public static final float BLIND_FOG_FAR_PLANE = 5f;

    private EyeDamageVision() {
    }

    /** Tightens existing fog only, preserving any more restrictive environment fog. */
    public static FogPlanes tightenFog(float vanillaNearPlane, float vanillaFarPlane, boolean blind) {
        if (!Float.isFinite(vanillaNearPlane) || !Float.isFinite(vanillaFarPlane) || !blind) {
            return new FogPlanes(vanillaNearPlane, vanillaFarPlane);
        }
        return new FogPlanes(Math.min(vanillaNearPlane, 0f),
                Math.min(vanillaFarPlane, BLIND_FOG_FAR_PLANE));
    }

    /**
     * Decides whether this projection changed the event and therefore needs
     * cancellation for NeoForge's RenderFog values to take effect.
     */
    public static FogProjection projectFog(float currentNearPlane, float currentFarPlane,
                                           boolean blind, boolean alreadyCanceled) {
        FogPlanes planes = tightenFog(currentNearPlane, currentFarPlane, blind);
        boolean changed = Float.compare(planes.nearPlane(), currentNearPlane) != 0
                || Float.compare(planes.farPlane(), currentFarPlane) != 0;
        return new FogProjection(planes, changed, alreadyCanceled || changed);
    }

    public record FogPlanes(float nearPlane, float farPlane) {
    }

    public record FogProjection(FogPlanes planes, boolean changed, boolean canceled) {
    }
}
