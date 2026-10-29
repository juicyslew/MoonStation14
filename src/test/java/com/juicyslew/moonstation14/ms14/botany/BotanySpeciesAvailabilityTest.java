package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BotanySpeciesAvailabilityTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("test", path); }
    private static SeedProfile seed(List<ResourceLocation> edges) {
        return new SeedProfile(id("packet"), id("product"), "visual",
                new SeedProfile.Biology(10, 1, 1, 293, 10, 0, 1000, 5, 10, 5),
                new SeedProfile.Lifecycle(3, 10, 1, 1, 2, 10, SeedProfile.Harvest.REPEAT),
                Map.of(), Map.of(), Map.of(), edges, SeedProfile.Scream.DISABLED, "test");
    }
    @Test void requiresRegisteredPacketAndSupportedRegisteredProduceComposition() {
        var profile = seed(List.of(id("unavailable_edge")));
        var seeds = new PrototypeCatalog<SeedProfile>(Map.of(id("plant"), profile));
        var products = new PrototypeCatalog<ProduceProfile>(Map.of(id("product"), new ProduceProfile(null, null)));
        assertTrue(BotanySpeciesAvailability.policy(seeds, products, id -> true, id -> true).test(id("plant")));
        assertFalse(BotanySpeciesAvailability.policy(seeds, products, id -> !id.equals(id("packet")), id -> true).test(id("plant")));
        assertFalse(BotanySpeciesAvailability.policy(seeds, products, id -> !id.equals(id("product")), id -> true).test(id("plant")));
        assertFalse(BotanySpeciesAvailability.policy(seeds, new PrototypeCatalog<>(Map.of()), id -> true, id -> true).test(id("plant")));
        assertFalse(BotanySpeciesAvailability.policy(seeds, products, id -> true, id -> false).test(id("plant")));
        assertTrue(profile.mutationTargets().contains(id("unavailable_edge")));
        assertTrue(BotanySpeciesAvailability.policy(seeds, products, id -> true, id -> true).test(id("plant")));
    }
}
