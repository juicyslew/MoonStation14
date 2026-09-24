package com.juicyslew.moonstation14.ms14.alert;

import com.juicyslew.moonstation14.component.codec.json.AlertData;
import com.juicyslew.moonstation14.util.interfaces.IMS14Component;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Objects;

/** Immutable character-owned alert state. Deadlines are absolute server game ticks; absent means persistent. */
public record AlertComponent(Map<ResourceKey<AlertData>, AlertInstance> contents)
        implements IMS14Component<AlertComponent, AlertAttachment> {
    public static final int MAX_ENTRIES = 256;
    public AlertComponent {
        Objects.requireNonNull(contents, "contents");
        if (contents.size() > MAX_ENTRIES) throw new IllegalArgumentException("alert map exceeds maximum");
        contents.forEach((key, value) -> { Objects.requireNonNull(key, "alert key"); Objects.requireNonNull(value, "alert instance"); });
        contents = Map.copyOf(contents);
    }
    public AlertComponent() { this(Map.of()); }
    public AlertAttachment toAttachment() { return new AlertAttachment(this); }
    public static final Codec<AlertComponent> CODEC = Codec.unboundedMap(
            ResourceLocation.CODEC.xmap(id -> ResourceKey.create(ModAlerts.ALERT_REGISTRY_KEY, id), ResourceKey::location),
            AlertInstance.CODEC).comapFlatMap(map -> map.size() <= MAX_ENTRIES
                    ? com.mojang.serialization.DataResult.success(new AlertComponent(map))
                    : com.mojang.serialization.DataResult.error(() -> "alert map exceeds maximum of " + MAX_ENTRIES), AlertComponent::contents);
    public static final StreamCodec<RegistryFriendlyByteBuf, AlertComponent> STREAM_CODEC =
            ByteBufCodecs.<RegistryFriendlyByteBuf, ResourceKey<AlertData>, AlertInstance, Map<ResourceKey<AlertData>, AlertInstance>>map(
                    java.util.HashMap::new, ResourceKey.streamCodec(ModAlerts.ALERT_REGISTRY_KEY), AlertInstance.STREAM_CODEC, MAX_ENTRIES)
                    .map(AlertComponent::new, AlertComponent::contents);
    @Override public Codec<AlertComponent> getCodec() { return CODEC; }
    @Override public StreamCodec<RegistryFriendlyByteBuf, AlertComponent> getStreamCodec() { return STREAM_CODEC; }
}
