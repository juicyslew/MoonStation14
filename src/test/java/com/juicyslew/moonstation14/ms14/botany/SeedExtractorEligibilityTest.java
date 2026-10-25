package com.juicyslew.moonstation14.ms14.botany;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SeedExtractorEligibilityTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "extractor_test");

    private static SeedProfile profile(boolean seedless) {
        return new SeedProfile(ID, ID, "plant", new SeedProfile.Biology(30, 0, 0, 293, 10, 81, 121, 5, 10, 4),
                new SeedProfile.Lifecycle(5, 25, 10, 5, 1, 1, SeedProfile.Harvest.REPEAT, true, seedless), Map.of(), "test-plant-name");
    }

    @Test void effectiveGeneticEligibilityRejectsSeedlessWithoutSpeciesBranches() {
        var baseline = profile(false);
        var mutated = profile(true);
        assertTrue(SeedExtractorHandUseService.canExtract(baseline));
        assertFalse(SeedExtractorHandUseService.canExtract(mutated));
        assertFalse(SeedExtractorHandUseService.canExtract(null));
    }

    @Test void unboundProduceFallbackCannotResolveAnExtractableSeed() {
        var unboundProduce = new ProduceProfile(null, null);
        assertNull(unboundProduce.seed());
        assertFalse(SeedExtractorHandUseService.canExtract(null),
                "an unbound produce profile's null seed fallback cannot authorize extraction");
    }
}
