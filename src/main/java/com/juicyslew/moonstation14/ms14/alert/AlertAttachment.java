package com.juicyslew.moonstation14.ms14.alert;

import com.juicyslew.moonstation14.component.codec.json.AlertData;
import com.juicyslew.moonstation14.util.interfaces.IMS14Attachment;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Mutable runtime view of immutable alert state. */
public final class AlertAttachment implements IMS14Attachment<AlertAttachment, AlertComponent> {
    private final Map<ResourceKey<AlertData>, AlertInstance> alerts;
    public AlertAttachment() { this(new AlertComponent()); }
    public AlertAttachment(AlertComponent state) { this.alerts = new HashMap<>(state.contents()); }
    public boolean isEmpty() { return alerts.isEmpty(); }
    public Map<ResourceKey<AlertData>, AlertInstance> snapshot() { return Map.copyOf(alerts); }
    public Optional<AlertInstance> get(ResourceKey<AlertData> key) { return Optional.ofNullable(alerts.get(key)); }
    void replace(Map<ResourceKey<AlertData>, AlertInstance> state) { alerts.clear(); alerts.putAll(state); }
    @Override public AlertComponent toComponent() { return new AlertComponent(alerts); }
    public static final Codec<AlertAttachment> CODEC = AlertComponent.CODEC.xmap(AlertAttachment::new, AlertAttachment::toComponent);
    public static final StreamCodec<RegistryFriendlyByteBuf, AlertAttachment> STREAM_CODEC = AlertComponent.STREAM_CODEC.map(AlertAttachment::new, AlertAttachment::toComponent);
    @Override public Codec<AlertAttachment> getCodec() { return CODEC; }
    @Override public StreamCodec<RegistryFriendlyByteBuf, AlertAttachment> getStreamCodec() { return STREAM_CODEC; }
}
