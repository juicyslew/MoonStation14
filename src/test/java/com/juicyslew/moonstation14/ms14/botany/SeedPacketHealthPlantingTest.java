package com.juicyslew.moonstation14.ms14.botany;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SeedPacketHealthPlantingTest {
    private static SeedProfile profile() {
        return new SeedProfile(id("packet"), id("produce"), "fixture",
                new SeedProfile.Biology(7, 1, 1, 1, 1, 1, 1, 1, 1, 1, 13),
                new SeedProfile.Lifecycle(3, 20, 21, 22, 23, 24, SeedProfile.Harvest.NO_REPEAT, false, true),
                Map.of(), Map.of(), Map.of(), List.of(), SeedProfile.Scream.DISABLED, "test-fixture-name");
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("test", path); }

    @Test void packetPlantingUsesOptionalHealthWithoutMutatingSeedIdentityOrGenes() {
        var genetics = new GeneticValue(id("wheat"), profile());
        var identity = new PlantSeedReference(genetics, true);
        var normal = new PlantInstance(0, 0, 10);
        normal.plant(identity, 10);
        assertEquals(7f, normal.health());
        assertSame(identity, normal.seedReference());

        var damaged = new PlantInstance(0, 0, 20);
        damaged.plant(identity, 20, -3.25f);
        assertEquals(-3.25f, damaged.health());
        assertSame(identity, damaged.seedReference());
        assertSame(genetics, identity.current());
        assertTrue(identity.unique(), "packet planting does not clone a shared or unique seed identity");
    }
}
