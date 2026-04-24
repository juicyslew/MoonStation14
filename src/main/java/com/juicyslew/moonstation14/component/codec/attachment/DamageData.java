package com.juicyslew.moonstation14.component.codec.attachment;

import com.juicyslew.moonstation14.component.codec.component.DamageMap;
import com.juicyslew.moonstation14.util.interfaces.IClampedMapHolder;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashMap;
import java.util.Map;

public final class DamageData implements IClampedMapHolder<String> {
    private final Map<String, Float> damageMap;
    public DamageData(DamageMap data){
        this.damageMap = new HashMap<>(data.contents());
    };
    // TODO: Should probably have some information somewhere about what damage types are allowed for the object using this.

    public DamageData(){
        damageMap = new HashMap<>();
    }

    @Override public Map<String, Float> getMap() { return damageMap; }

    public DamageMap toData() {
        return new DamageMap(Map.copyOf(damageMap));
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

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }
}
