package com.juicyslew.moonstation14.ms14.status_effect;

import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import com.juicyslew.moonstation14.util.interfaces.IMS14Component;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.juicyslew.moonstation14.util.CodecHelpers.LENIENT_ID_CODEC;

public record StatusEffectComponent(Map<ResourceKey<StatusEffectData>, Float> contents) implements IMS14Component<StatusEffectComponent, StatusEffectAttachment> {

    public StatusEffectComponent() {
        this(Map.of());
    }

    public StatusEffectAttachment toAttachment(){
        return new StatusEffectAttachment(this);
    }

    // This is the ONLY Codec you need to write for maps
    public static final Codec<StatusEffectComponent> CODEC =
            Codec.unboundedMap(LENIENT_ID_CODEC.xmap(
                    rl -> ResourceKey.create(ModStatusEffects.STATUS_EFFECT_REGISTRY_KEY, rl),
                    ResourceKey::location
            ), Codec.FLOAT).xmap(StatusEffectComponent::new, StatusEffectComponent::contents);

    // NETWORK: Uses optimized Integer IDs for the ResourceKeys (Registry-aware)
    public static final StreamCodec<RegistryFriendlyByteBuf, StatusEffectComponent> STREAM_CODEC =
            ByteBufCodecs.<RegistryFriendlyByteBuf, ResourceKey<StatusEffectData>, Float, Map<ResourceKey<StatusEffectData>, Float>>map(
                    HashMap::new,
                    ResourceKey.streamCodec(ModStatusEffects.STATUS_EFFECT_REGISTRY_KEY),
                    ByteBufCodecs.FLOAT
            ).map(StatusEffectComponent::new, StatusEffectComponent::contents);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StatusEffectComponent other)) return false;
        // HashMap.equals() checks every key and every value for equality
        return Objects.equals(this.contents, other.contents);
    }

    @Override
    public Codec<StatusEffectComponent> getCodec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, StatusEffectComponent> getStreamCodec() {
        return STREAM_CODEC;
    }
}
