package com.juicyslew.moonstation14.component.codec;


import com.juicyslew.moonstation14.enums.ReagentEnum;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.*;

public final class ReagentContainerData {
    private final Map<ReagentEnum, Integer> reagentMap;
    public ReagentContainerData(Map<ReagentEnum, Integer> reagent_map){
        this.reagentMap = reagent_map;
    };

    public ReagentContainerData(){
        reagentMap = new HashMap<>();
    }

    public Map<ReagentEnum,Integer> getMap() { return reagentMap; }

    public int getTotalVolume() {
        int sum = 0;
        for (int i : reagentMap.values()) {
            sum += i;
        }
        return sum;
    }

    public int getBlendedColor() {
        if (reagentMap.isEmpty()) return -1; // Default/No tint

        float totalWeight = 0;
        float r = 0, g = 0, b = 0;

        for (Map.Entry<ReagentEnum, Integer> entry : reagentMap.entrySet()) {
            int amount = entry.getValue();
            int color = entry.getKey().getColor();

            r += ((color >> 16) & 0xFF) * amount;
            g += ((color >> 8) & 0xFF) * amount;
            b += (color & 0xFF) * amount;
            totalWeight += amount;
        }

        return 0xFF000000 | ((int)(r / totalWeight) << 16) | ((int)(g / totalWeight) << 8) | (int)(b / totalWeight);
    }

    public ReagentContainerData withClampedAdd(ReagentEnum Reagent, int amount, int capacity) {
        int totalVolume = getTotalVolume();
        int actual_add = Integer.min(totalVolume + amount, capacity) - totalVolume;
        Map<ReagentEnum, Integer> newMap = new HashMap<>(this.reagentMap);
        newMap.put(Reagent, newMap.getOrDefault(Reagent, 0) + actual_add); // Add your clamping logic here
        return new ReagentContainerData(newMap);
    }

    public ReagentContainerData withRemovedClamp(ReagentEnum Reagent, int amount) {
        int reagent_present = reagentMap.getOrDefault(Reagent, 0);
        int new_amount = Integer.max(reagent_present - amount, 0);
        Map<ReagentEnum, Integer> newMap = new HashMap<>(this.reagentMap);
        newMap.put(Reagent, new_amount); // Add your clamping logic here
        return new ReagentContainerData(newMap);
    }

    // decode -> mutable map
    // base map codec (removes zero entries before saving)
    private static final Codec<Map<ReagentEnum,Integer>> MAP_CODEC =
            Codec.unboundedMap(ReagentEnum.CODEC, Codec.INT);

    public static final Codec<ReagentContainerData> CODEC = MAP_CODEC.xmap(
            ReagentContainerData::new,
            ReagentContainerData::getMap
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ReagentContainerData> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.map(
            HashMap::new,
            ByteBufCodecs.fromCodec(ReagentEnum.CODEC),
            ByteBufCodecs.INT
        ),
        ReagentContainerData::getMap,
        ReagentContainerData::new
    );

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }
}