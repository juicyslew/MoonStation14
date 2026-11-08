package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Physical rounds belong to their actual magazine/item state. */
public record AmmoContainerState(List<ItemStack> rounds) {
    public static final Codec<AmmoContainerState> CODEC=RecordCodecBuilder.create(i->i.group(ItemStack.CODEC.listOf().fieldOf("rounds").forGetter(AmmoContainerState::rounds)).apply(i,AmmoContainerState::new));
    public AmmoContainerState { rounds=rounds.stream().map(ItemStack::copy).toList(); }
    @Override public List<ItemStack> rounds(){return rounds.stream().map(ItemStack::copy).toList();}
}
