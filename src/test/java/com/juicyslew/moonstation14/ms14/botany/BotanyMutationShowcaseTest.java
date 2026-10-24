package com.juicyslew.moonstation14.ms14.botany;

import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSigns;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BotanyMutationShowcaseTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void everyResolvedMainMutationRowIsMappedOrExplicitlyUnsupported() {
        var rows=PlantMutationCycleTest.sourceTable().mutations().stream().map(PlantMutationTable.Entry::name).toList();
        var coverage=BotanyFixtureSigns.mutationShowcaseCoverage();
        var genes=coverage.stream().map(BotanyFixtureSigns.MutationShowcaseCoverage::mutation).toList();
        assertEquals(genes.size(),genes.stream().distinct().count());
        assertEquals(new java.util.HashSet<>(rows), new java.util.HashSet<>(genes), "coverage must match every resolved random_plant_mutations row");
        assertEquals(27,coverage.size());
        assertEquals(25,coverage.stream().filter(BotanyFixtureSigns.MutationShowcaseCoverage::supported).count());
        assertEquals(List.of("Sentient","Kudzufication"),coverage.stream().filter(c->!c.supported()).map(BotanyFixtureSigns.MutationShowcaseCoverage::mutation).toList());
        for(var row:coverage) {
            assertFalse(row.action().isBlank()); assertFalse(row.expected().isBlank());
            if(row.supported()) assertFalse(row.stations().isEmpty(),row.mutation()+" lacks a physical station");
            else assertTrue(row.stations().isEmpty(),row.mutation()+" unsupported row must not fake a station");
        }
        var main = new net.minecraft.core.BlockPos(0,64,0);
        var careOrigin = SmallBotanyFixtureLayout.careOrigin(main);
        var stations = java.util.stream.Stream.of(
                BotanyFixtureSigns.labels(main),
                BotanyFixtureSigns.careLabels(careOrigin),
                BotanyFixtureSigns.toleranceLabels(careOrigin))
                .flatMap(List::stream).map(BotanyFixtureSigns.Label::title).toList();
        assertTrue(stations.containsAll(List.of("AO VIABLE","AP UNVIABLE","AQ LIFE 25","AR LIFE 5","AS PRESS 121","AT PRESS 85","AU SLIPPERY","AV CHEMICAL","AW WHEAT","AX MEAT WHEAT","AY NITROGEN","AZ OXYGEN")));
        var prefixes = BotanyFixtureSigns.mutationShowcaseCoverage().stream().filter(BotanyFixtureSigns.MutationShowcaseCoverage::supported)
                .flatMap(c -> c.stations().stream()).distinct().toList();
        assertTrue(prefixes.stream().allMatch(prefix -> stations.stream().anyMatch(title -> title.startsWith(prefix + " "))),
                "every supported mutation station prefix must have a label");
    }

    @Test void pairedPresetsChangeOnlyTheirIntendedFieldsAndPreserveResolvedComposition() {
        var profile = BotanyTestSeedFixtures.resolved(id("wheat"));
        var wheat = new GeneticValue(id("wheat"), profile);
        for (int i = 0; i < 6; i++) {
            var value = BotanyMutationShowcase.specimen(i, wheat, null, null, null);
            assertEquals(profile.packet(), value.profile().packet());
            assertEquals(profile.product(), value.profile().product());
            assertEquals(profile.visualKey(), value.profile().visualKey());
            assertEquals(profile.chemicals(), value.profile().chemicals());
            assertEquals(profile.mutationTargets(), value.profile().mutationTargets());
            assertEquals(profile.biology().endurance(), value.profile().biology().endurance());
            switch (i) {
                case 0 -> assertEquals(profile.lifecycle().viable(), value.profile().lifecycle().viable());
                case 1 -> assertNotEquals(profile.lifecycle().viable(), value.profile().lifecycle().viable());
                case 2 -> assertEquals(profile.lifecycle().lifespan(), value.profile().lifecycle().lifespan());
                case 3 -> assertEquals(5f, value.profile().lifecycle().lifespan());
                case 4 -> assertEquals(profile.biology().highPressure(), value.profile().biology().highPressure());
                case 5 -> assertEquals(85f, value.profile().biology().highPressure());
            }
        }
        assertThrows(IllegalArgumentException.class, () -> BotanyMutationShowcase.specimen(-1, wheat, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> BotanyMutationShowcase.specimen(6, wheat, null, null, null));
    }

    @Test void speciesPresetRequiresResolvedReachableTargetAndGasPresetsAreExclusive() {
        var wheatProfile = BotanyTestSeedFixtures.resolved(id("wheat"));
        var wheat = new GeneticValue(id("wheat"), wheatProfile);
        assertTrue(wheatProfile.mutationTargets().contains(id("meatwheat")), "resolved wheat composition must expose reachable meatwheat target");
        var meat = new GeneticValue(id("meatwheat"), BotanyTestSeedFixtures.resolved(id("meatwheat")));
        var transformed = BotanyMutationShowcase.specimen(9, wheat, meat, null, null);
        assertEquals(id("meatwheat"), transformed.species());
        assertEquals(meat.profile().product(), transformed.profile().product());
        var table = PlantMutationCycleTest.sourceTable();
        var slipify = table.mutations().stream().filter(e -> e.name().equals("Slippery")).findFirst().orElseThrow();
        var chemistry = PlantMutationCycleTest.chemicalCatalogs();
        var slippery = BotanyMutationShowcase.specimen(6, wheat, meat, slipify, null);
        assertEquals("Slippery", slippery.mutations().getFirst().name());
        var chemical = BotanyMutationShowcase.specimen(7, wheat, meat, slipify, chemistry.tables().get(id("randompickbotanyreagent")));
        assertNotEquals(wheatProfile.chemicals(), chemical.profile().chemicals());
        var nitrogen = BotanyMutationShowcase.specimen(10, wheat, null, null, null).profile();
        var oxygen = BotanyMutationShowcase.specimen(11, wheat, null, null, null).profile();
        assertEquals(java.util.Map.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN, .02f), nitrogen.consumeGasses());
        assertEquals(java.util.Map.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN, .01f), oxygen.exudeGasses());
    }

    @Test void ordinaryMetabolismDamagesSusceptibleShowcaseMutantsWithoutKillingThem() {
        var wheatProfile = BotanyTestSeedFixtures.resolved(id("wheat"));
        var wheat = new GeneticValue(id("wheat"), wheatProfile);
        var tray = new TrayProfile(60, 300, 100, 100, true, 20000, 5000);
        var environment = Optional.of(new PlantMetabolismEvaluator.Environment(92.6f, 293.15f));
        for (int controlIndex : List.of(0, 2, 4)) {
                var pairPlants = new java.util.ArrayList<PlantInstance>();
                var control = BotanyMutationShowcase.specimen(controlIndex, wheat, null, null, null);
                var mutant = BotanyMutationShowcase.specimen(controlIndex + 1, wheat, null, null, null);
                for (var genetics : List.of(control, mutant)) {
                    var plant = new PlantInstance(100, 100, 0);
                    plant.plant(genetics.species(), genetics.profile(), 0);
                    plant.soil(100, 100, 0, 0);
                    plant.pestLevel(0);
                    plant.state(controlIndex == 2 ? 6 : 2, genetics.profile().biology().endurance(), 0, 0, false, false);
                    PlantMetabolismEvaluator.cycle(plant, tray, genetics.profile(), 300,
                            new ControlledExposureRandom(), environment);
                    pairPlants.add(plant);
                }
                assertFalse(pairPlants.get(0).dead(), "control remains alive for pair " + controlIndex);
                assertFalse(pairPlants.get(1).dead(), "mutant remains alive for pair " + controlIndex);
                assertEquals(100, pairPlants.get(0).health(), "control is undamaged for pair " + controlIndex);
                assertTrue(pairPlants.get(1).health() < 100, "susceptibility causes ordinary health loss for pair " + controlIndex);
        }
    }

    private static final class ControlledExposureRandom implements java.util.random.RandomGenerator {
        @Override public float nextFloat() { return .99f; }
        @Override public int nextInt(int origin, int bound) { return origin; }
        @Override public long nextLong() { return 0; }
    }
}
