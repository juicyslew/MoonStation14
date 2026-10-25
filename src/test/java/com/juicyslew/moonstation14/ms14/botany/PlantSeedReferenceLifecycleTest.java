package com.juicyslew.moonstation14.ms14.botany;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlantSeedReferenceLifecycleTest {
    private static ResourceLocation id(String value) { return ResourceLocation.fromNamespaceAndPath("test", value); }
    private static GeneticValue value(String species, float potency) {
        var profile = new SeedProfile(id("packet"), id("produce"), species,
                new SeedProfile.Biology(11,2,3,4,5,6,7,8,9,10,12),
                new SeedProfile.Lifecycle(3,20,21,22,23,24,SeedProfile.Harvest.SELF_HARVEST,false,true),
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.List.of(), SeedProfile.Scream.DISABLED, "test-"+species+"-name");
        return new GeneticValue(id(species), profile).adjustPotency(potency);
    }
    @Test void capturedAliasObservesInPlaceChangeThenCowLeavesDonorUntouched() {
        var source = new PlantInstance(20,20,0);
        source.plant(value("seed", 1), 0);
        var capture = source.seedReference();
        capture.unique(false);
        assertSame(capture, source.seedReference());
        source.ensureUniqueSeed();
        assertNotSame(capture, source.seedReference());
        assertEquals(capture.current(), source.genetics());
        assertNotSame(capture.current(), source.genetics());
        source.genetics(source.genetics().adjustPotency(1));
        assertNotSame(capture.current(), source.genetics());
        assertTrue(source.seedReference().unique());
        assertFalse(capture.unique());
    }
    @Test void cowCloneDefaultsAudioWithoutChangingCapturedAliasAndUniqueCellKeepsCustomAudio() {
        var base = value("seed", 1);
        var customAudio = new SeedProfile.Scream(true, id("custom/scream"), -3f);
        var custom = new GeneticValue(base.species(), base.profile().withScream(customAudio));
        var shared = new PlantSeedReference(custom, false);
        var detached = shared.ensureUnique();
        assertNotSame(shared, detached);
        assertNotEquals(shared.id(), detached.id());
        assertEquals(customAudio, shared.current().profile().scream());
        assertEquals(new SeedProfile.Scream(true, SeedProfile.Scream.DEFAULT_SOUND, -10f), detached.current().profile().scream());
        assertEquals(custom.profile().mutationTargets(), detached.current().profile().mutationTargets());
        assertSame(detached, detached.ensureUnique());
        assertEquals(SeedProfile.Scream.DEFAULT_SOUND, detached.current().profile().scream().sound());
        var uniqueCustom = new PlantSeedReference(custom, true);
        assertSame(uniqueCustom, uniqueCustom.ensureUnique());
        uniqueCustom.update(uniqueCustom.current().adjustPotency(1));
        assertEquals(customAudio, uniqueCustom.current().profile().scream());
    }
    @Test void plantRemovalAndSpeciesReplacementDoNotRewriteCapturedIdentity() {
        var source = new PlantInstance(20,20,0);
        source.plant(value("seed", 1), 0);
        var capture = source.seedReference();
        source.transitionSpecies(value("other", 2));
        assertNotSame(capture, source.seedReference());
        source.clearPlant();
        assertEquals(value("seed", 1).species(), capture.current().species());
        assertNull(source.seedReference());
    }
}
