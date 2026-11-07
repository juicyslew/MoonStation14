package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;

/** Single capacity authority for integral batteries and removable cell items. */
public record BatteryProfile(float capacity, float startingCharge, Set<ResourceLocation> tags) {
    public static final Codec<BatteryProfile> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.FLOAT.fieldOf("capacity").forGetter(BatteryProfile::capacity), Codec.FLOAT.optionalFieldOf("starting_charge",0f).forGetter(BatteryProfile::startingCharge),
            ResourceLocation.CODEC.listOf().optionalFieldOf("tags", java.util.List.of()).xmap(Set::copyOf,s->java.util.List.copyOf(s)).forGetter(BatteryProfile::tags)
    ).apply(i,BatteryProfile::new));
    public BatteryProfile { tags=Set.copyOf(tags); if(!Float.isFinite(capacity)||capacity<=0||!Float.isFinite(startingCharge)||startingCharge<0||startingCharge>capacity) throw new IllegalArgumentException("invalid battery capacity/charge"); }
}
