package com.juicyslew.moonstation14.component.codec.attachment;

import com.juicyslew.moonstation14.component.codec.component.DamageMap;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Map;

/** Immutable persisted/networked snapshot of character damage. */
public final class DamageData {
    private final Map<String, Float> damageMap;
    public DamageData(DamageMap data){
        this.damageMap = data.contents();
    }

    public DamageData(){
        damageMap = Map.of();
    }

    public Map<String, Float> getMap() { return damageMap; }

    public boolean isEmpty() { return damageMap.isEmpty(); }

    public DamageMap toData() {
        return new DamageMap(damageMap);
    }

    // decode -> mutable map
    // base map codec (removes zero entries before saving)

    public static final Codec<DamageData> CODEC = DamageMap.CODEC.xmap(
            DamageData::new,
            DamageData::toData
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, DamageData> STREAM_CODEC = DamageMap.STREAM_CODEC.map(
            DamageData::new,
            DamageData::toData
    );

    public int hashCode() {
        return damageMap.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof DamageData other && damageMap.equals(other.damageMap);
    }
}
