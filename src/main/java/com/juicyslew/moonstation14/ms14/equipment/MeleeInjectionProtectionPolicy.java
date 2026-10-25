package com.juicyslew.moonstation14.ms14.equipment;

import java.util.List;

/** Hardsuit-only protection rule for non-piercing melee injection. */
public final class MeleeInjectionProtectionPolicy {
    private MeleeInjectionProtectionPolicy() { }

    public static boolean protects(List<ResolvedWornEquipment.Entry> wornEntries, boolean pierceArmor) {
        if (pierceArmor) return false;
        return wornEntries.stream().anyMatch(entry ->
                EquipmentSlots.OUTER_CLOTHING.equals(entry.slotId()) && entry.profile().tags().contains(EquipmentTag.HARDSUIT));
    }
}
