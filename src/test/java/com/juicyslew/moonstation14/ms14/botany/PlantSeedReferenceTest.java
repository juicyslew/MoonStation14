package com.juicyslew.moonstation14.ms14.botany;

import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlantSeedReferenceTest {
    private static ResourceLocation id(String value) { return ResourceLocation.fromNamespaceAndPath("test", value); }
    private static GeneticValue value(String species) {
        return new GeneticValue(id(species), new SeedProfile(id("packet"), id("produce"), species,
                new SeedProfile.Biology(11,2,3,4,5,6,7,8,9,10,12),
                new SeedProfile.Lifecycle(3,20,21,22,23,24,SeedProfile.Harvest.SELF_HARVEST,false,true),
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.List.of(), SeedProfile.Scream.DISABLED, "test-"+species+"-name"));
    }
    @Test void idEqualitySurvivesGenomeUpdateAndCodecDecodesDetachedCurrentSnapshot() {
        var reference = new PlantSeedReference(value("first"), true);
        int hash = reference.hashCode();
        var updated = value("second");
        reference.update(updated);
        assertEquals(hash, reference.hashCode());
        var encoded = PlantSeedReference.CODEC.encodeStart(JsonOps.INSTANCE, reference).getOrThrow();
        var decoded = PlantSeedReference.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(reference, decoded);
        assertNotSame(reference, decoded);
        assertEquals(updated, decoded.current());
        assertTrue(decoded.unique());
    }
    @Test void uniquenessDetachesOnlySharedIdentity() {
        var unique = new PlantSeedReference(value("one"), true);
        assertSame(unique, unique.ensureUnique());
        var shared = new PlantSeedReference(value("two"), false);
        var detached = shared.ensureUnique();
        assertNotSame(shared, detached);
        assertNotEquals(shared, detached);
        assertEquals(shared.current(), detached.current());
        assertNotSame(shared.current(), detached.current());
        assertFalse(shared.unique());
        assertTrue(detached.unique());
    }
    @Test void copyOnWriteResetsOnlyCopiedAudioWhilePreservingToggleAndOldAlias() {
        var originalValue = value("audio");
        var customAudio = new SeedProfile.Scream(true, id("custom_scream"), -3f);
        var customProfile = originalValue.profile().withScream(customAudio);
        var shared = new PlantSeedReference(new GeneticValue(originalValue.species(), customProfile), false);
        var oldAlias = shared.current();
        var detached = shared.ensureUnique();
        assertNotSame(shared, detached);
        assertSame(oldAlias, shared.current());
        assertEquals(customAudio, shared.current().profile().scream());
        assertEquals(new SeedProfile.Scream(true, SeedProfile.Scream.DEFAULT_SOUND, -10f), detached.current().profile().scream());
        var copied = detached.current();
        var inPlace = new GeneticValue(copied.species(), copied.profile().withScream(customAudio));
        detached.update(inPlace);
        assertEquals(customAudio, detached.current().profile().scream());
    }
    @Test void plantMutationsUpdateCellButSpeciesTransitionAndClearDetachHolder() {
        var plant = new PlantInstance(20, 20, 0);
        var first = value("first");
        plant.plant(first, 0);
        var captured = plant.seedReference();
        var inPlace = value("first");
        plant.genetics(inPlace);
        assertSame(captured, plant.seedReference());
        assertSame(inPlace, captured.current());
        var next = value("second");
        plant.transitionSpecies(next);
        assertNotSame(captured, plant.seedReference());
        plant.clearPlant();
        assertNull(plant.seedReference());
        assertNull(plant.genetics());
        assertSame(inPlace, captured.current());
    }
}
