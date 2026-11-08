package com.juicyslew.moonstation14.ms14.botany;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class BotanyInspectCommandTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    private static PlantInstance wheat() {
        var manager = new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager();
        manager.register(ModSeedProfiles.TYPE);
         var names = BotanyCatalogCandidateTest.resources().keySet().stream().map(ResourceLocation::getPath).toArray(String[]::new);
         manager.reload(ModSeedProfiles.TYPE, BotanyCatalogCandidateTest.mainResources("seed_profile", names));
        var plant = new PlantInstance(40, 40, 0);
        plant.plant(new GeneticValue(id("wheat"), manager.snapshot(ModSeedProfiles.TYPE).get(id("wheat"))), 0);
        return plant;
    }
    private static BotanyInspectCommand.Snapshot capture(PlantInstance plant, BlockPos pos, int tick, Map<String,String> outcomes) {
        return BotanyInspectCommand.Snapshot.capture("moonstation14:test", pos, tick, plant, outcomes);
    }
    private static SeedProfile copy(SeedProfile p, Map<ResourceLocation,SeedProfile.Chemical> chemicals,
                                    Map<com.juicyslew.moonstation14.ms14.atmos.core.GasType,Float> consume,
                                    Map<com.juicyslew.moonstation14.ms14.atmos.core.GasType,Float> exude,
                                    SeedProfile.Lifecycle lifecycle, SeedProfile.Scream scream) {
        return new SeedProfile(p.packet(), p.product(), p.visualKey(), p.biology(), lifecycle, chemicals, consume,
                exude, p.mutationTargets(), scream, p.sourceName());
    }

    @Test void captureMapsLiveResolvedPlantAndSnapshotsAreDefensive() {
        var plant = wheat(); var pos = new BlockPos.MutableBlockPos(1,2,3);
        var reference = plant.seedReference(); var revision = plant.revision(); var profile = plant.effectiveProfile();
        assertEquals(100f, profile.biology().endurance());
        assertEquals(25f, profile.lifecycle().lifespan());
        var outcomes = new java.util.HashMap<>(Map.of("tray.warningBits", "0"));
        var baseline = capture(plant, pos, 10, outcomes);
        outcomes.put("species", "forged"); outcomes.put("tray.warningBits", "99"); pos.setX(8);
         assertEquals("moonstation14:wheat", baseline.fields().get("species"));
         assertEquals("moonstation14:wheat_bushel", baseline.fields().get("product identity"));
         assertEquals("moonstation14:wheat_seeds", baseline.fields().get("packet identity"));
         assertEquals("wheat", baseline.fields().get("visual identity"));
         assertTrue(baseline.fields().containsKey("product consumer"));
         assertTrue(baseline.fields().containsKey("harvest item consumer"));
        assertEquals("100.0", baseline.fields().get("biology.endurance"));
        assertEquals("25.0", baseline.fields().get("lifecycle.lifespan"));
        assertEquals("0", baseline.fields().get("outcome.tray.warningBits"));
        assertEquals(new BlockPos(1,2,3), baseline.pos());
        assertThrows(UnsupportedOperationException.class, () -> baseline.fields().put("x", "y"));
        assertEquals(revision, plant.revision()); assertSame(reference, plant.seedReference()); assertSame(profile, plant.effectiveProfile());

        var changed = PlantStatMutation.numeric(PlantStatMutation.Target.ENDURANCE, 0, 200, 200).apply(plant.genetics(), new Random(1));
        plant.genetics(changed);
        var current = capture(plant, new BlockPos(1,2,3), 11, Map.of("tray.warningBits", "0"));
        assertEquals(Float.toString(changed.profile().biology().endurance()), current.fields().get("biology.endurance"));
        assertTrue(current.differences(baseline).contains("biology.endurance: 100.0 → " + current.fields().get("biology.endurance")));
        var report = current.lines("CURRENT SAMPLE", current.differences(baseline));
        assertTrue(report.stream().anyMatch(line -> line.contains("not mutation provenance")));
        assertFalse(report.stream().anyMatch(line -> line.contains("caused")));
        assertEquals(List.of("No named fields changed."), baseline.differences(baseline));
        assertFalse(baseline.sameTarget(new BotanyInspectCommand.Snapshot("other", new BlockPos(1,2,3), 12, plant.revision(), Map.of())));
        assertFalse(baseline.sameTarget(BotanyInspectCommand.Snapshot.capture("moonstation14:test", new BlockPos(2,2,3), 12, plant, Map.of())));
    }

    @Test void mapsCapturedMutationDescriptorsAndDiffsFullPayloadWithoutChangingPlantIdentity() {
        var plant=wheat(); var pos=new BlockPos(7,8,9); var reference=plant.seedReference(); var revision=plant.revision();
        var empty=capture(plant,pos,1,Map.of());
        assertEquals(revision,plant.revision()); assertSame(reference,plant.seedReference());
        assertEquals("0",empty.fields().get("mutations.count"));
        var original=empty.fields();
        var payload=new com.juicyslew.moonstation14.ms14.slip.ItemSlipperyComposition(
                new com.juicyslew.moonstation14.ms14.slip.ItemStepTriggerData(true,.4,4.25,true),
                new com.juicyslew.moonstation14.ms14.slip.SlipResponseData(.7,1.2,1.8,.35,.6,17,true,false,true));
        var record=new PlantMutationRecord("Slipify",true,false,true,payload);
        plant.genetics(new GeneticValue(plant.genetics().species(),plant.genetics().profile(),List.of(record)));
        var baseline=capture(plant,pos,2,Map.of());
        var baselineRevision=plant.revision(); var baselineReference=plant.seedReference(); var baselineGenetics=plant.genetics(); var baselineUnique=reference.unique();
        assertEquals(baselineRevision,plant.revision()); assertSame(baselineReference,plant.seedReference()); assertSame(baselineGenetics,plant.genetics()); assertEquals(baselineUnique,reference.unique());
        assertEquals("Slipify",baseline.fields().get("mutation.0.name"));
        assertEquals("true",baseline.fields().get("mutation.0.appliesToPlant"));
        assertEquals("false",baseline.fields().get("mutation.0.appliesToProduce"));
        assertEquals("true",baseline.fields().get("mutation.0.persists"));
        assertEquals("0.4",baseline.fields().get("mutation.0.slippery.stepTrigger.intersectionRatio"));
        assertTrue(baseline.fields().get("mutation.0.slippery.response").contains("stunSeconds=0.7"));
        assertTrue(baseline.lines("CAPTURED",List.of()).stream().anyMatch(s->s.contains("not provenance, history, or action cause")));

        var changedPayload=new com.juicyslew.moonstation14.ms14.slip.ItemSlipperyComposition(
                payload.stepTrigger(),new com.juicyslew.moonstation14.ms14.slip.SlipResponseData(.9,1.2,1.8,.35,.6,17,true,false,true));
        plant.genetics(new GeneticValue(plant.genetics().species(),plant.genetics().profile(),List.of(new PlantMutationRecord("Slipify",true,false,true,changedPayload))));
        var changedRevision=plant.revision(); var changedReference=plant.seedReference(); var changedGenetics=plant.genetics(); var changedUnique=reference.unique();
        var current=capture(plant,pos,3,Map.of());
        assertEquals(changedRevision,plant.revision()); assertSame(changedReference,plant.seedReference()); assertSame(changedGenetics,plant.genetics()); assertEquals(changedUnique,reference.unique());
        assertTrue(current.differences(baseline).stream().anyMatch(s->s.startsWith("mutation.0.slippery.response:")));
        assertEquals(original.get("mutations.count"),empty.fields().get("mutations.count"));
    }

    @Test void mapperTracksActualChemicalGasSpeciesAndCapabilityChanges() {
        var plant=wheat(); var base=plant.genetics(); var profile=base.profile(); var pos=new BlockPos(4,5,6);
        var before=capture(plant,pos,1,Map.of());
        var chemicals=new java.util.HashMap<>(profile.chemicals());
        var chemicalId=ResourceLocation.fromNamespaceAndPath("moonstation14","inspection_test_chemical");
        chemicals.put(chemicalId,new SeedProfile.Chemical(1f,2f,100f,false));
        var consume=new java.util.EnumMap<com.juicyslew.moonstation14.ms14.atmos.core.GasType,Float>(com.juicyslew.moonstation14.ms14.atmos.core.GasType.class);
        consume.putAll(profile.consumeGasses()); consume.put(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN,0.75f);
        var exude=new java.util.EnumMap<com.juicyslew.moonstation14.ms14.atmos.core.GasType,Float>(com.juicyslew.moonstation14.ms14.atmos.core.GasType.class);
        exude.putAll(profile.exudeGasses()); exude.put(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN,0.25f);
        var life=profile.lifecycle();
        var toggled=new SeedProfile.Lifecycle(life.growthStages(),life.lifespan(),life.maturation(),life.production(),life.yield(),life.potency(),life.harvest(),life.viable(),!life.seedless(),!life.ligneous());
        var changedProfile=copy(profile,chemicals,consume,exude,toggled,profile.scream().withEnabled(!profile.scream().enabled()));
        plant.genetics(new GeneticValue(base.species(),changedProfile));
        var after=capture(plant,pos,2,Map.of());
        assertEquals(base.species().toString(),after.fields().get("species"));
        var chemical=changedProfile.chemicals().get(chemicalId);
        assertEquals(Float.toString(chemical.min()),after.fields().get("chemical."+chemicalId+".min"));
        assertTrue(after.differences(before).contains("chemical."+chemicalId+".min: null → 1.0"));
        assertEquals("0.75",after.fields().get("consumeGas.oxygen"));
        assertEquals("0.25",after.fields().get("exudeGas.nitrogen"));
        assertTrue(after.differences(before).contains("consumeGas.oxygen: null → 0.75"));
        assertEquals(Boolean.toString(toggled.seedless()),after.fields().get("lifecycle.seedless"));
        assertEquals(Boolean.toString(toggled.ligneous()),after.fields().get("lifecycle.ligneous"));
        assertEquals(Boolean.toString(changedProfile.scream().enabled()),after.fields().get("scream.enabled"));
        assertTrue(after.differences(before).stream().anyMatch(s->s.startsWith("scream.enabled:")));

        chemicals.remove(chemicalId); consume.remove(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN);
        exude.remove(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN);
        var removed=copy(changedProfile,chemicals,consume,exude,toggled,changedProfile.scream());
        plant.genetics(new GeneticValue(plant.genetics().species(),removed));
        var finalSample=capture(plant,pos,3,Map.of());
        assertTrue(finalSample.differences(after).contains("chemical."+chemicalId+".min: 1.0 → null"));
        assertTrue(finalSample.differences(after).contains("consumeGas.oxygen: 0.75 → null"));
        assertTrue(finalSample.differences(after).contains("exudeGas.nitrogen: 0.25 → null"));
    }

}
