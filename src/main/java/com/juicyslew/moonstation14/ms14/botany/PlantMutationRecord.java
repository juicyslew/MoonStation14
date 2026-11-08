package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.ms14.slip.ItemSlipperyComposition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Captured upstream mutation descriptor; effect values are snapshots, not table references. */
public record PlantMutationRecord(String name, boolean appliesToPlant, boolean appliesToProduce,
                                  boolean persists, ItemSlipperyComposition slippery) {
    public static final Codec<PlantMutationRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(PlantMutationRecord::name),
            Codec.BOOL.fieldOf("applies_to_plant").forGetter(PlantMutationRecord::appliesToPlant),
            Codec.BOOL.fieldOf("applies_to_produce").forGetter(PlantMutationRecord::appliesToProduce),
            Codec.BOOL.fieldOf("persists").forGetter(PlantMutationRecord::persists),
            ItemSlipperyComposition.CODEC.codec().fieldOf("slippery").forGetter(PlantMutationRecord::slippery)
    ).apply(i, PlantMutationRecord::new));
    public PlantMutationRecord {
        if (name == null || name.isBlank() || slippery == null) throw new IllegalArgumentException("invalid mutation record");
    }
}
