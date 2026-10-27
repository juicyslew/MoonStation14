package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.Supplier;

/** Shared immutable-profile mutation owner used by reagent polling and mutation effects. */
public final class PlantChemicalMutation {
    private PlantChemicalMutation() { }
    public static WeightedRandomFillSolution prepare(String sourceId,
            PrototypeCatalog<WeightedRandomFillSolution> tables,
            PrototypeCatalog<ReagentData> reagents) {
        ResourceLocation id = ModWeightedRandomFillSolutions.canonicalId(sourceId);
        WeightedRandomFillSolution table = tables == null ? null : tables.get(id);
        if (table == null) throw new IllegalArgumentException("Unknown weighted fill table: " + id);
        if (reagents == null) throw new IllegalStateException("Reagent catalog unavailable");
        for (var group : table.fills()) for (var reagent : group.reagents())
            if (reagents.get(reagent) == null) throw new IllegalArgumentException("Unknown reagent " + reagent + " in " + id);
        return table;
    }
    public static GeneticValue apply(GeneticValue value, WeightedRandomFillSolution table, Supplier<Float> random) {
        Objects.requireNonNull(value); Objects.requireNonNull(table); Objects.requireNonNull(random);
        var selection = table.select(random::get);
        long sampleCents = BotanyFixedPoint.fromFloat(random.get() * selection.quantity());
        long amountCents = Math.max(1, sampleCents);
        SeedProfile profile = value.profile();
        var chemicals = new LinkedHashMap<>(profile.chemicals());
        SeedProfile.Chemical existing = chemicals.get(selection.reagent());
        if (existing == null) {
            long minCents = Math.max(1, BotanyFixedPoint.fromFloat(Math.max(.01f, Math.min(1f, selection.quantity() / 5f))));
            long maxCents = Math.addExact(minCents, amountCents);
            float min = BotanyFixedPoint.toFloat(minCents), max = BotanyFixedPoint.toFloat(maxCents);
            chemicals.put(selection.reagent(), new SeedProfile.Chemical(min, max, 100f / max, false));
        } else chemicals.put(selection.reagent(), existing.extendCents(amountCents));
        return new GeneticValue(value.species(), new SeedProfile(profile.packet(), profile.product(), profile.visualKey(),
                profile.biology(), profile.lifecycle(), chemicals, profile.consumeGasses(), profile.exudeGasses(), profile.mutationTargets(), profile.scream(), profile.sourceName()), value.mutations());
    }
}
