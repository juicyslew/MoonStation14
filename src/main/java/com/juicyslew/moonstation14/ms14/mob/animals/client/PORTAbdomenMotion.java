package com.juicyslew.moonstation14.ms14.mob.animals.client;

/** Small PORT-only gait articulation shared by the moproach and mothroach rigs. */
final class PORTAbdomenMotion {

    private PORTAbdomenMotion() {}

    static float yaw(String clip, boolean dead, float elapsedSeconds, float duration) {
        if (dead || !Float.isFinite(elapsedSeconds)) return 0.0F;
        float amplitudeDegrees;
        if ("walk".equals(clip)) {
            amplitudeDegrees = 4.0F;
        } else if ("run".equals(clip)) {
            amplitudeDegrees = 6.0F;
        } else {
            return 0.0F;
        }
        if (!(duration > 0.0F) || !Float.isFinite(duration)) return 0.0F;
        double phase = (Math.max(0.0F, elapsedSeconds) % duration) / duration;
        return (float) Math.sin(phase * Math.PI * 2.0) * amplitudeDegrees * (float) (Math.PI / 180.0);
    }
}
