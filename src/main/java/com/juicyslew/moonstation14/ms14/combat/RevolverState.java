package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Persistent per-stack revolving chamber state. Empty chambers and virtual live rounds are distinct. */
public record RevolverState(int index, List<Chamber> chambers) {
    public static final int MAX_CAPACITY = 64;
    public static final Codec<RevolverState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("index").forGetter(RevolverState::index),
            Chamber.CODEC.listOf().fieldOf("chambers").forGetter(RevolverState::chambers)
    ).apply(i, (index, chambers) -> new RevolverState(index, chambers)));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, RevolverState> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public RevolverState {
        chambers = List.copyOf(chambers);
        if (chambers.isEmpty() || chambers.size() > MAX_CAPACITY || index < 0 || index >= chambers.size())
            throw new IllegalArgumentException("invalid revolver state");
    }
    public static RevolverState empty(int capacity) {
        if (capacity < 1 || capacity > MAX_CAPACITY) throw new IllegalArgumentException("invalid capacity");
        return new RevolverState(0, java.util.Collections.nCopies(capacity, Chamber.empty()));
    }
    public record Chamber(boolean virtualLive, Optional<ItemStack> physical, Optional<net.minecraft.resources.ResourceLocation> spentCasing) {
        public static final Codec<Chamber> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("virtual_live").forGetter(Chamber::virtualLive),
                ItemStack.CODEC.optionalFieldOf("physical").forGetter(Chamber::physical),
                net.minecraft.resources.ResourceLocation.CODEC.optionalFieldOf("spent_casing").forGetter(Chamber::spentCasing)
        ).apply(i, Chamber::new));
        public Chamber(boolean virtualLive, Optional<ItemStack> physical) { this(virtualLive, physical, Optional.empty()); }
        public Chamber {
            physical = physical == null ? Optional.empty() : physical.map(ItemStack::copy);
            spentCasing = spentCasing == null ? Optional.empty() : spentCasing;
            int kinds = (virtualLive ? 1 : 0) + (physical.isPresent() ? 1 : 0) + (spentCasing.isPresent() ? 1 : 0);
            if (kinds > 1 || physical.filter(ItemStack::isEmpty).isPresent()) throw new IllegalArgumentException("contradictory or empty cartridge chamber");
        }
        @Override public Optional<ItemStack> physical() { return physical.map(ItemStack::copy); }
        public static Chamber empty() { return new Chamber(false, Optional.empty(), Optional.empty()); }
        public static Chamber spentDeferred(net.minecraft.resources.ResourceLocation prototype) { return new Chamber(false, Optional.empty(), Optional.of(prototype)); }
    }
}
