package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.component.codec.json.PlantMetabolismData;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.botany.PlantReagentMetabolism;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlantReagentMetabolismTest {
    private static final ResourceKey<ReagentData> WATER = ModReagents.createKey("water");
    private static final ResourceKey<ReagentData> NUTRIENT = ModReagents.createKey("eznutrient");

    @Test
    void resolvedInheritedWaterAndNutrientApplyFixedEffectsAndConsumeOneUnit() throws IOException {
        var catalog = resolvedCatalog();
        ReagentData water = catalog.get(id("water"));
        ReagentData nutrient = catalog.get(id("eznutrient"));
        assertTrue(water.plantMetabolism().stream().anyMatch(PlantMetabolismData.PlantAdjustWater.class::isInstance),
                "water must receive PlantAdjustWater through resolved BaseDrink ancestry");
        assertEquals(1f, ((PlantMetabolismData.PlantAdjustWater) water.plantMetabolism().stream()
                .filter(PlantMetabolismData.PlantAdjustWater.class::isInstance).findFirst().orElseThrow()).amount());
        assertEquals(2f, ((PlantMetabolismData.PlantAdjustNutrition) nutrient.plantMetabolism().getFirst()).amount());

        ReagentAttachment mixture = new ReagentAttachment(Map.of(WATER, 50f, NUTRIENT, 50f));
        Target target = new Target();
        var result = PlantReagentMetabolism.poll(mixture, key -> catalog.get(key.location()), target);
        assertTrue(result.unsupportedEffects().isEmpty());
        assertEquals(1f, target.water);
        assertEquals(2f, target.nutrition);
        assertEquals(Map.of(WATER, 49f, NUTRIENT, 49f), mixture.getMap());
    }

    @Test void actualResolvedCareReagentsExposeAndDispatchAllSixCareDescriptors() throws IOException {
        var catalog = resolvedCatalog();
        List<String> ids = List.of("ammonia", "chlorine", "pestkiller", "sedin", "robustharvest");
        var keys = ids.stream().map(ModReagents::createKey).toList();
        var mixtureMap = new LinkedHashMap<ResourceKey<ReagentData>, Float>();
        keys.forEach(key -> mixtureMap.put(key, 1f));
        class CareDispatch extends Target {
            int health, toxins, weeds, pests, potency, robust;
            @Override public double probabilitySample() { return .01d; }
            @Override public boolean hasLivingPlant() { return true; }
            @Override public void adjustHealth(float amount) { health++; }
            @Override public void adjustToxins(float amount) { toxins++; }
            @Override public void adjustWeeds(float amount) { weeds++; }
            @Override public void adjustPests(float amount) { pests++; }
            @Override public void adjustPotency(float amount) { potency++; }
            @Override public void robustHarvest(PlantMetabolismData.RobustHarvest effect) { robust++; }
        }
        CareDispatch target = new CareDispatch();
        var result = PlantReagentMetabolism.poll(new ReagentAttachment(mixtureMap),
                key -> catalog.get(key.location()), target);
        assertTrue(result.unsupportedEffects().isEmpty());
        assertTrue(target.health > 0 && target.toxins > 0 && target.weeds > 0 && target.pests > 0
                && target.potency > 0 && target.robust > 0);
        assertTrue(mixtureMap.keySet().stream().allMatch(key -> catalog.get(key.location()) != null));
    }

    @Test void remainingResolvedEffectsUseTypedDefaultsAndConfiguredScaleThreshold() throws IOException {
        var catalog = resolvedCatalog();
        var cryo = catalog.get(id("cryoxadone")).plantMetabolism().stream()
                .filter(PlantMetabolismData.PlantCryoxadone.class::isInstance).map(PlantMetabolismData.PlantCryoxadone.class::cast).findFirst().orElseThrow();
        var diethyl = catalog.get(id("diethylamine")).plantMetabolism().stream()
                .filter(PlantMetabolismData.PlantAffectGrowth.class::isInstance).map(PlantMetabolismData.PlantAffectGrowth.class::cast).findFirst().orElseThrow();
        var diethylamine = catalog.get(id("diethylamine")).plantMetabolism().stream()
                .filter(PlantMetabolismData.PlantDiethylamine.class::isInstance).map(PlantMetabolismData.PlantDiethylamine.class::cast).findFirst().orElseThrow();
        var phalan = catalog.get(id("phalanximine")).plantMetabolism().stream()
                .filter(PlantMetabolismData.PlantPhalanximine.class::isInstance).map(PlantMetabolismData.PlantPhalanximine.class::cast).findFirst().orElseThrow();
        var restore = catalog.get(id("sedin")).plantMetabolism().stream()
                .filter(PlantMetabolismData.PlantRestoreSeeds.class::isInstance).map(PlantMetabolismData.PlantRestoreSeeds.class::cast).findFirst().orElseThrow();
        assertEquals(1f, cryo.probability());
        assertEquals(.2f, diethyl.probability()); assertEquals(1f, diethyl.amount());
        assertEquals(1f, diethylamine.probability());
        assertEquals(4f, phalan.minScale()); assertEquals(1f, phalan.probability());
        assertEquals(.2f, restore.probability());

        class Effects extends Target {
            int cryo, growth, diethyl, phalan, restore;
            @Override public boolean hasLivingPlant() { return true; }
            @Override public double probabilitySample() { return .1; }
            @Override public void cryoxadone() { cryo++; }
            @Override public void affectGrowth(int amount) { growth += amount; }
            @Override public void diethylamine() { diethyl++; }
            @Override public void phalanximine() { phalan++; }
            @Override public void restoreSeeds() { restore++; }
        }
        var scaleTarget = new Effects();
        var key = ModReagents.createKey("phalan_test");
        PlantReagentMetabolism.poll(new ReagentAttachment(Map.of(key, 3.99f)),
                ignored -> fake("phalan_test", List.of(phalan)), scaleTarget);
        assertEquals(0, scaleTarget.phalan, "minScale is checked against unrounded entry volume");
        PlantReagentMetabolism.poll(new ReagentAttachment(Map.of(key, 4f)),
                ignored -> fake("phalan_test", List.of(phalan)), scaleTarget);
        assertEquals(1, scaleTarget.phalan);

        var all = List.<PlantMetabolismData>of(cryo, diethyl, diethylamine, restore);
        PlantReagentMetabolism.poll(new ReagentAttachment(Map.of(key, 4f)), ignored -> fake("all", all), scaleTarget);
        assertEquals(1, scaleTarget.cryo); assertEquals(1, scaleTarget.growth);
        assertEquals(1, scaleTarget.diethyl); assertEquals(1, scaleTarget.restore);
    }

    @Test void affectGrowthUsesSourceBaseAttributeAmountAndEntityProbabilityDefaults() {
        var parsed = PlantMetabolismData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"type\":\"PlantAffectGrowth\"}")).getOrThrow();
        var growth = (PlantMetabolismData.PlantAffectGrowth) parsed;
        assertEquals(1f, growth.amount());
        assertEquals(1f, growth.probability());
        var defaultPhalan = (PlantMetabolismData.PlantPhalanximine) PlantMetabolismData.CODEC.parse(
                com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"type\":\"PlantPhalanximine\"}")).getOrThrow();
        assertEquals(0f, defaultPhalan.minScale());
        assertEquals(1f, defaultPhalan.probability());
    }

    @Test
    void dispatchesByResolvedMetadataAndDoesNotApplyFractionalReagent() {
        ReagentData basis = fake("water", List.of(new PlantMetabolismData.PlantAdjustNutrition(3f)));
        ReagentData otherId = fake("unrelated-id", basis.plantMetabolism());
        ResourceKey<ReagentData> custom = ModReagents.createKey("custom");
        ResourceKey<ReagentData> fraction = ModReagents.createKey("fraction");
        Map<ResourceKey<ReagentData>, ReagentData> lookup = Map.of(custom, otherId, fraction,
                fake("fraction", List.of(new PlantMetabolismData.PlantAdjustWater(9f))));
        ReagentAttachment mixture = new ReagentAttachment(Map.of(custom, 1.25f, fraction, .5f));
        Target target = new Target();
        PlantReagentMetabolism.poll(mixture, lookup::get, target);
        assertEquals(0f, target.water);
        assertEquals(3f, target.nutrition);
        assertEquals(Map.of(custom, .25f), mixture.getMap());
    }

    @Test
    void mixedEntriesConsumeExactUnitsAndReportUnsupportedEffects() {
        ResourceKey<ReagentData> water = ModReagents.createKey("w");
        ResourceKey<ReagentData> unsupported = ModReagents.createKey("other");
        ResourceKey<ReagentData> fraction = ModReagents.createKey("fraction");
        Map<ResourceKey<ReagentData>, ReagentData> lookup = new LinkedHashMap<>();
        lookup.put(water, fake("w", List.of(new PlantMetabolismData.PlantAdjustWater(1f))));
        lookup.put(unsupported, fake("other", List.of(new PlantMetabolismData.PlantRemoveKudzu())));
        lookup.put(fraction, fake("fraction", List.of()));
        ReagentAttachment mixture = new ReagentAttachment(Map.of(water, 1f, unsupported, 1f, fraction, .07f));
        Target target = new Target();
        var result = PlantReagentMetabolism.poll(mixture, lookup::get, target);
        assertEquals(1f, target.water);
        assertEquals(List.of(new PlantReagentMetabolism.UnsupportedEffect("other", "PlantRemoveKudzu")),
                result.unsupportedEffects());
        assertTrue(mixture.isEmpty(), "all entries are consumed by one unit, including fractional remainder");
    }

    @Test void careEffectsDispatchWithDefaultsProbabilityGuardsAndDrainOneUnit() {
        var key = ModReagents.createKey("care");
        var effects = List.<PlantMetabolismData>of(new PlantMetabolismData.PlantAdjustHealth(2),
                new PlantMetabolismData.PlantAdjustToxins(-3), new PlantMetabolismData.PlantAdjustWeeds(4),
                new PlantMetabolismData.PlantAdjustPests(5), new PlantMetabolismData.PlantAdjustPotency(6),
                new PlantMetabolismData.RobustHarvest());
        class Care extends Target {
            float health, toxins, weeds, pests, potency; int robust;
            @Override public boolean hasLivingPlant() { return true; }
            @Override public float nextFloat() { return .5f; }
            @Override public void adjustHealth(float amount) { health += amount; }
            @Override public void adjustToxins(float amount) { toxins += amount; }
            @Override public void adjustWeeds(float amount) { weeds += amount; }
            @Override public void adjustPests(float amount) { pests += amount; }
            @Override public void adjustPotency(float amount) { potency += amount; }
            @Override public void robustHarvest(PlantMetabolismData.RobustHarvest effect) { robust++; }
        }
        var care = new Care(); var source = new ReagentAttachment(Map.of(key, 1.25f));
        var result = PlantReagentMetabolism.poll(source, ignored -> fake("care", effects), care);
        assertTrue(result.unsupportedEffects().isEmpty());
        assertEquals(2, care.health); assertEquals(-3, care.toxins); assertEquals(4, care.weeds);
        assertEquals(5, care.pests); assertEquals(6, care.potency); assertEquals(1, care.robust);
        assertEquals(.25f, source.getMap().get(key));
    }

    @Test void effectProbabilityIsSampledBeforeLivingGuardAndDoesNotConsumeEffectRandomStream() {
        var key = ModReagents.createKey("care_probability");
        class Probe extends Target {
            int samples, guards, effectRolls;
            @Override public double probabilitySample() { samples++; return .25d; }
            @Override public boolean hasLivingPlant() { guards++; return false; }
            @Override public float nextFloat() { effectRolls++; return .9f; }
        }
        Probe probe = new Probe();
        var effects = List.<PlantMetabolismData>of(new PlantMetabolismData.PlantAdjustHealth(0f, 1f),
                new PlantMetabolismData.PlantAdjustToxins(1f, 1f));
        PlantReagentMetabolism.poll(new ReagentAttachment(Map.of(key, 1f)), ignored -> fake("care_probability", effects), probe);
        assertEquals(2, probe.samples); assertEquals(1, probe.guards);
        assertEquals(0, probe.effectRolls);
    }

    @Test
    void unknownResolvedReagentFailsBeforeEffectsOrConsumption() {
        ResourceKey<ReagentData> known = ModReagents.createKey("known");
        ResourceKey<ReagentData> missing = ModReagents.createKey("missing");
        ReagentAttachment mixture = new ReagentAttachment(Map.of(known, 1f, missing, .5f));
        Map<ResourceKey<ReagentData>, Long> before = mixture.snapshotUnits();
        int[] cow = {0};
        Target target = new Target() { @Override public void beginNonemptyPoll() { cow[0]++; } };
        assertThrows(NullPointerException.class,
                () -> PlantReagentMetabolism.poll(mixture,
                        key -> key.equals(known) ? fake("known", List.of(new PlantMetabolismData.PlantAdjustWater(1f))) : null,
                        target));
        assertEquals(0f, target.water);
        assertEquals(before, mixture.snapshotUnits());
        assertEquals(0, cow[0], "failed whole-batch resolution must not detach seed identity");
    }

    @Test void invalidChemicalTablePreflightFailsBeforeCowEffectsOrDrain() {
        var key = ModReagents.createKey("invalid_chemical_table");
        var source = new ReagentAttachment(Map.of(key, 1f));
        var before = source.snapshotUnits();
        int[] cow = {0}, effects = {0};
        Target target = new Target() {
            @Override public void beginNonemptyPoll() { cow[0]++; }
            @Override public boolean hasLivingPlant() { return true; }
            @Override public boolean hasChemicalMutationAdapter() { return true; }
            @Override public void prepareChemicalMutation(String tableId) { throw new IllegalArgumentException("invalid table"); }
            @Override public boolean mutateChemicals(String tableId) { effects[0]++; return true; }
        };
        assertThrows(IllegalArgumentException.class, () -> PlantReagentMetabolism.poll(source,
                ignored -> fake("invalid_chemical_table", List.of(new PlantMetabolismData.PlantMutateChemicals("missing_table"))), target));
        assertEquals(0, cow[0], "preflight failure must not detach seed identity");
        assertEquals(0, effects[0]);
        assertEquals(before, source.snapshotUnits(), "preflight failure must not consume reagent");
    }

    @Test void mutationLevelAdmissionSkipsLookupEffectsAndWholeSourceDrainAtTwentyFive() {
        var key = ModReagents.createKey("admission");
        var source = new ReagentAttachment(Map.of(key, 1.5f));
        var before = source.snapshotUnits();
        int[] lookups = {0};
        Target blocked = new Target() {
            @Override public boolean admitsMetabolismPoll() { return false; }
        };
        var result = PlantReagentMetabolism.poll(source, ignored -> {
            lookups[0]++;
            return fake("admission", List.of(new PlantMetabolismData.PlantAdjustWater(10)));
        }, blocked);
        assertTrue(result.unsupportedEffects().isEmpty());
        assertEquals(0, lookups[0], "admission precedes resolve/preflight");
        assertEquals(before, source.snapshotUnits(), "all entries, including fractional residue, remain untouched");
    }

    @Test void mutationLevelAdmissionIsSampledOnceAndCrossingTwentyFiveDoesNotAbortAdmittedPoll() {
        var key = ModReagents.createKey("admission_crossing");
        var source = new ReagentAttachment(Map.of(key, 1f));
        class Crossing extends Target {
            float mutationLevel = 24, nutrition;
            int admissions;
            @Override public boolean admitsMetabolismPoll() { admissions++; return mutationLevel < 25; }
            @Override public boolean hasLivingPlant() { return true; }
            @Override public void adjustMutationLevel(float amount) { mutationLevel += amount; }
            @Override public void adjustNutrition(float amount) { nutrition += amount; }
        }
        var target = new Crossing();
        PlantReagentMetabolism.poll(source, ignored -> fake("admission_crossing", List.of(
                new PlantMetabolismData.PlantAdjustMutationLevel(1),
                new PlantMetabolismData.PlantAdjustNutrition(3))), target);
        assertEquals(1, target.admissions);
        assertEquals(25, target.mutationLevel);
        assertEquals(3, target.nutrition, "an admitted batch continues after its level crosses the threshold");
        assertTrue(source.isEmpty(), "drain remains part of the admitted whole poll");
    }

    @Test void unknownMutationTablePreflightPreventsEarlierEffectAndSourceCommit() {
        ResourceKey<ReagentData> water = ModReagents.createKey("water");
        ResourceKey<ReagentData> mutator = ModReagents.createKey("mutator");
        var mixture = new ReagentAttachment(Map.of(water, 1f, mutator, 1f));
        var before = mixture.snapshotUnits(); Target target = new Target() {
            @Override public boolean hasLivingPlant() { return true; }
            @Override public boolean hasChemicalMutationAdapter() { return true; }
            @Override public void prepareChemicalMutation(String id) { throw new IllegalArgumentException("unknown table"); }
        };
        assertThrows(IllegalArgumentException.class, () -> PlantReagentMetabolism.poll(mixture, key ->
                key.equals(water) ? fake("water", List.of(new PlantMetabolismData.PlantAdjustWater(5)))
                        : fake("mutator", List.of(new PlantMetabolismData.PlantMutateChemicals("unknown"))), target));
        assertEquals(0, target.water);
        assertEquals(before, mixture.snapshotUnits());
    }
    @Test void inactiveChemicalMutationSkipsTablePreflightButConsumesOrdinaryPollEffects() {
        ResourceKey<ReagentData> water = ModReagents.createKey("water");
        ResourceKey<ReagentData> mutator = ModReagents.createKey("mutator");
        var mixture = new ReagentAttachment(Map.of(water, 1f, mutator, 1f));
        Target inactive = new Target() {
            @Override public boolean hasChemicalMutationAdapter() { return true; }
            @Override public boolean hasLivingPlant() { return false; }
            @Override public void prepareChemicalMutation(String id) { fail("inactive plant must not resolve mutation table"); }
            @Override public boolean mutateChemicals(String id) { return true; }
            @Override public void adjustWater(float amount) { super.adjustWater(amount); }
        };
        var result = PlantReagentMetabolism.poll(mixture, key -> key.equals(water)
                ? fake("water", List.of(new PlantMetabolismData.PlantAdjustWater(2)))
                : fake("mutator", List.of(new PlantMetabolismData.PlantMutateChemicals("unavailable-table"))), inactive);
        assertEquals(2f, inactive.water);
        assertTrue(result.unsupportedEffects().isEmpty());
        assertTrue(mixture.isEmpty(), "inactive effect remains a no-op but ordinary poll drain occurs");
    }
    @Test void mutationDescriptorWithoutAdapterIsReportedUnsupported() {
        var mutator = ModReagents.createKey("mutator");
        var mixture = new ReagentAttachment(Map.of(mutator, 1f));
        var result = PlantReagentMetabolism.poll(mixture,
                key -> fake("mutator", List.of(new PlantMetabolismData.PlantMutateChemicals("evilrandomfillsolution"))),
                new Target() { @Override public boolean hasLivingPlant() { return true; } });
        assertEquals(List.of(new PlantReagentMetabolism.UnsupportedEffect("mutator", "PlantMutateChemicals")), result.unsupportedEffects());
        assertTrue(mixture.isEmpty());
    }

    @Test void inactiveChemicalMutationWithoutAdapterIsNoOpAndNotReportedUnsupported() {
        var water = ModReagents.createKey("water");
        var mutator = ModReagents.createKey("mutator");
        var mixture = new ReagentAttachment(Map.of(water, 1f, mutator, 1f));
        Target inactive = new Target() {
            @Override public boolean hasLivingPlant() { return false; }
        };
        var result = PlantReagentMetabolism.poll(mixture, key -> key.equals(water)
                ? fake("water", List.of(new PlantMetabolismData.PlantAdjustWater(1)))
                : fake("mutator", List.of(new PlantMetabolismData.PlantMutateChemicals("missing-table"))), inactive);
        assertEquals(1f, inactive.water);
        assertTrue(result.unsupportedEffects().isEmpty());
        assertTrue(mixture.isEmpty());
    }

    @Test void mutationLevelAndModifierDescriptorsApplyResolvedEffectsWithModifierScaling() {
        var level = ModReagents.createKey("level");
        var mod = ModReagents.createKey("mod");
        var mixture = new ReagentAttachment(Map.of(level, 1f));
        class MutationTarget extends Target {
            float levelValue, modifier = 1.5f;
            @Override public boolean hasLivingPlant() { return true; }
            @Override public void adjustMutationLevel(float amount) { levelValue += amount * modifier; }
            @Override public void adjustMutationModifier(float amount) { modifier += amount; }
            @Override public float nextFloat() { return .25f; }
        }
        var target = new MutationTarget();
        PlantReagentMetabolism.poll(mixture, key -> fake("level", List.of(new PlantMetabolismData.PlantAdjustMutationLevel(2.25f))), target);
        var modifierMix = new ReagentAttachment(Map.of(mod, 1f));
        PlantReagentMetabolism.poll(modifierMix, key -> fake("mod", List.of(new PlantMetabolismData.PlantAdjustMutationMod(1f, .5f))), target);
        assertEquals(3.375f, target.levelValue, .0001f);
        assertEquals(2f, target.modifier);
        assertTrue(mixture.isEmpty()); assertTrue(modifierMix.isEmpty());
    }

    private static com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<ReagentData> resolvedCatalog()
            throws IOException {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModReagents.REAGENT_TYPE);
        manager.reload(ModReagents.REAGENT_TYPE, ReagentResourceSmokeTest.loadResources());
        return manager.snapshot(ModReagents.REAGENT_TYPE);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", path);
    }

    private static ReagentData fake(String id, List<PlantMetabolismData> effects) {
        return new ReagentData(id, id, "test", "", "", "", 0, Map.of(), 0f, 0f,
                com.juicyslew.moonstation14.util.enums.ContrabandSeverityEnum.NONE, effects, Map.of(),
                new com.juicyslew.moonstation14.component.codec.json.metamorphic.MetamorphicSpriteData(null, null),
                0f, "fill-", false, 0f);
    }

    private static class Target implements PlantReagentMetabolism.Target {
        float water;
        float nutrition;
        @Override public double probabilitySample() { return 0d; }
        @Override public void cryoxadone() { }
        @Override public void affectGrowth(int amount) { }
        @Override public void diethylamine() { }
        @Override public void phalanximine() { }
        @Override public void restoreSeeds() { }
        @Override public void adjustWater(float amount) { water += amount; }
        @Override public void adjustNutrition(float amount) { nutrition += amount; }
    }
}
