package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.function.Supplier;

/** Server-authoritative supported subset of the source mutation-cycle table. */
public final class PlantMutationSystem {
    public record Result(float severity, List<String> unsupported) { public Result { unsupported = List.copyOf(unsupported); } }
    private PlantMutationSystem() { }

    public static Result cycle(PlantInstance plant, PlantMutationTable table, RandomGenerator random,
                               Supplier<Float> chemicalRandom, PrototypeCatalog<WeightedRandomFillSolution> chemicalTables,
                               PrototypeCatalog<ReagentData> reagents) {
        return cycle(plant, table, random, chemicalRandom, chemicalTables, reagents, null);
    }
    public static Result cycle(PlantInstance plant, PlantMutationTable table, RandomGenerator random,
                               Supplier<Float> chemicalRandom, PrototypeCatalog<WeightedRandomFillSolution> chemicalTables,
                               PrototypeCatalog<ReagentData> reagents, PrototypeCatalog<SeedProfile> seedProfiles) {
        if (plant == null || plant.seed() == null || plant.dead() || plant.genetics() == null || plant.mutationLevel() <= 0)
            return new Result(0, List.of());
        float severity = Math.min(plant.mutationLevel(), 25f);
        // Validate every chemical target before any rolls can commit another genetic change.
        java.util.Map<PlantMutationTable.Entry, WeightedRandomFillSolution> prepared = new java.util.IdentityHashMap<>();
        for (var entry : table.mutations()) if (entry.appliesToPlant() && entry.effectKind() == PlantMutationTable.EffectKind.CHEMICALS)
            prepared.put(entry, PlantChemicalMutation.prepare(entry.chemicalTable().orElseThrow().toString(), chemicalTables, reagents));
        // Invalid configured effects fail atomically before detachment; once this mutation
        // cycle is admitted and fully prepared, isolate its seed before any roll/effect.
        plant.ensureUniqueSeed();
        GeneticValue value = plant.genetics();
        boolean speciesReplacement = false;
        List<String> unsupported = new ArrayList<>();
        for (var entry : table.mutations()) {
            double chance = Math.min(entry.baseOdds() * severity, 1d);
            boolean success = random.nextDouble() < chance;
            if (!success) continue;
            if (entry.effectKind() == PlantMutationTable.EffectKind.SLIPIFY && entry.persists())
                value = value.withRecordedMutation(new PlantMutationRecord(entry.name(), entry.appliesToPlant(),
                        entry.appliesToProduce(), entry.persists(), entry.slippery().orElseThrow()));
            if (!entry.appliesToPlant()) {
                if (entry.effectKind() == PlantMutationTable.EffectKind.CAPABILITY)
                    unsupported.add(entry.name() + ":" + entry.capability());
                continue;
            }
            switch (entry.effectKind()) {
                case STAT -> value = entry.stat().orElseThrow().apply(value, random);
                case CHEMICALS -> {
                    var fill = prepared.get(entry);
                    value = PlantChemicalMutation.apply(value, fill, chemicalRandom);
                }
                case GAS -> value = entry.gas().orElseThrow().apply(value, random);
                case HARVEST -> value = entry.harvest().orElseThrow().apply(value);
                case SPECIES -> {
                    var targets = value.profile().mutationTargets();
                    if (targets.isEmpty()) break;
                    var targetId = targets.get(random.nextInt(targets.size()));
                    var target = seedProfiles == null ? null : seedProfiles.get(targetId);
                    if (target == null) {
                        unsupported.add(entry.name() + ":species_target_missing:" + targetId);
                        break;
                    }
                    value = value.transformSpecies(targetId, target);
                    speciesReplacement = true;
                }
                case CAPABILITY -> unsupported.add(entry.name() + ":" + entry.capability());
                case SLIPIFY -> { /* Captured genetic descriptor; it does not affect the living plant. */ }
            }
        }
        if (value != plant.genetics()) {
            if (speciesReplacement || !value.species().equals(plant.seed())) plant.transitionSpecies(value);
            else plant.genetics(value);
        }
        plant.consumeMutationLevel();
        return new Result(severity, unsupported);
    }
}
