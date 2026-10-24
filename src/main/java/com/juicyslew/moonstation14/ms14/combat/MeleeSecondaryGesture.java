package com.juicyslew.moonstation14.ms14.combat;

/** Source-compatible secondary melee mode selection, independent of client input APIs. */
public final class MeleeSecondaryGesture {
    private MeleeSecondaryGesture() { }

    public static MeleeAttackRequest.Mode mode(MeleeWeaponResolver.Kind kind, boolean altDisarm, Boolean canDisarm) {
        if (kind == MeleeWeaponResolver.Kind.UNARMED && altDisarm)
            return Boolean.TRUE.equals(canDisarm) ? MeleeAttackRequest.Mode.DISARM : null;
        return MeleeAttackRequest.Mode.WIDE;
    }
}
