package com.juicyslew.moonstation14.ms14.status_effect;

import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import com.juicyslew.moonstation14.util.interfaces.IMS14Component;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.juicyslew.moonstation14.util.CodecHelpers.LENIENT_ID_CODEC;

public record StatusEffectComponent(Map<ResourceKey<StatusEffectData>, StatusEffectInstance> contents) implements IMS14Component<StatusEffectComponent, StatusEffectAttachment> {

    /** Maximum number of statuses allowed in persistent or network storage. */
    public static final int MAX_ENTRIES = 256;

    /** Compatibility name retained for callers that refer to the network limit. */
    @Deprecated
    public static final int MAX_NETWORK_ENTRIES = MAX_ENTRIES;

    public StatusEffectComponent {
        contents = immutableCopy(contents);
    }

    public StatusEffectComponent() {
        this(Map.of());
    }

    public StatusEffectAttachment toAttachment(){
        return new StatusEffectAttachment(this);
    }

    // This is the ONLY Codec you need to write for maps
    private static final Codec<Map<ResourceKey<StatusEffectData>, StatusEffectInstance>> RAW_PERSISTENT_MAP_CODEC =
            Codec.unboundedMap(LENIENT_ID_CODEC.xmap(
                     rl -> ResourceKey.create(ModStatusEffects.STATUS_EFFECT_REGISTRY_KEY, rl),
                     ResourceKey::location
             ), StatusEffectInstance.CODEC);

    /**
     * The vanilla unbounded-map decoder puts entries into a map. Validate
     * decoded IDs first so a short and fully-qualified spelling cannot
     * silently overwrite one another.
     */
    private static final Codec<Map<ResourceKey<StatusEffectData>, StatusEffectInstance>> PERSISTENT_MAP_CODEC =
            Codec.of(RAW_PERSISTENT_MAP_CODEC, new Decoder<>() {
                @Override
                public <T> DataResult<Pair<Map<ResourceKey<StatusEffectData>, StatusEffectInstance>, T>> decode(
                        DynamicOps<T> ops, T input) {
                    return ops.getMap(input).flatMap(map -> {
                        Set<net.minecraft.resources.ResourceLocation> seen = new HashSet<>();
                        DataResult<Boolean> keys = DataResult.success(true);
                        for (Pair<T, T> entry : map.entries().toList()) {
                            keys = keys.flatMap(ignored -> LENIENT_ID_CODEC.parse(ops, entry.getFirst())
                                    .flatMap(id -> seen.add(id)
                                            ? DataResult.success(true)
                                            : DataResult.error(() -> "duplicate canonical status effect key '"
                                                    + id + "'")));
                        }
                        return keys.flatMap(ignored -> RAW_PERSISTENT_MAP_CODEC.decode(ops, input));
                    });
                }
            });

    /** Persistent codec with the same entry bound enforced on both directions. */
    public static final Codec<StatusEffectComponent> CODEC = PERSISTENT_MAP_CODEC.flatXmap(
            contents -> validatePersistentSize(contents).map(StatusEffectComponent::new),
            component -> validatePersistentSize(component.contents()));

    // NETWORK: Uses optimized Integer IDs for the ResourceKeys (Registry-aware)
    public static final StreamCodec<RegistryFriendlyByteBuf, StatusEffectComponent> STREAM_CODEC =
            ByteBufCodecs.<RegistryFriendlyByteBuf, ResourceKey<StatusEffectData>, StatusEffectInstance,
                    Map<ResourceKey<StatusEffectData>, StatusEffectInstance>>map(
                    HashMap::new,
                    ResourceKey.streamCodec(ModStatusEffects.STATUS_EFFECT_REGISTRY_KEY),
                    StatusEffectInstance.STREAM_CODEC,
                     MAX_ENTRIES
             ).map(StatusEffectComponent::new, StatusEffectComponent::contents);

    private static DataResult<Map<ResourceKey<StatusEffectData>, StatusEffectInstance>> validatePersistentSize(
            Map<ResourceKey<StatusEffectData>, StatusEffectInstance> contents) {
        return contents.size() <= MAX_ENTRIES
                ? DataResult.success(contents)
                : DataResult.error(() -> "status effect map exceeds maximum of " + MAX_ENTRIES + " entries");
    }

    private static Map<ResourceKey<StatusEffectData>, StatusEffectInstance> immutableCopy(
            Map<ResourceKey<StatusEffectData>, StatusEffectInstance> source) {
        Objects.requireNonNull(source, "contents");
        for (Map.Entry<ResourceKey<StatusEffectData>, StatusEffectInstance> entry : source.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "status effect key");
            Objects.requireNonNull(entry.getValue(), "status effect instance");
        }
        return Map.copyOf(source);
    }

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
