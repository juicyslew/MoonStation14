package com.juicyslew.moonstation14.ms14.botany;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Fixture-only, deterministic genetics for the mutation showcase trays. */
public final class BotanyMutationShowcase {
    public static final int STATIONS = SmallBotanyFixtureLayout.SHOWCASE_STATIONS;
    private static final ResourceLocation WHEAT = ResourceLocation.fromNamespaceAndPath("moonstation14", "wheat");
    private static final ResourceLocation MEATWHEAT = ResourceLocation.fromNamespaceAndPath("moonstation14", "meatwheat");

    private BotanyMutationShowcase() { }

    static String arrange(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos origin) {
        var seeds = ModSeedProfiles.serverCatalog();
        var wheatProfile = seeds == null ? null : seeds.get(WHEAT);
        var meatwheatId = ResourceLocation.fromNamespaceAndPath("moonstation14", "meatwheat");
        var meatwheatProfile = seeds == null ? null : seeds.get(meatwheatId);
        if (wheatProfile == null || meatwheatProfile == null)
            return "Scenario refused: resolved wheat/meatwheat profiles are unavailable.";
        var tables = ModPlantMutations.serverCatalog();
        var mutationTable = tables == null ? null : tables.get(ModPlantMutations.tableId("random_plant_mutations"));
        if (mutationTable == null) return "Scenario refused: resolved random plant mutation table is unavailable.";
        var slipify = mutationTable.mutations().stream().filter(e -> e.effectKind() == PlantMutationTable.EffectKind.SLIPIFY)
                .findFirst().orElse(null);
        if (slipify == null) return "Scenario refused: resolved Slipify mutation row is unavailable.";
        final WeightedRandomFillSolution chemical;
        try {
            chemical = PlantChemicalMutation.prepare("EvilRandomFillSolution", ModWeightedRandomFillSolutions.serverCatalog(),
                    com.juicyslew.moonstation14.ms14.reagent.ModReagents.catalog(level));
        } catch (RuntimeException exception) {
            return "Scenario refused: resolved EvilRandomFillSolution is unavailable (" + exception.getMessage() + ").";
        }
        var wheat = new GeneticValue(WHEAT, wheatProfile);
        var meat = new GeneticValue(meatwheatId, meatwheatProfile);
        var presets = new java.util.ArrayList<GeneticValue>(STATIONS);
        try {
            for (int i = 0; i < STATIONS; i++) presets.add(specimen(i, wheat, meat, slipify, chemical));
        } catch (RuntimeException exception) {
            return "Scenario refused: showcase preset preflight failed (" + exception.getMessage() + ").";
        }
        var trays = new java.util.ArrayList<HydroponicsTrayBlockEntity>(STATIONS);
        for (int i = 0; i < STATIONS; i++) {
            var tray = level.getBlockEntity(SmallBotanyFixtureLayout.showcaseTray(origin, i));
            if (!(tray instanceof HydroponicsTrayBlockEntity hydroponics) || !hydroponics.isLoadedValid()
                    || hydroponics.plantState() == null)
                return "Scenario refused: showcase tray " + i + " is not initialized.";
            trays.add(hydroponics);
        }
        long now = level.getGameTime();
        for (int i = 0; i < STATIONS; i++) {
            var tray = trays.get(i);
            GeneticValue genetics = presets.get(i);
            if (i < 6) {
                tray.commitPlant(genetics, now);
                var plant = tray.plantState();
                plant.soil(100, 100, 0, 0);
                plant.pestLevel(0);
                plant.state(i == 2 || i == 3 ? 6 : 2, genetics.profile().biology().endurance(), 0, 0, false, false);
                plant.touch();
            } else if (i < 10) {
                BotanyFixtureScenarios.setReady(tray, genetics.species(), genetics.profile(), now);
                tray.plantState().genetics(genetics);
                tray.plantState().touch();
            } else {
                tray.commitPlant(genetics, now);
                var plant = tray.plantState();
                plant.soil(100, 100, 0, 0);
                plant.pestLevel(0);
                plant.state(2, genetics.profile().biology().endurance(), 0, 0, false, false);
                plant.touch();
            }
            tray.commitMutation();
        }
        return "Arranged all twelve resolved mutation showcase specimens.";
    }

