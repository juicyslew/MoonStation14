package com.juicyslew.moonstation14.ms14.combat;

/** Pure client-presentation sampler for the pinned SS14 melee arc animation tracks. */
public final class MeleeArcTimeline {
    private static final double THRUST_LENGTH = 0.6;
    private static final double FADE_TAIL = 0.15;

    private MeleeArcTimeline() {}

    public record Sample(double offsetMultiplier, double lateralOffset, double rotationDelta, double alpha, boolean expired) {}

    /** C9Effects lunge keys are absolute animation-local times, independent of attack rate. */
    public static double lunge(double elapsedSeconds) {
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0) throw new IllegalArgumentException("elapsed time must be finite and nonnegative");
        if (elapsedSeconds <= .04) return .15 * (elapsedSeconds / .04);
        if (elapsedSeconds <= .10) return .15 * (1 - (elapsedSeconds - .04) / .06);
        return 0;
    }

    /**
     * Samples the resolved profile. SS14 AnimationTrackProperty KeyTime values are relative to
     * segment duration (C9Effects.cs:151-177; AnimationTrackProperty at gitlink 3136118b),
     * so thrust's accumulated keys are 0, .10L, .30L, .60L, 1.20L. AnimationLength is L:
     * the track holds its value at L rather than advancing to the final key at 1.20L.
     */
    public static Sample sample(MeleeArcProfile profile, double attackRate, double configuredAnimationOffset, double elapsedSeconds) {
        return sample(profile, attackRate, configuredAnimationOffset, 60, false, elapsedSeconds);
    }
    public static Sample sample(MeleeArcProfile profile, double attackRate, double configuredAnimationOffset,
                                double angleDegrees, boolean swingLeft, double elapsedSeconds) {
        if (profile == null) throw new IllegalArgumentException("profile is required");
        if (!Double.isFinite(attackRate) || attackRate <= 0) throw new IllegalArgumentException("attack rate must be finite and positive");
        if (!Double.isFinite(configuredAnimationOffset)) throw new IllegalArgumentException("animation offset must be finite");
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0) throw new IllegalArgumentException("elapsed time must be finite and nonnegative");
        double lifetime = profile.lifetimeSeconds();
        if (profile.animation() == MeleeArcProfile.Animation.NONE) {
            double alpha = profile.fadeOut() ? clamp(1.0 - elapsedSeconds / FADE_TAIL) : 1.0;
            return new Sample(0, 0, 0, alpha, elapsedSeconds >= (profile.fadeOut() ? Math.min(lifetime, FADE_TAIL) : lifetime));
        }

        double length = THRUST_LENGTH / attackRate;
        double motionLength = profile.animation() == MeleeArcProfile.Animation.SLASH ? length + .05 : length;
        double t = Math.min(elapsedSeconds, motionLength);
        double multiplier; double lateral = 0, rotation = 0;
        if (profile.animation() == MeleeArcProfile.Animation.SLASH) {
            double signedArc = angleDegrees * (swingLeft ? -1 : 1);
            double half = signedArc * .5;
            double rotationFraction;
            if (elapsedSeconds <= .1 * length) rotationFraction=lerp(0,.5,elapsedSeconds/(.1*length));
            else if (elapsedSeconds <= .25 * length) rotationFraction=lerp(.5,1,(elapsedSeconds-.1*length)/(.15*length));
            else if (elapsedSeconds <= .45 * length) rotationFraction=lerp(1,.9,(elapsedSeconds-.25*length)/(.2*length));
            else rotationFraction=lerpOutQuart(.9,.8,(elapsedSeconds-.45*length)/(.6*length));
            rotation = half - signedArc * rotationFraction;

            double startForward=.9*Math.cos(Math.toRadians(half));
            double startLateral=.9*Math.sin(Math.toRadians(half));
            double endForward=.9*Math.cos(Math.toRadians(half));
            double endLateral=-.9*Math.sin(Math.toRadians(half));
            double forward, side;
            if (t <= .1*length) {
                double f=t/(.1*length); forward=lerp(startForward,1.1,f); side=lerp(startLateral,0,f);
            } else if (t <= .25*length) {
                double f=(t-.1*length)/(.15*length); forward=lerp(1.1,endForward,f); side=lerp(0,endLateral,f);
            } else if (t <= .85*length) {
                double f=(t-.25*length)/(.6*length); forward=lerp(endForward,lerp(startForward,endForward,.8),f); side=lerp(endLateral,lerp(startLateral,endLateral,.8),f);
            } else {
                forward=lerp(startForward,endForward,.8); side=lerp(startLateral,endLateral,.8);
            }
            multiplier=forward;
            lateral=side;
        } else {
        if (t <= .1 * length) multiplier = lerp(0, .78, t / (.1 * length));
        else if (t <= .3 * length) multiplier = lerp(.78, 1.2, (t - .1 * length) / (.2 * length));
        else if (t <= .6 * length) multiplier = lerp(1.2, 1.08, (t - .3 * length) / (.3 * length));
        else multiplier = lerpOutQuart(1.08, .84, (t - .6 * length) / (.6 * length));
        }
        double fadeEnd = length + FADE_TAIL;
        // Fade keys use relative segment duration too: second key is one full L+.15 after .5L.
        double alpha = profile.fadeOut() ? clamp(1.0 - Math.max(0, elapsedSeconds - .5 * length) / fadeEnd) : 1.0;
        boolean expired = elapsedSeconds >= (profile.fadeOut() ? Math.min(lifetime, fadeEnd) : lifetime);
        return new Sample(multiplier * configuredAnimationOffset, lateral * configuredAnimationOffset, rotation, alpha, expired);
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static double lerpOutQuart(double a, double b, double t) { double x = 1 - clamp(t); return lerp(a, b, 1 - x * x * x * x); }
    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
}
