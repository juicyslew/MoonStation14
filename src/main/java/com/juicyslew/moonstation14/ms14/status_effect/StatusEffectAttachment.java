package com.juicyslew.moonstation14.ms14.status_effect;

import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import com.juicyslew.moonstation14.util.interfaces.IMS14Attachment;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Mutable server-side view of the immutable status component state. */
public final class StatusEffectAttachment implements IMS14Attachment<StatusEffectAttachment, StatusEffectComponent> {
    private final Map<ResourceKey<StatusEffectData>, StatusEffectInstance> statusEffectMap;

    public StatusEffectAttachment(StatusEffectComponent data) {
        this(Objects.requireNonNull(data, "data").contents());
    }

    /** Constructs an attachment from a defensive copy of the supplied state. */
    public StatusEffectAttachment(Map<ResourceKey<StatusEffectData>, StatusEffectInstance> data) {
        statusEffectMap = new HashMap<>(validatedCopy(data));
    }

    public StatusEffectAttachment() {
        statusEffectMap = new HashMap<>();
    }

    public boolean isEmpty() {
        return statusEffectMap.isEmpty();
    }

    /**
     * Returns an immutable snapshot. This compatibility method is a query view,
     * not a mutation API; use {@link #apply} for changes.
     */
    @Deprecated
    public Map<ResourceKey<StatusEffectData>, StatusEffectInstance> getMap() {
        return snapshot();
    }

    /** Returns an immutable snapshot of all current statuses. */
    public Map<ResourceKey<StatusEffectData>, StatusEffectInstance> snapshot() {
        return Map.copyOf(statusEffectMap);
    }

    public Optional<StatusEffectInstance> get(ResourceKey<StatusEffectData> key) {
        return Optional.ofNullable(statusEffectMap.get(Objects.requireNonNull(key, "status effect key")));
    }

    public boolean contains(ResourceKey<StatusEffectData> key) {
        return statusEffectMap.containsKey(Objects.requireNonNull(key, "status effect key"));
    }

    public boolean isActive(ResourceKey<StatusEffectData> key) {
        return get(key).map(StatusEffectInstance::isActive).orElse(false);
    }

    /** Applies one typed operation and mutates this attachment only when needed. */
    public StatusEffectReduction apply(ResourceKey<StatusEffectData> key,
                                        StatusEffectOperation operation,
                                        OptionalInt durationTicks,
                                        int delayTicks) {
        return apply(key, operation, durationTicks, delayTicks, StatusEffectPayload.none());
    }

    /** Applies one operation with typed application-specific state. */
    public StatusEffectReduction apply(ResourceKey<StatusEffectData> key,
                                       StatusEffectOperation operation,
                                       OptionalInt durationTicks,
                                       int delayTicks,
                                       StatusEffectPayload payload) {
        ResourceKey<StatusEffectData> checkedKey = Objects.requireNonNull(key, "status effect key");
        StatusEffectReduction reduction = StatusEffectReducer.reduce(
                operation,
                Optional.ofNullable(statusEffectMap.get(checkedKey)),
                durationTicks,
                delayTicks,
                payload
        );
        if (reduction.kind() == StatusEffectChangeKind.REMOVED) {
            remove(checkedKey);
        } else if (reduction.next().isPresent()
                && reduction.kind() != StatusEffectChangeKind.UNCHANGED) {
            statusEffectMap.put(checkedKey, reduction.next().orElseThrow());
        }
        return reduction;
    }

    /** Removes one status and returns the instance that was attached, if any. */
    public Optional<StatusEffectInstance> remove(ResourceKey<StatusEffectData> key) {
        return Optional.ofNullable(statusEffectMap.remove(Objects.requireNonNull(key, "status effect key")));
    }

    /** Alias for callers that describe the operation as a reduction. */
    public StatusEffectReduction reduce(ResourceKey<StatusEffectData> key,
                                        StatusEffectOperation operation,
                                        OptionalInt durationTicks,
                                        int delayTicks) {
        return apply(key, operation, durationTicks, delayTicks);
    }

    public StatusEffectReduction reduce(ResourceKey<StatusEffectData> key,
                                        StatusEffectOperation operation,
                                        OptionalInt durationTicks,
                                        int delayTicks,
                                        StatusEffectPayload payload) {
        return apply(key, operation, durationTicks, delayTicks, payload);
    }

    /** Advances every stored status exactly once in canonical resource order. */
    public List<StatusEffectTickChange> advanceOneTick() {
        List<ResourceKey<StatusEffectData>> keys = new ArrayList<>(statusEffectMap.keySet());
        keys.sort(Comparator.comparing(key -> key.location().toString()));

        List<StatusEffectTickChange> changes = new ArrayList<>(keys.size());
        for (ResourceKey<StatusEffectData> key : keys) {
            StatusEffectInstance before = statusEffectMap.get(key);
            if (before == null) {
                continue;
            }
            StatusEffectTickResult result = before.tick();
            if (result.next().isPresent()) {
                statusEffectMap.put(key, result.next().orElseThrow());
            } else {
                remove(key);
            }
            changes.add(new StatusEffectTickChange(key, before, result.next(), result.transition()));
        }
        return List.copyOf(changes);
    }

    /** Creates a detached immutable component snapshot. */
    @Override
    public StatusEffectComponent toComponent() {
        return new StatusEffectComponent(statusEffectMap);
    }

    public static final Codec<StatusEffectAttachment> CODEC = StatusEffectComponent.CODEC.xmap(
            StatusEffectAttachment::new,
            StatusEffectAttachment::toComponent
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, StatusEffectAttachment> STREAM_CODEC =
            StatusEffectComponent.STREAM_CODEC.map(StatusEffectAttachment::new, StatusEffectAttachment::toComponent);

    private static Map<ResourceKey<StatusEffectData>, StatusEffectInstance> validatedCopy(
            Map<ResourceKey<StatusEffectData>, StatusEffectInstance> data) {
        Objects.requireNonNull(data, "data");
        Map<ResourceKey<StatusEffectData>, StatusEffectInstance> copy = new HashMap<>();
        for (Map.Entry<ResourceKey<StatusEffectData>, StatusEffectInstance> entry : data.entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey(), "status effect key"),
                    Objects.requireNonNull(entry.getValue(), "status effect instance"));
        }
        return Collections.unmodifiableMap(copy);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StatusEffectAttachment that)) return false;
        return statusEffectMap.equals(that.statusEffectMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(statusEffectMap);
    }

    @Override
    public Codec<StatusEffectAttachment> getCodec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, StatusEffectAttachment> getStreamCodec() {
        return STREAM_CODEC;
    }
}
