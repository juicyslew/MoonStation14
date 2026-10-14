package com.juicyslew.moonstation14.ms14.botany;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class HydroponicsWarningAppearanceTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "warning_test");
    private static final SeedProfile SEED = new SeedProfile(ID, ID, "plant",
            new SeedProfile.Biology(30, 0, 0, 293, 10, 81, 121, 5, 10, 4),
            new SeedProfile.Lifecycle(5, 25, 10, 5, 1, 1, SeedProfile.Harvest.REPEAT), Map.of());

    @Test void warningBitsAreQuietWhenDisabledOrAtmosphereUnknown() {
        PlantInstance plant = new PlantInstance(100, 100, 0);
        plant.plant(ID, SEED, 0);
        assertEquals(0, PlantMetabolismEvaluator.warningBits(plant, SEED, false, Optional.empty()));
        assertEquals(0, PlantMetabolismEvaluator.warningBits(plant, SEED, true, Optional.empty()));
    }

    @Test void knownExposureUsesStrictPressureAndTemperatureLimits() {
        var biology = PlantMetabolismEvaluator.improperEnvironment(SEED, new PlantMetabolismEvaluator.Environment(81, 303));
        assertFalse(biology.pressure()); assertFalse(biology.temperature());
        assertTrue(PlantMetabolismEvaluator.improperEnvironment(SEED, new PlantMetabolismEvaluator.Environment(80.99f, 303)).pressure());
        assertTrue(PlantMetabolismEvaluator.improperEnvironment(SEED, new PlantMetabolismEvaluator.Environment(121.01f, 303)).pressure());
        assertTrue(PlantMetabolismEvaluator.improperEnvironment(SEED, new PlantMetabolismEvaluator.Environment(100, 303.01f)).temperature());
        PlantInstance plant = new PlantInstance(100, 100, 0);
        plant.plant(ID, SEED, 0);
        assertEquals(0, PlantMetabolismEvaluator.warningBits(plant, SEED, true,
                Optional.of(new PlantMetabolismEvaluator.Environment(81, 303))));
        assertEquals(4, PlantMetabolismEvaluator.warningBits(plant, SEED, true,
                Optional.of(new PlantMetabolismEvaluator.Environment(80, 293))));
        assertEquals(4, PlantMetabolismEvaluator.warningBits(plant, SEED, true,
                Optional.of(new PlantMetabolismEvaluator.Environment(100, 303.01f))));
    }

    @Test void soilAndHealthThresholdsAreProjected() {
        PlantInstance plant = new PlantInstance(15, 8, 0);
        plant.plant(ID, SEED, 0);
        plant.soil(15, 8, 5, 40);
        plant.state(1, 5, 0, 0, true, false);
        assertEquals(31, PlantMetabolismEvaluator.warningBits(plant, SEED, true, Optional.empty()));
        plant.soil(16, 9, 4, 39);
        plant.state(1, 16, 0, 0, false, false);
        assertEquals(0, PlantMetabolismEvaluator.warningBits(plant, SEED, true, Optional.empty()));
    }
}
