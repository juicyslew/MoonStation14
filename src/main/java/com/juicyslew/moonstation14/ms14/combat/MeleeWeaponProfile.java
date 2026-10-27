package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.Optional;

/** Resolved catalog wrapper for supported melee weapon statistics. */
public record MeleeWeaponProfile(MeleeWeaponData weapon, Map<String, Float> damageOnHit,
                                 Optional<MeleeChemicalInjectorData> chemicalInjector) {
    public static final Codec<MeleeWeaponProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            MeleeWeaponData.CODEC.fieldOf("weapon").forGetter(MeleeWeaponProfile::weapon),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("damage_on_hit", Map.of()).forGetter(MeleeWeaponProfile::damageOnHit),
            MeleeChemicalInjectorData.CODEC.optionalFieldOf("chemical_injector").forGetter(MeleeWeaponProfile::chemicalInjector)
    ).apply(i, MeleeWeaponProfile::new));
    public MeleeWeaponProfile {
        if (damageOnHit != null && damageOnHit.values().stream().anyMatch(value -> value == null || !Float.isFinite(value) || value <= 0f))
            throw new IllegalArgumentException("hit damage must be finite and positive");
        damageOnHit = com.juicyslew.moonstation14.ms14.damage.DamageKeys.validateState(damageOnHit);
        chemicalInjector = chemicalInjector == null ? Optional.empty() : chemicalInjector;
    }

    public record MeleeChemicalInjectorData(String solutionName, int transferCents, boolean pierceArmor) {
        public static final Codec<MeleeChemicalInjectorData> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("solution_name", "default").forGetter(MeleeChemicalInjectorData::solutionName),
                Codec.INT.optionalFieldOf("transfer_cents", 100).forGetter(MeleeChemicalInjectorData::transferCents),
                Codec.BOOL.optionalFieldOf("pierce_armor", true).forGetter(MeleeChemicalInjectorData::pierceArmor)
        ).apply(i, MeleeChemicalInjectorData::new));
        public MeleeChemicalInjectorData {
            if (solutionName == null || solutionName.isBlank()) throw new IllegalArgumentException("solution name must be nonblank");
            if (transferCents <= 0) throw new IllegalArgumentException("transfer amount must be positive");
        }
    }
}
