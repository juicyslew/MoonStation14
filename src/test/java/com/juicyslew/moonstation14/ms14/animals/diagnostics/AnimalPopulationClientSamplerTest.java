package com.juicyslew.moonstation14.ms14.animals.diagnostics;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AnimalPopulationClientSamplerTest {
    @Test void atomicFrameAndUuidAdmission() {
        String populationPrior = System.getProperty("ms14.animalPopulationDiagnostic");
        String posePrior = System.getProperty("ms14.animalFlyerPoseDiagnostic");
        try {
        System.setProperty("ms14.animalPopulationDiagnostic", "false");
        System.setProperty("ms14.animalFlyerPoseDiagnostic", "true");
        assertFalse(AnimalPopulationClientSampler.tryFlyerPoseSample(UUID.randomUUID(), new Object(), 5));
        System.setProperty("ms14.animalPopulationDiagnostic", "true");
        System.setProperty("ms14.animalFlyerPoseDiagnostic", "false");
        assertFalse(AnimalPopulationClientSampler.tryFlyerPoseSample(UUID.randomUUID(), new Object(), 5));
        System.setProperty("ms14.animalFlyerPoseDiagnostic", "true");
        var budget = new AnimalPopulationClientSampler.FlyerPoseBudget();
        UUID[] ids = new UUID[17];
        for (int i=0;i<17;i++) ids[i]=UUID.randomUUID();
        budget.beginFrame();
        for (int i=0;i<4;i++) assertTrue(budget.trySample(ids[i], 0));
        assertFalse(budget.trySample(ids[4], 0));
        budget.beginFrame();
        assertFalse(budget.trySample(ids[0], 4));
        assertTrue(budget.trySample(ids[0], 5));
        budget.beginFrame();
        assertTrue(budget.trySample(ids[1], 5));
        assertTrue(budget.trySample(ids[2], 5));
        assertTrue(budget.trySample(ids[3], 5));
        assertTrue(budget.trySample(ids[4], 5)); // rejected frame admission did not stamp UUID
        assertFalse(budget.trySample(ids[5], 5));
        for (int batch=0;batch<2;batch++) {
            budget.beginFrame();
            for (int i=0;i<4;i++) assertTrue(budget.trySample(ids[5+batch*4+i], 5));
        }
        budget.beginFrame();
        for (int i=0;i<3;i++) assertTrue(budget.trySample(ids[13+i], 5));
        budget.beginFrame();
        assertFalse(budget.trySample(ids[16], 5)); // sixteen-ID cap
        budget.clear(); budget.beginFrame();
        assertTrue(budget.trySample(ids[16], 5));
        budget.clear(); budget.beginFrame();
        assertTrue(budget.trySample(ids[0], 5));
        } finally {
            if (populationPrior == null) System.clearProperty("ms14.animalPopulationDiagnostic"); else System.setProperty("ms14.animalPopulationDiagnostic", populationPrior);
            if (posePrior == null) System.clearProperty("ms14.animalFlyerPoseDiagnostic"); else System.setProperty("ms14.animalFlyerPoseDiagnostic", posePrior);
        }
    }
}
