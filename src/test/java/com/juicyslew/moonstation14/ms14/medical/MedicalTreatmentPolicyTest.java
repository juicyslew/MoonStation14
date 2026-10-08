package com.juicyslew.moonstation14.ms14.medical;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MedicalTreatmentPolicyTest {
    @Test void selfTreatmentUsesResolvedDelayMultiplierAndCriticalThreshold() {
        var policy = new MedicalItemData.HealingPolicy(Map.of("blunt", -5f), java.util.List.of("biological"),
                0, 3, 3, Optional.empty(), Optional.empty());
        assertEquals(60, MedicalTreatmentService.treatmentDelayTicks(policy, 0, 100));
        assertEquals(180, MedicalTreatmentService.treatmentDelayTicks(policy, 100, 100));
        assertEquals(180, MedicalTreatmentService.treatmentDelayTicks(policy, 100, 0));
        assertEquals(60, MedicalTreatmentService.treatmentDelayTicks(policy, 0, 200));
        assertEquals(60, MedicalTreatmentService.baseDelayTicks(policy));
    }

    @Test void healingPolicyCarriesExactResolvedTypedDeltas() {
        var policy = new MedicalItemData.HealingPolicy(Map.of("heat", -5f, "caustic", -1.5f),
                java.util.List.of("biological"), 0, 3, 3,
                Optional.of(ResourceLocation.parse("moonstation14:medical/ointment_begin")), Optional.empty());
        assertEquals(Map.of("heat", -5f, "caustic", -1.5f), policy.damage());
        assertEquals(java.util.List.of("biological"), policy.allowedDamageContainers());
        assertEquals(true, MedicalTreatmentService.acceptsDamageContainer(policy, Optional.of("Biological")));
        assertEquals(false, MedicalTreatmentService.acceptsDamageContainer(policy, Optional.of("mechanical")));
        assertEquals(true, MedicalTreatmentService.acceptsDamageContainer(policy, Optional.empty()));
    }
}
