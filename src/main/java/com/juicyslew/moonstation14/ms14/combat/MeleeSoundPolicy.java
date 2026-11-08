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
    public static PositionalSoundCue outcome(MeleeSoundData audio, PositionalSoundCue override, boolean committedPositiveDamage) {
        if (override != null) return override.withVariation(.05f);
        var cue = committedPositiveDamage ? audio.hit() : audio.noDamage();
        if (cue == null) cue = audio.hit() != null ? audio.hit() : audio.noDamage();
        return cue == null ? null : cue.withVariation(.05f);
    }
    /** Selects the first surviving positive contact, or first surviving contact if all were zero. */
    public static int wideContactIndex(java.util.List<Boolean> committedPositive) {
        if (committedPositive == null || committedPositive.isEmpty()) return -1;
        for (int i = 0; i < committedPositive.size(); i++) if (Boolean.TRUE.equals(committedPositive.get(i))) return i;
        return 0;
    }
    public static boolean shouldPlayHit(boolean handled, boolean targetSurvives) { return !handled && targetSurvives; }
    public static boolean shouldPredictSwing(boolean nominatedMiss) { return nominatedMiss; }
    /** Sound recipients for a server swing cue: only a client-confirmed local cue suppresses its own echo. */
    public static java.util.UUID swingExcludedPlayer(java.util.UUID attacker, boolean swingPredicted) {
        return swingPredicted ? attacker : null;
    }
    public static float pitch(float base, float randomUnit, float variation) {
        if (!Float.isFinite(base) || !Float.isFinite(randomUnit) || !Float.isFinite(variation)) return 1f;
        return base + (randomUnit * 2f - 1f) * variation;
    }
}
