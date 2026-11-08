package com.juicyslew.moonstation14.ms14.singularity;

/** Atomic per-generator startup accumulator. Charge is accepted only when the closed volume is valid. */
public final class SingularityGeneratorStartup {
    private double charge;
    private boolean inert;
    private long nextFailsafeTick;

    public SingularityGeneratorStartup(SingularityGeneratorProfile profile) {
        this(profile, 0, profile.startsInert(), 0);
    }

    /** Restores persistent state. Cooldown deadlines use stable world-game-time ticks. */
    public SingularityGeneratorStartup(SingularityGeneratorProfile profile, double savedCharge,
                                       boolean savedInert, long savedNextFailsafeTick) {
        profile.validate();
        if (!Double.isFinite(savedCharge) || savedCharge < 0 || savedNextFailsafeTick < 0)
            throw new IllegalArgumentException("invalid saved singularity generator state");
        charge = savedCharge;
        inert = savedInert;
        nextFailsafeTick = savedNextFailsafeTick;
    }

    /** SS14 SingularityGeneratorSystem:112-159. Failed volume checks schedule failsafe; only a later valid pulse charges. */
    public synchronized boolean accept(double correctedPaStrength, boolean closedVolumeValid,
                                       long nowTick, SingularityGeneratorProfile profile) {
        if (inert || !Double.isFinite(correctedPaStrength) || correctedPaStrength <= 0 || nowTick < 0)
            return false;
        if (nowTick < nextFailsafeTick) return false;
        if (!closedVolumeValid) {
            nextFailsafeTick = Math.addExact(nowTick, cooldownTicks(profile));
            return false;
        }
        charge += correctedPaStrength;
        if (charge < profile.startupThreshold()) return false;
        charge = 0;
        inert = true;
        return true;
    }

    public synchronized double charge() { return charge; }
    public synchronized boolean inert() { return inert; }
    public synchronized long nextFailsafeTick() { return nextFailsafeTick; }
    public synchronized Snapshot snapshot() { return new Snapshot(charge, inert, nextFailsafeTick); }

    private static long cooldownTicks(SingularityGeneratorProfile profile) {
        profile.validate();
        return (long) Math.ceil(profile.failsafeCooldownSeconds() * 20);
    }

    public record Snapshot(double charge, boolean inert, long nextFailsafeTick) { }
}

