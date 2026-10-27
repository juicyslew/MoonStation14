package com.juicyslew.moonstation14.ms14.reagent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;

/** Initial contents and policy for the single named solution supported by an item stack (food by default). */
public record ItemFoodSolutionProfile(int capacityCents, Map<ResourceLocation, Long> contentsCents, boolean canReact, String solutionName, boolean refillable) {
    public static final Codec<ItemFoodSolutionProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, 1_000_000).fieldOf("capacity_cents").forGetter(ItemFoodSolutionProfile::capacityCents),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.LONG).fieldOf("contents_cents").forGetter(ItemFoodSolutionProfile::contentsCents),
            Codec.BOOL.optionalFieldOf("can_react", true).forGetter(ItemFoodSolutionProfile::canReact),
            Codec.STRING.optionalFieldOf("solution_name", "food").forGetter(ItemFoodSolutionProfile::solutionName),
            Codec.BOOL.optionalFieldOf("refillable", false).forGetter(ItemFoodSolutionProfile::refillable)
    ).apply(i, ItemFoodSolutionProfile::new));

    public ItemFoodSolutionProfile(int capacityCents, Map<ResourceLocation, Long> contentsCents) { this(capacityCents, contentsCents, true, "food", false); }
    public ItemFoodSolutionProfile(int capacityCents, Map<ResourceLocation, Long> contentsCents, boolean canReact) { this(capacityCents, contentsCents, canReact, "food", false); }
    public ItemFoodSolutionProfile(int capacityCents, Map<ResourceLocation, Long> contentsCents, boolean canReact, String solutionName) { this(capacityCents, contentsCents, canReact, solutionName, false); }

    public ItemFoodSolutionProfile {
        contentsCents = Map.copyOf(contentsCents);
        if (solutionName == null || solutionName.isBlank()) throw new IllegalArgumentException("item solution name cannot be blank");
        long total = 0;
        for (var amount : contentsCents.values()) {
            if (amount == null || amount < 0 || amount > 1_000_000) throw new IllegalArgumentException("invalid item food solution amount");
            total = Math.addExact(total, amount);
        }
        if (capacityCents < 0 || capacityCents > 1_000_000 || total > capacityCents)
            throw new IllegalArgumentException("item food solution exceeds capacity");
    }
}
