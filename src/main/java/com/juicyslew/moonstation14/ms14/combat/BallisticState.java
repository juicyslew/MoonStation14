package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Ballistic provider supply: physical loaded rounds plus logical, not-yet-materialized fill. */
public record BallisticState(List<ItemStack> rounds, int unspawnedCount, ResourceLocation fillPrototype) {
    public static final Codec<BallisticState> CODEC=RecordCodecBuilder.create(i->i.group(
            ItemStack.CODEC.listOf().optionalFieldOf("rounds", List.of()).forGetter(BallisticState::rounds),
            Codec.INT.optionalFieldOf("unspawned_count", 0).forGetter(BallisticState::unspawnedCount),
            ResourceLocation.CODEC.fieldOf("fill_prototype").forGetter(BallisticState::fillPrototype)
    ).apply(i,BallisticState::new));
    public BallisticState { rounds=rounds.stream().map(ItemStack::copy).toList(); if(unspawnedCount<0)throw new IllegalArgumentException("negative unspawned count"); }
    @Override public List<ItemStack> rounds(){return rounds.stream().map(ItemStack::copy).toList();}
    public int count(){return rounds.stream().mapToInt(ItemStack::getCount).sum()+unspawnedCount;}
    public static BallisticState full(int capacity, ResourceLocation cartridge){return new BallisticState(List.of(),capacity,cartridge);}
}
