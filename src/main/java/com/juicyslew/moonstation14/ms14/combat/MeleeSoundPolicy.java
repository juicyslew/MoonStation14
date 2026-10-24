package com.juicyslew.moonstation14.ms14.combat;

/** Selection policy for the currently supported source weapon cues. */
public final class MeleeSoundPolicy {
    private MeleeSoundPolicy() { }
    public static PositionalSoundCue hit(MeleeSoundData audio) {
        var cue = audio.hit() != null ? audio.hit() : audio.noDamage();
        return cue == null ? null : cue.withVariation(.05f);
    }
    public static PositionalSoundCue hit(MeleeSoundData audio, PositionalSoundCue override) {
        return override != null ? override.withVariation(.05f) : hit(audio);
    }
    public static boolean shouldPlayHit(boolean handled, boolean targetSurvives) { return !handled && targetSurvives; }
    public static boolean shouldPredictSwing(boolean nominatedMiss) { return nominatedMiss; }
    public static float pitch(float base, float randomUnit, float variation) {
        if (!Float.isFinite(base) || !Float.isFinite(randomUnit) || !Float.isFinite(variation)) return 1f;
        return base + (randomUnit * 2f - 1f) * variation;
    }
}
