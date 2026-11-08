package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.juicyslew.moonstation14.ms14.atmos.audio.AtmosAmbientAudio;

/** Pure gate shared by the authoritative block entity and focused policy tests. */
public final class AtmosAmbientEligibility {
    private AtmosAmbientEligibility() { }

    public static boolean eligible(AtmosMachineProfile profile, boolean enabled, double acceptedMoles,
                                   boolean actuallyPowered, int nativeDisplay) {
        if (profile == null || profile.ambientAudio().isEmpty()) return false;
        var cue = profile.ambientAudio().orElseThrow();
        if (cue.gate() == AtmosAmbientAudio.Gate.ENABLED_POWERED)
            return eligible(cue, enabled, actuallyPowered);
        return switch (cue.gate()) {
            case TRANSFER -> enabled && Double.isFinite(acceptedMoles) && acceptedMoles > 0
                    && (profile.power() == null || actuallyPowered);
            case OPEN -> enabled;
            case TEG_DISPLAY -> nativeDisplay >= 2;
            case ENABLED_POWERED -> enabled && actuallyPowered;
        };
    }

    /** Eligibility for shared cues consumed outside the atmos-machine profile runtime. */
    public static boolean eligible(AtmosAmbientAudio cue, boolean enabled, boolean actuallyPowered) {
        return cue != null && cue.gate() == AtmosAmbientAudio.Gate.ENABLED_POWERED
                && enabled && actuallyPowered;
    }
}
