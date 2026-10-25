package com.juicyslew.moonstation14.ms14.combat;

/** Pure rules shared by the server projectile tick and focused policy tests. */
public final class ProjectileImpactPolicy {
    public enum TickSide { CLIENT, SERVER }
    private ProjectileImpactPolicy() { }
    public static boolean discardForMissingProfile(TickSide side, boolean hasProfile) {
        return side == TickSide.SERVER && !hasProfile;
    }
    public static int lifetimeTicks(ProjectileProfile profile) {
        double ticks = Math.ceil((double) profile.lifetimeSeconds() * 20d);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1d, ticks));
    }
    public static boolean consumeCollision(ProjectileProfile profile) {
        return profile.deleteOnCollide();
    }
    /** One authored impact event per collision, independently of whether configured damage is zero. */
    public static boolean emitImpact(ProjectileProfile profile, boolean alreadyEmitted) { return profile != null && !alreadyEmitted; }
}
