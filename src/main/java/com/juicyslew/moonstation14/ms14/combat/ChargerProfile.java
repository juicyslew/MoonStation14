package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;

/** Typed projection of the supported BaseRecharger composition. Power demand is J/s. */
public record ChargerProfile(float idlePower, float chargeRate, boolean ejectOnInteract,
                             String slotId, Set<ChargerCapability> acceptedCapabilities) {
    public static final Codec<ChargerProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("idle_power", 1f).forGetter(ChargerProfile::idlePower),
            Codec.FLOAT.optionalFieldOf("charge_rate", 20f).forGetter(ChargerProfile::chargeRate),
            Codec.BOOL.optionalFieldOf("eject_on_interact", true).forGetter(ChargerProfile::ejectOnInteract),
            Codec.STRING.optionalFieldOf("slot_id", "charger_slot").forGetter(ChargerProfile::slotId),
            ChargerCapability.CODEC.listOf().optionalFieldOf("accepted_capabilities", java.util.List.of())
                    .xmap(Set::copyOf, values -> java.util.List.copyOf(values)).forGetter(ChargerProfile::acceptedCapabilities)
    ).apply(i, ChargerProfile::new));
    public ChargerProfile { acceptedCapabilities = Set.copyOf(acceptedCapabilities); if (slotId == null || slotId.isBlank() || !Float.isFinite(idlePower) || idlePower < 0 || !Float.isFinite(chargeRate) || chargeRate <= 0) throw new IllegalArgumentException("invalid charger profile"); }
}
