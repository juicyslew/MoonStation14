package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GasReactionExposurePriorityTest {
    private static final PrototypeCatalog<GasReactionData> CATALOG = new PrototypeCatalog<>(Map.of(
            ResourceLocation.parse("moonstation14:tritium_fire"), new GasReactionData(
                    Map.of(GasType.TRITIUM, .01, GasType.OXYGEN, .01), 373.149, 2000,
                    0, -1, List.of(new GasReactionData.Effect(GasReactionData.EffectType.TRITIUM_FIRE)))));

    @Test void freshExposureBurnsDespiteInvalidStoredPartition() {
        var before = new GasMixture(Map.of(GasType.TRITIUM, 1.0, GasType.OXYGEN, 1.0), 300);
        var stale = new HotspotKernel.HotspotState(.25, 1600);
        var exposure = new ExplosionHotspotExposure.Decision(true, 800, .25);
        var result = GasReactionStep.evaluate(CATALOG, before, stale, exposure);
        assertTrue(result.fireOccurred());
        assertFalse(result.events().isEmpty());
        double suppliedHeat = .25 * before.heatCapacity() * (800 - 300);
        assertEquals(suppliedHeat, result.externalHeatJoules(), 1e-9);
        double fireHeat = result.events().stream()
                .mapToDouble(GasReactionEvaluator.Event::energyDeltaJoules).sum();
        assertEquals(before.thermalEnergy() + suppliedHeat + fireHeat,
                result.mixture().thermalEnergy(), 1e-8);
        for (GasType species : GasType.values()) {
            double eventDelta = result.events().stream()
                    .mapToDouble(event -> event.speciesDelta().getOrDefault(species, 0.0)).sum();
            assertEquals(before.moles(species) + eventDelta, result.mixture().moles(species), 1e-10);
        }
    }
}
