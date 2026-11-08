package com.juicyslew.moonstation14.ms14.combat;

/** Shared read-only interpretation of persistent fire-mode state. */
public final class GunModePolicy {
    private GunModePolicy() { }

    public static String effectiveFireMode(GunRuntimeState state, GunProfile profile) {
        if (profile == null) return null;
        String selected = state == null ? null : state.selectedFireMode();
        return selected != null && profile.gun().availableModes().contains(selected)
                ? selected : profile.gun().firingMode();
    }

    /** Cycles only configured firing modes in stable canonical order and preserves cadence/ammo fields. */
    public static GunRuntimeState cycleFireMode(GunRuntimeState state, GunProfile profile) {
        if (state == null || profile == null) return null;
        var modes = java.util.List.of("FULL_AUTO", "SEMI_AUTO").stream()
                .filter(profile.gun().availableModes()::contains).toList();
        if (modes.size() < 2) return null;
        String current = effectiveFireMode(state, profile);
        return state.withSelectedFireMode(modes.get((modes.indexOf(current) + 1) % modes.size()));
    }

    public static int selectedBatteryMode(GunRuntimeState state, GunProfile profile) {
        int index = state == null ? 0 : state.batteryModeIndex();
        int count = profile == null || profile.batteryFireModes().isEmpty() ? 1
                : profile.batteryFireModes().orElseThrow().size();
        return index >= 0 && index < count ? index : 0;
    }

    public static boolean effectivePacifismAllowed(GunRuntimeState state, GunProfile profile) {
        if (profile == null) return false;
        if (profile.batteryFireModes().isPresent())
            return profile.batteryFireModes().orElseThrow().get(selectedBatteryMode(state, profile)).pacifismAllowed();
        return profile.pacifismAllowed();
    }

    public static GunProfile.SpreadData effectiveSpread(GunProfile profile, boolean validWieldProof) {
        var base = profile.gun().spread();
        if (!validWieldProof || profile.wieldBonus().isEmpty()) return base;
        var bonus = profile.wieldBonus().orElseThrow();
        return new GunProfile.SpreadData(Math.max(0, base.minimumDegrees() + bonus.minimumDegrees()),
                Math.max(0, base.maximumDegrees() + bonus.maximumDegrees()),
                Math.max(0, base.angleIncreaseDegrees() + bonus.angleIncreaseDegrees()),
                Math.max(0, base.angleDecayDegreesPerSecond() + bonus.angleDecayDegrees()));
    }

    public static boolean mayFire(GunProfile profile, boolean validWieldProof) {
        return profile != null && (!profile.requiresWield() || validWieldProof);
    }
}