    /** Builds the paired viability/lifespan/pressure genetics from a fully resolved wheat profile. */
    static GeneticValue specimen(int index, GeneticValue wheat, GeneticValue meatwheat,
                                 PlantMutationTable.Entry slipify, WeightedRandomFillSolution chemicalTable) {
        if (index < 0 || index >= STATIONS) throw new IllegalArgumentException("showcase station index");
        if (wheat == null || !WHEAT.equals(wheat.species()))
            throw new IllegalArgumentException("showcase requires resolved wheat genetics");
        var profile = wheat.profile();
        var life = profile.lifecycle();
        var biology = profile.biology();
        return switch (index) {
            case 0, 1 -> new GeneticValue(WHEAT, withLifecycle(profile, new SeedProfile.Lifecycle(
                    life.growthStages(), life.lifespan(), life.maturation(), life.production(), life.yield(), life.potency(),
                    life.harvest(), index == 0, life.seedless(), life.ligneous())));
            case 2, 3 -> new GeneticValue(WHEAT, withLifecycle(profile, new SeedProfile.Lifecycle(
                    life.growthStages(), index == 2 ? life.lifespan() : 5, life.maturation(), life.production(), life.yield(),
                    life.potency(), life.harvest(), life.viable(), life.seedless(), life.ligneous())));
            case 4, 5 -> new GeneticValue(WHEAT, withBiology(profile, new SeedProfile.Biology(
                    biology.endurance(), biology.nutrientConsumption(), biology.waterConsumption(), biology.idealHeat(),
                    biology.heatTolerance(), biology.lowPressure(), index == 4 ? biology.highPressure() : 85,
                    biology.weedTolerance(), biology.weedHighLevel(), biology.toxinTolerance(), biology.pestTolerance())));
            case 6 -> {
                if (slipify == null || slipify.effectKind() != PlantMutationTable.EffectKind.SLIPIFY)
                    throw new IllegalArgumentException("showcase requires a resolved Slipify mutation entry");
                var record = new PlantMutationRecord(slipify.name(), slipify.appliesToPlant(),
                        slipify.appliesToProduce(), slipify.persists(), slipify.slippery().orElseThrow());
                yield wheat.withRecordedMutation(record);
            }
            case 7 -> {
                if (chemicalTable == null) throw new IllegalArgumentException("showcase requires resolved EvilRandomFillSolution");
                yield PlantChemicalMutation.apply(wheat, chemicalTable, () -> .5f);
            }
            case 8 -> wheat;
            case 9 -> {
                if (meatwheat == null || !MEATWHEAT.equals(meatwheat.species()))
                    throw new IllegalArgumentException("showcase requires resolved meatwheat genetics");
                if (!profile.mutationTargets().contains(MEATWHEAT))
                    throw new IllegalArgumentException("resolved wheat does not target meatwheat");
                yield wheat.transformSpecies(MEATWHEAT, meatwheat.profile());
            }
            case 10, 11 -> {
                var consume = index == 10 ? Map.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN, .02f) : profile.consumeGasses();
                var exude = index == 11 ? Map.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN, .01f) : profile.exudeGasses();
                yield new GeneticValue(WHEAT, withGasses(profile, consume, exude));
            }
            default -> throw new AssertionError(index);
        };
    }

    private static SeedProfile withLifecycle(SeedProfile p, SeedProfile.Lifecycle l) {
        return new SeedProfile(p.packet(), p.product(), p.visualKey(), p.biology(), l, p.chemicals(), p.consumeGasses(),
                p.exudeGasses(), p.mutationTargets(), p.scream(), p.sourceName());
    }
    private static SeedProfile withBiology(SeedProfile p, SeedProfile.Biology b) {
        return new SeedProfile(p.packet(), p.product(), p.visualKey(), b, p.lifecycle(), p.chemicals(), p.consumeGasses(),
                p.exudeGasses(), p.mutationTargets(), p.scream(), p.sourceName());
    }
    private static SeedProfile withGasses(SeedProfile p, Map<com.juicyslew.moonstation14.ms14.atmos.core.GasType, Float> consume,
                                          Map<com.juicyslew.moonstation14.ms14.atmos.core.GasType, Float> exude) {
        return new SeedProfile(p.packet(), p.product(), p.visualKey(), p.biology(), p.lifecycle(), p.chemicals(),
                new LinkedHashMap<>(consume), new LinkedHashMap<>(exude), p.mutationTargets(), p.scream(), p.sourceName());
    }
}
