package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.LinkedHashMap;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class SeedProfileTest {
    @Test void gasGenesUseCanonicalIdsCopyOnWriteAndSurviveOtherGeneticMutations() {
        var id=id("gas-test");
        var oxygen=com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN;
        var ammonia=com.juicyslew.moonstation14.ms14.atmos.core.GasType.AMMONIA;
        var profile=new com.juicyslew.moonstation14.ms14.botany.SeedProfile(id,id,"v",
                new com.juicyslew.moonstation14.ms14.botany.SeedProfile.Biology(100,.4f,.5f,293,10,70,120,5,10,3),
                new com.juicyslew.moonstation14.ms14.botany.SeedProfile.Lifecycle(6,30,5,3,6,60,
                        com.juicyslew.moonstation14.ms14.botany.SeedProfile.Harvest.NO_REPEAT),Map.of(),Map.of(oxygen,.2f),Map.of(ammonia,.3f),java.util.List.of(id("tomato"),id("potato"),id("tomato")),com.juicyslew.moonstation14.ms14.botany.SeedProfile.Scream.DISABLED,"test-gas-source-name");
        assertEquals(Map.of(oxygen,.2f),profile.consumeGasses()); assertEquals(Map.of(ammonia,.3f),profile.exudeGasses());
        var encoded=com.juicyslew.moonstation14.ms14.botany.SeedProfile.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,profile).getOrThrow();
        var decoded=com.juicyslew.moonstation14.ms14.botany.SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,encoded).getOrThrow();
        assertEquals(profile.consumeGasses(),decoded.consumeGasses()); assertEquals(profile.exudeGasses(),decoded.exudeGasses());
         var defaultJson=com.google.gson.JsonParser.parseString("{\"packet\":\"x:a\",\"product\":\"x:b\",\"visual_key\":\"v\",\"biology\":{\"endurance\":100,\"nutrient_consumption\":0,\"water_consumption\":0,\"ideal_heat\":293,\"heat_tolerance\":10,\"low_pressure\":0,\"high_pressure\":120,\"weed_tolerance\":5,\"weed_high_level\":10,\"toxin_tolerance\":3},\"lifecycle\":{\"growth_stages\":6,\"lifespan\":30,\"maturation\":5,\"production\":3,\"yield\":1,\"potency\":60,\"harvest\":\"NO_REPEAT\"},\"chemicals\":{},\"source_name\":\"test-default-source-name\"}");
        var empty=com.juicyslew.moonstation14.ms14.botany.SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,defaultJson).getOrThrow();
        assertTrue(empty.consumeGasses().isEmpty()); assertTrue(empty.exudeGasses().isEmpty());
        var value=new com.juicyslew.moonstation14.ms14.botany.GeneticValue(id,profile);
        var stat=com.juicyslew.moonstation14.ms14.botany.PlantStatMutation.numeric(com.juicyslew.moonstation14.ms14.botany.PlantStatMutation.Target.IDEAL_HEAT,263,323,5)
                .apply(value,new java.util.Random(1){@Override public float nextFloat(){return 0;}});
        assertEquals(profile.consumeGasses(),stat.profile().consumeGasses()); assertEquals(profile.exudeGasses(),stat.profile().exudeGasses());
        assertEquals(profile.mutationTargets(),stat.profile().mutationTargets());
        var mutation=new com.juicyslew.moonstation14.ms14.botany.PlantGasMutation(com.juicyslew.moonstation14.ms14.botany.PlantGasMutation.Target.EXUDE,.01f,.5f);
        var rng=new java.util.Random(1){@Override public float nextFloat(float origin,float bound){assertEquals(.01f,origin);assertEquals(.5f,bound);return .25f;} @Override public int nextInt(int bound){return 0;}};
        var changed=mutation.apply(value,rng);
        assertEquals(.2f,changed.profile().consumeGasses().get(oxygen)); assertEquals(.3f,changed.profile().exudeGasses().get(ammonia));
        assertEquals(.25f,changed.profile().exudeGasses().get(oxygen));
        var repeated=mutation.apply(changed,rng);
        assertEquals(.5f,repeated.profile().exudeGasses().get(oxygen));
        assertEquals(.3f,repeated.profile().exudeGasses().get(ammonia));
        assertEquals(.2f,repeated.profile().consumeGasses().get(oxygen));
        assertFalse(profile.exudeGasses().containsKey(oxygen),"immutable source genetics remain unchanged");
        assertEquals(profile.mutationTargets(),changed.profile().mutationTargets());
        for (var gas:com.juicyslew.moonstation14.ms14.atmos.core.GasType.values())
            assertEquals(gas,com.juicyslew.moonstation14.ms14.atmos.core.GasType.fromId(gas.id()));
    }
    @Test void genericStatMutationsPreserveTypedGeneticsAndSourceArithmetic() {
        var id = id("wheat");
        var profile = new com.juicyslew.moonstation14.ms14.botany.SeedProfile(id,id,"visual",
                new com.juicyslew.moonstation14.ms14.botany.SeedProfile.Biology(100,.4f,.5f,293,10,70,120,5,10,3),
                new com.juicyslew.moonstation14.ms14.botany.SeedProfile.Lifecycle(6,30,5,3,6,60,
                        com.juicyslew.moonstation14.ms14.botany.SeedProfile.Harvest.NO_REPEAT), Map.of(), "test-generic-source-name");
        var value = new com.juicyslew.moonstation14.ms14.botany.GeneticValue(id,profile);
        var heat = com.juicyslew.moonstation14.ms14.botany.PlantStatMutation.numeric(
                com.juicyslew.moonstation14.ms14.botany.PlantStatMutation.Target.IDEAL_HEAT,263,323,5);
        var up = heat.apply(value,new java.util.Random(1) { @Override public float nextFloat(){ return .5f; } });
        assertEquals(299f,up.profile().biology().idealHeat()); // 293 maps to midpoint-even bin 2, p=.6
        var down = heat.apply(value,new java.util.Random(1) { @Override public float nextFloat(){ return .7f; } });
        assertEquals(287f,down.profile().biology().idealHeat());
        var yield = com.juicyslew.moonstation14.ms14.botany.PlantStatMutation.integerYield(3,10,5);
        assertEquals(7,yield.apply(value,new java.util.Random(1) { @Override public float nextFloat(){ return 0; } }).profile().lifecycle().yield());
        var maxYield = new com.juicyslew.moonstation14.ms14.botany.GeneticValue(id,new com.juicyslew.moonstation14.ms14.botany.SeedProfile(
                 id,id,"v",profile.biology(),new com.juicyslew.moonstation14.ms14.botany.SeedProfile.Lifecycle(6,30,5,3,10,60,com.juicyslew.moonstation14.ms14.botany.SeedProfile.Harvest.NO_REPEAT),Map.of(),"test-max-yield-source-name"));
        assertEquals(9,yield.apply(maxYield,new java.util.Random(1)).profile().lifecycle().yield());
        assertEquals(profile.chemicals(),up.profile().chemicals()); assertEquals(profile.product(),up.profile().product());
        assertNotSame(profile,up.profile()); assertEquals(293f,value.profile().biology().idealHeat());
    }
    @Test void geneticsDefaultsAndFalseMarkersRoundTrip() {
        var lifeCodec = com.juicyslew.moonstation14.ms14.botany.SeedProfile.Lifecycle.CODEC;
        var json = com.google.gson.JsonParser.parseString("{\"growth_stages\":6,\"lifespan\":30,\"maturation\":5,\"production\":3,\"yield\":0,\"potency\":60,\"harvest\":\"NO_REPEAT\"}");
        var defaults = lifeCodec.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow();
        assertTrue(defaults.viable()); assertFalse(defaults.seedless()); assertEquals(0,defaults.yield());
        var encoded=lifeCodec.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,new com.juicyslew.moonstation14.ms14.botany.SeedProfile.Lifecycle(6,30,5,3,0,60,
                com.juicyslew.moonstation14.ms14.botany.SeedProfile.Harvest.NO_REPEAT,false,true)).getOrThrow();
        var decoded=lifeCodec.parse(com.mojang.serialization.JsonOps.INSTANCE,encoded).getOrThrow();
        assertFalse(decoded.viable()); assertTrue(decoded.seedless());
    }
    @Test void everyFloatTargetUpdatesItsTypedOwnerOnlyAndBooleanTargetsToggleInLifecycle() {
        var id=id("wheat");
        var original=new com.juicyslew.moonstation14.ms14.botany.SeedProfile(id,id,"visual",
                new com.juicyslew.moonstation14.ms14.botany.SeedProfile.Biology(100,.4f,.5f,293,10,70,120,5,10,3),
                new com.juicyslew.moonstation14.ms14.botany.SeedProfile.Lifecycle(6,30,5,3,6,60,
                        com.juicyslew.moonstation14.ms14.botany.SeedProfile.Harvest.NO_REPEAT,true,false,true),Map.of(),"test-target-source-name");
        var value=new com.juicyslew.moonstation14.ms14.botany.GeneticValue(id,original);
        for (var target : com.juicyslew.moonstation14.ms14.botany.PlantStatMutation.Target.values()) {
            if (target==com.juicyslew.moonstation14.ms14.botany.PlantStatMutation.Target.YIELD
                    || target==com.juicyslew.moonstation14.ms14.botany.PlantStatMutation.Target.VIABLE
                    || target==com.juicyslew.moonstation14.ms14.botany.PlantStatMutation.Target.SEEDLESS
                    || target==com.juicyslew.moonstation14.ms14.botany.PlantStatMutation.Target.CAN_SCREAM
                    || target==com.juicyslew.moonstation14.ms14.botany.PlantStatMutation.Target.LIGNEOUS) continue;
            float before=switch(target) {
                case ENDURANCE->100; case WATER_CONSUMPTION->.5f; case NUTRIENT_CONSUMPTION->.4f; case IDEAL_HEAT->293;
                case HEAT_TOLERANCE->10; case LOW_PRESSURE->70; case HIGH_PRESSURE->120; case WEED_TOLERANCE->5;
                case TOXIN_TOLERANCE->3; case PEST_TOLERANCE->5; case LIFESPAN->30; case MATURATION->5;
                case PRODUCTION->3; case POTENCY->60; default->throw new AssertionError(target);
            };
            var changed=com.juicyslew.moonstation14.ms14.botany.PlantStatMutation.numeric(target,before-10,before+10,5)
                    .apply(value,new java.util.Random(1){@Override public float nextFloat(){return 0;}});
            assertEquals(id,changed.species()); assertEquals(original.packet(),changed.profile().packet());
            assertEquals(original.product(),changed.profile().product()); assertEquals(original.visualKey(),changed.profile().visualKey());
            assertEquals(original.chemicals(),changed.profile().chemicals());
            assertTrue(changed.profile().lifecycle().ligneous(),"unrelated numeric mutation preserves Ligneous");
            if (switch(target) { case ENDURANCE,WATER_CONSUMPTION,NUTRIENT_CONSUMPTION,IDEAL_HEAT,HEAT_TOLERANCE,LOW_PRESSURE,HIGH_PRESSURE,WEED_TOLERANCE,TOXIN_TOLERANCE,PEST_TOLERANCE->true; default->false; }) {
                assertNotSame(original.biology(),changed.profile().biology(),target.name()); assertSame(original.lifecycle(),changed.profile().lifecycle(),target.name());
            } else { assertSame(original.biology(),changed.profile().biology(),target.name()); assertNotSame(original.lifecycle(),changed.profile().lifecycle(),target.name()); }
        }
        for (var target : new com.juicyslew.moonstation14.ms14.botany.PlantStatMutation.Target[]{
                com.juicyslew.moonstation14.ms14.botany.PlantStatMutation.Target.VIABLE,
                com.juicyslew.moonstation14.ms14.botany.PlantStatMutation.Target.SEEDLESS,
                com.juicyslew.moonstation14.ms14.botany.PlantStatMutation.Target.LIGNEOUS}) {
            var changed=com.juicyslew.moonstation14.ms14.botany.PlantStatMutation.toggle(target).apply(value,new java.util.Random(1));
            assertSame(original.biology(),changed.profile().biology()); assertNotSame(original.lifecycle(),changed.profile().lifecycle());
            assertEquals(target==com.juicyslew.moonstation14.ms14.botany.PlantStatMutation.Target.VIABLE?false:true,changed.profile().lifecycle().viable());
            assertEquals(target==com.juicyslew.moonstation14.ms14.botany.PlantStatMutation.Target.SEEDLESS,changed.profile().lifecycle().seedless());
            assertEquals(target!=com.juicyslew.moonstation14.ms14.botany.PlantStatMutation.Target.LIGNEOUS,changed.profile().lifecycle().ligneous());
        }
    }
    @Test void chemicalCodecDefaultsSourceInherentAndPersistsAcquiredFalseWithZeroDivisor() {
        var chemicalCodec = com.juicyslew.moonstation14.ms14.botany.SeedProfile.Chemical.CODEC;
        var inherited = chemicalCodec.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"min\":0.01,\"max\":1,\"potency_divisor\":0}")).getOrThrow();
        assertTrue(inherited.inherent());
        assertEquals(.01f, inherited.amount(10));
        var acquired = new com.juicyslew.moonstation14.ms14.botany.SeedProfile.Chemical(.2f, .5f, 200, false);
        var encoded = chemicalCodec.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, acquired).getOrThrow();
        assertFalse(chemicalCodec.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).getOrThrow().inherent());
    }
    @Test void chemicalAboveTenThousandSurvivesProfileCodecRoundTrip() {
        var profile = ModSeedProfiles.serverCatalog().get(id("wheat"));
        if (profile == null) {
            PrototypeRuntime.serverManager().reload(ModSeedProfiles.TYPE, loadProfiles());
            profile = ModSeedProfiles.serverCatalog().get(id("wheat"));
        }
        var chemicals = new java.util.LinkedHashMap<>(profile.chemicals());
        chemicals.put(id("large_acquired"), new com.juicyslew.moonstation14.ms14.botany.SeedProfile.Chemical(
                .01f, 10000.01f, 100f / 10000.01f, false));
        var large = new com.juicyslew.moonstation14.ms14.botany.SeedProfile(profile.packet(), profile.product(), profile.visualKey(),
                profile.biology(), profile.lifecycle(), chemicals, profile.sourceName());
        var encoded = com.juicyslew.moonstation14.ms14.botany.SeedProfile.CODEC
                .encodeStart(com.mojang.serialization.JsonOps.INSTANCE, large).getOrThrow();
        var decoded = com.juicyslew.moonstation14.ms14.botany.SeedProfile.CODEC
                .parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(10000.01f, decoded.chemicals().get(id("large_acquired")).max());
        assertFalse(decoded.chemicals().get(id("large_acquired")).inherent());
    }
    @Test void resolvedCatalogInheritsDefaultsAndChemistryAndRuntimeExposesSnapshot() {
        var runtime = PrototypeRuntime.serverManager();
        Map<ResourceLocation, com.google.gson.JsonObject> profiles = loadProfiles();
        runtime.reload(ModSeedProfiles.TYPE, profiles);
        PrototypeRuntime.clientManager().reload(ModSeedProfiles.TYPE, profiles);
        var wheat = ModSeedProfiles.serverCatalog().get(id("wheat"));
        assertEquals(100, wheat.biology().endurance());
        assertEquals(0.4f, wheat.biology().nutrientConsumption());
        assertEquals(6, wheat.lifecycle().growthStages());
        assertEquals(1.25f, wheat.chemicals().get(id("nutriment")).amount(5));
        assertEquals(20, wheat.chemicals().get(id("nutriment")).amount(1000));
        assertTrue(ModSeedProfiles.serverReady());
        assertSame(wheat, ModSeedProfiles.serverLookup(id("wheat")));
        assertTrue(ModSeedProfiles.clientReady());
        assertEquals(6, ModSeedProfiles.clientLookup(id("wheat")).lifecycle().growthStages());
        Map<String, Integer> stages = Map.of("wheat", 6, "tomato", 6, "potato", 4, "poppy", 3, "aloe", 5);
        for (var entry : stages.entrySet()) {
            var profile = ModSeedProfiles.serverLookup(id(entry.getKey()));
            assertEquals(100, profile.biology().endurance());
            assertEquals(entry.getValue(), profile.lifecycle().growthStages());
            assertFalse(profile.chemicals().isEmpty());
        }
        assertEquals("wheat_bushel", wheat.product().getPath());
        assertEquals(id("wheat_seeds"), wheat.packet());
        assertEquals("tomato", ModSeedProfiles.serverLookup(id("tomato")).product().getPath());
        assertEquals("potato", ModSeedProfiles.serverLookup(id("potato")).product().getPath());
        assertEquals("poppy", ModSeedProfiles.serverLookup(id("poppy")).product().getPath());
        assertEquals("aloe", ModSeedProfiles.serverLookup(id("aloe")).product().getPath());
        for (String crop : new String[]{"wheat", "tomato", "potato", "poppy", "aloe"})
        {
            var resolved=ModSeedProfiles.serverLookup(id(crop));
            assertEquals(id(crop + "_seeds"), resolved.packet(), crop + " packet prototype ID");
            assertTrue(resolved.consumeGasses().isEmpty(), crop + " inherits empty upstream consumption map");
            assertTrue(resolved.exudeGasses().isEmpty(), crop + " inherits empty upstream exudation map");
        }
    }
    @Test void productionProfilesHavePinnedSourceNamesAndCodecRoundTripsOpaqueNames() {
        var profiles=loadProfiles();
        PrototypeRuntime.serverManager().reload(ModSeedProfiles.TYPE,profiles);
        Map<String,String> expected=Map.of("wheat","seeds-wheat-name","tomato","seeds-tomato-name","potato","seeds-potato-name","poppy","seeds-poppy-name","aloe","seeds-aloe-name");
        for(var entry:expected.entrySet()) assertEquals(entry.getValue(),ModSeedProfiles.serverLookup(id(entry.getKey())).sourceName());
        var profile=ModSeedProfiles.serverLookup(id("wheat"));
        var opaque=new com.juicyslew.moonstation14.ms14.botany.SeedProfile(profile.packet(),profile.product(),profile.visualKey(),profile.biology(),profile.lifecycle(),profile.chemicals(),"opaque \u2603 source");
        var encoded=com.juicyslew.moonstation14.ms14.botany.SeedProfile.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,opaque).getOrThrow();
        assertEquals("opaque \u2603 source",encoded.getAsJsonObject().get("source_name").getAsString());
        var decoded=com.juicyslew.moonstation14.ms14.botany.SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,encoded).getOrThrow();
        assertEquals("opaque \u2603 source",decoded.sourceName());

        var genetic=new com.juicyslew.moonstation14.ms14.botany.GeneticValue(id("wheat"),opaque);
        var geneticJson=com.juicyslew.moonstation14.ms14.botany.GeneticValue.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,genetic).getOrThrow();
        var decodedGenetic=com.juicyslew.moonstation14.ms14.botany.GeneticValue.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,geneticJson).getOrThrow();
        assertEquals(id("wheat"),decodedGenetic.species());
        assertEquals("opaque \u2603 source",decodedGenetic.profile().sourceName());

        var reference=new com.juicyslew.moonstation14.ms14.botany.PlantSeedReference(genetic,false);
        var referenceJson=com.juicyslew.moonstation14.ms14.botany.PlantSeedReference.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,reference).getOrThrow();
        var decodedReference=com.juicyslew.moonstation14.ms14.botany.PlantSeedReference.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,referenceJson).getOrThrow();
        assertEquals(reference.id(),decodedReference.id());
        assertFalse(decodedReference.unique());
        assertEquals(id("wheat"),decodedReference.current().species());
        assertEquals("opaque \u2603 source",decodedReference.current().profile().sourceName());
        assertNotSame(reference,decodedReference,"serialized reference decodes as a detached identity");
    }
    @Test void inheritedSourceNameIsAllowedAndNamesAreNotUniqueAcrossConcreteSeeds() {
        var profiles=loadProfiles();
        var parent=profiles.get(id("tomato")).deepCopy(); profiles.put(id("source_parent"),parent);
        var child=new com.google.gson.JsonObject(); child.addProperty("parent","source_parent"); profiles.put(id("source_child"),child);
        var twin=profiles.get(id("potato")).deepCopy(); twin.getAsJsonObject().addProperty("source_name","tomato"); profiles.put(id("source_twin"),twin);
        PrototypeRuntime.serverManager().reload(ModSeedProfiles.TYPE,profiles);
        assertEquals("seeds-tomato-name",PrototypeRuntime.serverManager().snapshot(ModSeedProfiles.TYPE).get(id("source_child")).sourceName());
        assertEquals("tomato",PrototypeRuntime.serverManager().snapshot(ModSeedProfiles.TYPE).get(id("source_twin")).sourceName());
    }
    @Test void concreteSourceNameMustBePresentAndNonblank() {
        var missing=com.google.gson.JsonParser.parseString("{\"packet\":\"x:a\",\"product\":\"x:b\",\"visual_key\":\"v\",\"biology\":{\"endurance\":100,\"nutrient_consumption\":0,\"water_consumption\":0,\"ideal_heat\":293,\"heat_tolerance\":10,\"low_pressure\":0,\"high_pressure\":120,\"weed_tolerance\":5,\"weed_high_level\":10,\"toxin_tolerance\":3},\"lifecycle\":{\"growth_stages\":6,\"lifespan\":30,\"maturation\":5,\"production\":3,\"yield\":1,\"potency\":60,\"harvest\":\"NO_REPEAT\"},\"chemicals\":{}}");
        assertTrue(com.juicyslew.moonstation14.ms14.botany.SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,missing).error().isPresent());
        var profiles=loadProfiles(); profiles.get(id("wheat")).addProperty("source_name","  ");
        assertThrows(PrototypeLoadException.class,()->PrototypeRuntime.serverManager().reload(ModSeedProfiles.TYPE,profiles));
    }
    @Test void reservedUnboundNameIsRejectedOnlyForConcreteProfiles() {
        var profiles=loadProfiles();
        assertEquals("__unbound_seed_name__",profiles.get(id("defaults")).get("source_name").getAsString());
        profiles.get(id("wheat")).addProperty("source_name","__unbound_seed_name__");
        assertThrows(PrototypeLoadException.class,()->PrototypeRuntime.serverManager().reload(ModSeedProfiles.TYPE,profiles));
        var valid=loadProfiles(); PrototypeRuntime.serverManager().reload(ModSeedProfiles.TYPE,valid);
        assertEquals("__unbound_seed_name__",PrototypeRuntime.serverManager().snapshot(ModSeedProfiles.TYPE).get(id("defaults")).sourceName());
    }
    @Test void invalidSourceNameStagingLeavesPublishedSnapshotUnchanged() {
        var manager=PrototypeRuntime.serverManager(); manager.reload(ModSeedProfiles.TYPE,loadProfiles());
        var before=manager.snapshot(); var invalid=loadProfiles(); invalid.get(id("wheat")).addProperty("source_name","");
        assertThrows(PrototypeLoadException.class,()->manager.reload(ModSeedProfiles.TYPE,invalid));
        assertSame(before,manager.snapshot());
    }
    @Test void mutationTargetsCodecAndResolvedPrototypeInheritancePreserveOrdering() {
        var id=id("targets");
         var json=com.google.gson.JsonParser.parseString("{\"packet\":\"x:a\",\"product\":\"x:b\",\"visual_key\":\"v\",\"biology\":{\"endurance\":100,\"nutrient_consumption\":0,\"water_consumption\":0,\"ideal_heat\":293,\"heat_tolerance\":10,\"low_pressure\":0,\"high_pressure\":120,\"weed_tolerance\":5,\"weed_high_level\":10,\"toxin_tolerance\":3},\"lifecycle\":{\"growth_stages\":6,\"lifespan\":30,\"maturation\":5,\"production\":3,\"yield\":1,\"potency\":60,\"harvest\":\"NO_REPEAT\"},\"chemicals\":{},\"mutation_targets\":[\"x:tomato\",\"x:potato\",\"x:tomato\"],\"source_name\":\"test-targets-source-name\"}");
        var profile=com.juicyslew.moonstation14.ms14.botany.SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow();
        var tomato=ResourceLocation.fromNamespaceAndPath("x","tomato"); var potato=ResourceLocation.fromNamespaceAndPath("x","potato");
        assertEquals(java.util.List.of(tomato,potato,tomato),profile.mutationTargets());
        assertThrows(UnsupportedOperationException.class,()->profile.mutationTargets().add(id("wheat")));
        var encoded=com.juicyslew.moonstation14.ms14.botany.SeedProfile.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,profile).getOrThrow();
        assertEquals(profile.mutationTargets(),com.juicyslew.moonstation14.ms14.botany.SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,encoded).getOrThrow().mutationTargets());
    }
    @Test void resolvedPrototypeCatalogAppliesMutationTargetInheritanceAndChildOverride() {
        var profiles=loadProfiles(); var parent=profiles.get(id("tomato")).deepCopy().getAsJsonObject();
        parent.add("mutation_targets",com.google.gson.JsonParser.parseString("[\"moonstation14:potato\",\"moonstation14:potato\",\"moonstation14:wheat\"]"));
        profiles.put(id("targets_parent"),parent);
        var inherited=new com.google.gson.JsonObject(); inherited.addProperty("parent","targets_parent"); profiles.put(id("targets_inherited"),inherited);
        var overridden=new com.google.gson.JsonObject(); overridden.addProperty("parent","targets_parent");
        overridden.add("mutation_targets",com.google.gson.JsonParser.parseString("[\"moonstation14:aloe\",\"moonstation14:aloe\"]")); profiles.put(id("targets_overridden"),overridden);
        PrototypeRuntime.serverManager().reload(ModSeedProfiles.TYPE,profiles);
        var inheritedTargets=PrototypeRuntime.serverManager().snapshot(ModSeedProfiles.TYPE).get(id("targets_inherited")).mutationTargets();
        assertEquals(java.util.List.of(id("potato"),id("potato"),id("wheat")),inheritedTargets);
        assertEquals(java.util.List.of(id("aloe"),id("aloe")),PrototypeRuntime.serverManager().snapshot(ModSeedProfiles.TYPE).get(id("targets_overridden")).mutationTargets());
    }
    @Test void abstractZeroMaturationIsAllowedButConcreteZeroAndNegativeAreRejected() {
        var profiles = loadProfiles();
        PrototypeRuntime.serverManager().reload(ModSeedProfiles.TYPE, profiles);
        assertEquals(0, PrototypeRuntime.serverManager().snapshot(ModSeedProfiles.TYPE).get(id("defaults")).lifecycle().maturation());
        profiles.get(id("wheat")).getAsJsonObject("lifecycle").addProperty("maturation", 0);
        assertThrows(PrototypeLoadException.class, () -> PrototypeRuntime.serverManager().reload(ModSeedProfiles.TYPE, profiles));
        assertThrows(IllegalArgumentException.class, () -> new com.juicyslew.moonstation14.ms14.botany.SeedProfile.Lifecycle(
                6, 1, -1, 1, 1, 1, com.juicyslew.moonstation14.ms14.botany.SeedProfile.Harvest.NO_REPEAT));
    }
    private static Map<ResourceLocation, com.google.gson.JsonObject> loadProfiles() {
        var names = BotanyCatalogCandidateTest.resources().keySet().stream().map(ResourceLocation::getPath).toArray(String[]::new);
        return BotanyCatalogCandidateTest.mainResources("seed_profile", names);
    }
    private static ResourceLocation id(String id) { return ResourceLocation.fromNamespaceAndPath("moonstation14", id); }
}
