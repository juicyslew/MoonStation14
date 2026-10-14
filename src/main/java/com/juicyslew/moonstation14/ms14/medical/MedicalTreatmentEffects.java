package com.juicyslew.moonstation14.ms14.medical;

import com.juicyslew.moonstation14.ms14.damage.DamageReducer;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;

/** The resolved typed-damage and bleeding effects shared by authoritative treatment and focused tests. */
public final class MedicalTreatmentEffects {
    private MedicalTreatmentEffects() { }

    public record Result(boolean damageApplied, double bleedApplied) {
        public boolean changed() { return damageApplied || bleedApplied != 0; }
    }

    public static Result apply(LivingEntity patient, MedicalItemData.HealingPolicy policy) {
        Map<String, Float> before = damageSnapshot(patient);
        Map<String, Float> after = DamageReducer.applyDelta(before, policy.damage());
        boolean damageEffect = !after.equals(before);
        double bleedBefore = com.juicyslew.moonstation14.ms14.blood.BloodSystem.state(patient)
                .filter(com.juicyslew.moonstation14.ms14.blood.BloodComponent::initialized)
                .map(com.juicyslew.moonstation14.ms14.blood.BloodComponent::bleedRate).orElse(0.0);
        boolean bleedEffect = policy.bleedModifier() < 0 && bleedBefore > 0
                && com.juicyslew.moonstation14.ms14.blood.BloodSystem.resolvePolicy(patient).isPresent();
        boolean damageApplied = false;
        if (damageEffect && DamageSystem.applyHealthChange(patient, policy.damage(), 1f, false) == DamageSystem.Result.APPLIED)
            damageApplied = !damageSnapshot(patient).equals(before);
        double bleedApplied = bleedEffect
                ? com.juicyslew.moonstation14.ms14.blood.BloodSystem.modifyBleedAmount(patient, policy.bleedModifier()) : 0;
        return new Result(damageApplied, bleedApplied);
    }

    private static Map<String, Float> damageSnapshot(LivingEntity patient) {
        var stored = patient.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get());
        if (com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isDead(patient)) return stored == null ? Map.of() : stored.getMap();
        return DamageSystem.existingOrVanillaBaseline(patient);
    }
}

