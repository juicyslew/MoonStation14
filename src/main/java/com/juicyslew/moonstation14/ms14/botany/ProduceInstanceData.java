package com.juicyslew.moonstation14.ms14.botany;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Reproducible harvest provenance; live remaining chemistry is held only by REAGENT. */
public record ProduceInstanceData(ResourceLocation seed, float baselinePotency, int generatedCapacityCents) {
    public static final Codec<ProduceInstanceData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("seed").forGetter(ProduceInstanceData::seed),
            Codec.floatRange(0, 10000).fieldOf("baseline_potency").forGetter(ProduceInstanceData::baselinePotency),
            Codec.intRange(0, 1000000).fieldOf("generated_capacity_cents").forGetter(ProduceInstanceData::generatedCapacityCents)
    ).apply(i, ProduceInstanceData::new));
    public ProduceInstanceData { if (seed == null || !Float.isFinite(baselinePotency) || baselinePotency < 0 || generatedCapacityCents < 0) throw new IllegalArgumentException("invalid produce metadata"); }
}
