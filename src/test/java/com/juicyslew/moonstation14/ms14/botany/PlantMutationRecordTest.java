package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.ms14.slip.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlantMutationRecordTest {
    private static final ItemSlipperyComposition SLIP = new ItemSlipperyComposition(
            new ItemStepTriggerData(false, .17, 7.25, true),
            new SlipResponseData(2.5, 4.5, 3.25, .75, 8.5, 14, false, true, true,
                    ResourceLocation.fromNamespaceAndPath("moonstation14", "mimana"), -100d));
    private static PlantMutationRecord record(String name, ItemSlipperyComposition payload) {
        return new PlantMutationRecord(name, false, true, true, payload);
    }
    private static GeneticValue value() {
        var id = ResourceLocation.fromNamespaceAndPath("test", "wheat");
        return new GeneticValue(id, new SeedProfile(id, id, "wheat",
                new SeedProfile.Biology(11,2,3,4,5,6,7,8,9,10,12),
                new SeedProfile.Lifecycle(3,20,21,22,23,24,SeedProfile.Harvest.SELF_HARVEST,false,true),
                Map.of(), Map.of(), Map.of(), List.of(), SeedProfile.Scream.DISABLED, "wheat"));
    }
    @Test void capturedDescriptorCodecAndAbsentRecordsDefault() {
        var mutation = record("Slippery", SLIP);
        var encoded = GeneticValue.CODEC.encodeStart(JsonOps.INSTANCE, new GeneticValue(value().species(), value().profile(), List.of(mutation))).getOrThrow();
         assertEquals(mutation, GeneticValue.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().mutations().getFirst());
         var saved = GeneticValue.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().mutations().getFirst().slippery();
         assertEquals(ResourceLocation.fromNamespaceAndPath("moonstation14", "mimana"), saved.response().soundEvent());
         assertEquals(-100d, saved.response().soundVolumeDb());
        assertEquals(List.of(), GeneticValue.CODEC.parse(JsonOps.INSTANCE,
                GeneticValue.CODEC.encodeStart(JsonOps.INSTANCE, value()).getOrThrow()).getOrThrow().mutations());
        var legacy = encoded.getAsJsonObject(); legacy.remove("mutations");
        assertTrue(GeneticValue.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow().mutations().isEmpty());
    }
    @Test void orderedAppendFirstNameWinsAndInputsAreDetached() {
        var source = new ArrayList<>(List.of(record("A", SLIP)));
        var genes = new GeneticValue(value().species(), value().profile(), source);
        source.clear(); assertEquals(1, genes.mutations().size());
        var initialGenes = genes;
        assertThrows(UnsupportedOperationException.class, () -> initialGenes.mutations().clear());
        var other = record("B", ItemSlipperyComposition.DEFAULT);
        genes = genes.withRecordedMutation(other).withRecordedMutation(record("A", ItemSlipperyComposition.DEFAULT));
        assertEquals(List.of("A", "B"), genes.mutations().stream().map(PlantMutationRecord::name).toList());
        assertEquals(SLIP, genes.mutations().getFirst().slippery());
    }
    @Test void noncrossCopiesAndUniqueSeedReferencePreserveDetachedRecords() {
        var original = value().withRecordedMutation(record("Slippery", SLIP));
        var stat = PlantStatMutation.numeric(PlantStatMutation.Target.ENDURANCE, 1, 3, 1).apply(original, new Random(1));
        assertEquals(original.mutations(), stat.mutations());
        var transformed = original.transformSpecies(ResourceLocation.fromNamespaceAndPath("test", "other"), original.profile());
        assertEquals(original.mutations(), transformed.mutations());
        var ref = new PlantSeedReference(original, false);
        var unique = ref.ensureUnique();
        assertNotSame(original, unique.current());
        assertEquals(original.mutations(), unique.current().mutations());
        assertEquals(original.mutations(), ref.current().mutations());
    }

    @Test void namedAcquiredRecordSurvivesCarrierAndOrdinaryPollenInheritance() {
        var acquired = record("Slippery", SLIP);
        var harvestedCarrier = value().withRecordedMutation(acquired);
        var encodedCarrier = GeneticValue.CODEC.encodeStart(JsonOps.INSTANCE, harvestedCarrier).getOrThrow();
        var extractedPacket = GeneticValue.CODEC.parse(JsonOps.INSTANCE, encodedCarrier).getOrThrow();
        assertEquals(List.of("Slippery"), extractedPacket.mutations().stream().map(PlantMutationRecord::name).toList());
        var replanted = new PlantSeedReference(extractedPacket, false).ensureUnique().current();
        assertEquals(acquired, replanted.mutations().getFirst());

        // A successful source-compatible B-record draw inherits the recipient packet's acquired record.
        var crossed = PlantPollenCross.cross(value(), replanted, () -> 0d);
        assertTrue(crossed.mutations().contains(acquired), "inherited named record remains attached after crossing");
        assertEquals(acquired.slippery(), crossed.mutations().getFirst().slippery());
    }
}
