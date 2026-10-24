package com.juicyslew.moonstation14.ms14.botany;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.ms14.slip.ItemSlipperyComposition;
import java.util.List;
import java.util.Optional;

/** Source-ordered random mutation table. Entries preserve unsupported effects as explicit capabilities. */
public record PlantMutationTable(List<Entry> mutations) {
    public enum EffectKind { STAT, CHEMICALS, GAS, HARVEST, SPECIES, CAPABILITY, SLIPIFY }
    public record Entry(String name, float baseOdds, boolean appliesToPlant, boolean appliesToProduce,
                        boolean persists, EffectKind effectKind, Optional<PlantStatMutation> stat,
                        Optional<ResourceLocation> chemicalTable, String capability, Optional<PlantGasMutation> gas,
                        Optional<PlantHarvestMutation> harvest, Optional<ItemSlipperyComposition> slippery) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Entry::name), Codec.FLOAT.fieldOf("base_odds").forGetter(Entry::baseOdds),
                Codec.BOOL.optionalFieldOf("applies_to_plant", true).forGetter(Entry::appliesToPlant),
                Codec.BOOL.optionalFieldOf("applies_to_produce", true).forGetter(Entry::appliesToProduce),
                Codec.BOOL.optionalFieldOf("persists", true).forGetter(Entry::persists),
                Codec.STRING.xmap(EffectKind::valueOf, EffectKind::name).fieldOf("effect_kind").forGetter(Entry::effectKind),
                PlantStatMutation.CODEC.optionalFieldOf("stat").forGetter(Entry::stat),
                ResourceLocation.CODEC.optionalFieldOf("chemical_table").forGetter(Entry::chemicalTable),
                Codec.STRING.optionalFieldOf("capability", "").forGetter(Entry::capability),
                PlantGasMutation.CODEC.optionalFieldOf("gas").forGetter(Entry::gas),
                PlantHarvestMutation.CODEC.optionalFieldOf("harvest").forGetter(Entry::harvest),
                ItemSlipperyComposition.CODEC.codec().optionalFieldOf("slippery").forGetter(Entry::slippery)
        ).apply(i, Entry::new));
        public Entry(String name, float baseOdds, boolean appliesToPlant, boolean appliesToProduce, boolean persists,
                     EffectKind effectKind, Optional<PlantStatMutation> stat, Optional<ResourceLocation> chemicalTable, String capability) {
            this(name, baseOdds, appliesToPlant, appliesToProduce, persists, effectKind, stat, chemicalTable, capability, Optional.empty(), Optional.empty(), Optional.empty());
        }
        public Entry(String name, float baseOdds, boolean appliesToPlant, boolean appliesToProduce, boolean persists,
                     EffectKind effectKind, Optional<PlantStatMutation> stat, Optional<ResourceLocation> chemicalTable,
                     String capability, Optional<PlantGasMutation> gas) {
            this(name, baseOdds, appliesToPlant, appliesToProduce, persists, effectKind, stat, chemicalTable, capability, gas, Optional.empty(), Optional.empty());
        }
        public Entry(String name, float baseOdds, boolean appliesToPlant, boolean appliesToProduce, boolean persists,
                     EffectKind effectKind, Optional<PlantStatMutation> stat, Optional<ResourceLocation> chemicalTable,
                     String capability, Optional<PlantGasMutation> gas, Optional<PlantHarvestMutation> harvest) {
            this(name, baseOdds, appliesToPlant, appliesToProduce, persists, effectKind, stat, chemicalTable, capability, gas, harvest, Optional.empty());
        }
        public Entry {
            stat = stat == null ? Optional.empty() : stat;
            chemicalTable = chemicalTable == null ? Optional.empty() : chemicalTable;
            gas = gas == null ? Optional.empty() : gas;
            harvest = harvest == null ? Optional.empty() : harvest;
            slippery = slippery == null ? Optional.empty() : slippery;
            if (name == null || name.isBlank() || !Float.isFinite(baseOdds) || baseOdds < 0 || baseOdds > 1 || effectKind == null)
                throw new IllegalArgumentException("invalid mutation entry");
            if ((effectKind == EffectKind.STAT) != stat.isPresent() || (effectKind == EffectKind.CHEMICALS) != chemicalTable.isPresent()
                    || (effectKind == EffectKind.GAS) != gas.isPresent()
                    || (effectKind == EffectKind.HARVEST) != harvest.isPresent()
                    || (effectKind == EffectKind.SLIPIFY) != slippery.isPresent()
                    || (effectKind == EffectKind.SLIPIFY && appliesToPlant)
                    || (effectKind == EffectKind.CAPABILITY && (capability == null || capability.isBlank())))
                throw new IllegalArgumentException("mutation effect payload does not match kind: " + name);
        }
    }
    public static final Codec<PlantMutationTable> CODEC = RecordCodecBuilder.create(i -> i.group(
            Entry.CODEC.listOf().fieldOf("mutations").forGetter(PlantMutationTable::mutations)
    ).apply(i, PlantMutationTable::new));
    public PlantMutationTable { mutations = List.copyOf(mutations); if (mutations.isEmpty()) throw new IllegalArgumentException("empty mutation table"); }
}
