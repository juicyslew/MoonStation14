package com.juicyslew.moonstation14.component.codec.component;

import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Map;

// TODO: There's certainly a way to reuse the similar setup from ReagentMap here.
public record DamageMap(Map<String, Float> contents) {

    public DamageMap {
        contents = DamageKeys.validateState(contents);
    }

    public DamageMap() {
        this(Map.of());
    }

    // This is the ONLY Codec you need to write for maps
    public static final Codec<DamageMap> CODEC = Codec.unboundedMap(Codec.STRING, Codec.FLOAT)
            .flatXmap(values -> {
                try {
                    return DataResult.success(new DamageMap(values));
                } catch (IllegalArgumentException | NullPointerException error) {
                    return DataResult.error(() -> error.getMessage());
                }
            }, values -> DataResult.success(values.contents()));

    // NETWORK: Uses optimized Integer IDs for the ResourceKeys (Registry-aware)
    public static final StreamCodec<RegistryFriendlyByteBuf, DamageMap> STREAM_CODEC =
            ByteBufCodecs.<RegistryFriendlyByteBuf, String, Float, Map<String, Float>>map(
                    java.util.LinkedHashMap::new,
                    ByteBufCodecs.STRING_UTF8,
                    ByteBufCodecs.FLOAT
            ).map(DamageMap::new, DamageMap::contents);
}
