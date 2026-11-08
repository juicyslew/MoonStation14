package com.juicyslew.moonstation14.ms14.combat;

/** Pure presentation sampler for body-connected strike extension and its trail. */
public final class MeleeStrikeTimeline {
    private static final double WIDE_SWEEP_END_SECONDS = 0.6;
    private MeleeStrikeTimeline() {}

    public record Sample(double extension, double progress, double trailAlpha, boolean expired) {}

    /** SS14's copied-weapon sweep rotation as a fraction of its full turn. */
    public static double wideSweepFraction(MeleeStrikeProfile profile, double progress) {
        if (profile == null) throw new IllegalArgumentException("strike profile is required");
        if (!Double.isFinite(progress)) throw new IllegalArgumentException("progress must be finite");
        double fraction = Math.max(0, progress) * profile.durationSeconds() / WIDE_SWEEP_END_SECONDS;
        if (fraction < .10) return .5 * fraction / .10;
        if (fraction < .15) return .5 + .5 * (fraction - .10) / .05;
        if (fraction < .20) return 1 - .1 * (fraction - .15) / .05;
        if (fraction < .60) {
            double t = (fraction - .20) / .40;
            return .9 - .1 * (1 - Math.pow(1 - t, 4));
        }
        return .8;
    }

    /** Samples from attack start; attack rate scales the profile's authored duration. */
    public static Sample sample(MeleeStrikeProfile profile, double attackRate, double ageSeconds) {
        if (profile == null) throw new IllegalArgumentException("strike profile is required");
        if (!Double.isFinite(attackRate) || attackRate <= 0)
            throw new IllegalArgumentException("attack rate must be finite and positive");
        if (!Double.isFinite(ageSeconds)) throw new IllegalArgumentException("age must be finite");
        double duration = profile.durationSeconds() / attackRate;
        if (!Double.isFinite(duration) || duration <= 0)
            throw new IllegalArgumentException("scaled strike duration must be finite and positive");
        if (ageSeconds < 0) return new Sample(0, 0, 0, false);
        double progress = ageSeconds / duration;
        if (progress >= 1) return new Sample(0, 1, 0, true);
        if (progress <= 0) return new Sample(0, 0, profile.trailAlpha(), false);

        double extension;
        if (progress < profile.extensionEndFraction()) {
            extension = smooth(progress / profile.extensionEndFraction());
        } else if (progress <= profile.retractionStartFraction()) {
            extension = 1;
        } else {
            extension = 1 - smooth((progress - profile.retractionStartFraction())
                    / (1 - profile.retractionStartFraction()));
        }
        // The trail is strongest through extension/hold, then smoothly fades with retraction.
        double trail = profile.trailAlpha() * (progress <= profile.retractionStartFraction()
                ? 1 : 1 - smooth((progress - profile.retractionStartFraction())
                / (1 - profile.retractionStartFraction())));
        return new Sample(extension, progress, trail, false);
    }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }
}
