package com.juicyslew.moonstation14.ms14.damage;

/** Shared decisions for the server native-event bridge and independent client visual hooks. */
public final class DamageFeedbackPresentationPolicy {
    private DamageFeedbackPresentationPolicy() { }
    public static boolean allowRedFlash(DamageFeedbackProfile profile) { return profile.redFlash(); }
    public static boolean allowCameraShake(DamageFeedbackProfile profile) { return profile.cameraShake(); }
    public static boolean useNativeDamageEvent(DamageFeedbackProfile profile) {
        return profile.redFlash() && profile.cameraShake();
    }
    public static boolean clearServerHurtCounter(DamageFeedbackProfile profile) {
        return !useNativeDamageEvent(profile);
    }
    public static boolean allowRedOverlay(DamageFeedbackProfile profile, int hurtTime, int deathTime,
                                          boolean medicallyManaged, boolean feedbackActive) {
        if (medicallyManaged) return hurtTime > 0 && profile.redFlash();
        return (hurtTime > 0 && (!feedbackActive || profile.redFlash())) || (deathTime > 0 && !feedbackActive);
    }
    public static boolean allowHurtCamera(DamageFeedbackProfile profile, int hurtTime, int deathTime,
                                          boolean medicallyManaged, boolean feedbackActive) {
        if (medicallyManaged) return hurtTime > 0 && profile.cameraShake();
        return (hurtTime > 0 && (!feedbackActive || profile.cameraShake())) || (deathTime > 0 && !feedbackActive);
    }
}

