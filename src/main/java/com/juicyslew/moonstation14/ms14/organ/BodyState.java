package com.juicyslew.moonstation14.ms14.organ;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Immutable authoritative body snapshot. Presence (including EMPTY) is the initialization marker. */
public record BodyState(List<OrganInstance> organs, RespirationState respiration) {
    public static final int MAX_ORGANS = 32;
    public static final BodyState EMPTY = new BodyState(List.of(), RespirationState.UNINITIALIZED);
    private static final Codec<BodyState> RECORD = RecordCodecBuilder.create(i -> i.group(
            OrganInstance.CODEC.listOf().fieldOf("organs").forGetter(BodyState::organs),
            RespirationState.CODEC.fieldOf("respiration").forGetter(BodyState::respiration)
    ).apply(i, BodyState::new));
    public static final Codec<BodyState> CODEC = Codec.of(RECORD, new Decoder<>() {
        @Override public <T> DataResult<Pair<BodyState, T>> decode(DynamicOps<T> ops, T input) {
            try {
                JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                if (!json.isJsonObject() || !json.getAsJsonObject().keySet().equals(Set.of("organs", "respiration")))
                    return DataResult.error(() -> "invalid body fields");
                return RECORD.decode(ops, input);
            } catch (RuntimeException e) { return DataResult.error(() -> "invalid body: " + e.getMessage()); }
        }
    });

    public BodyState {
        Objects.requireNonNull(organs, "organs");
        Objects.requireNonNull(respiration, "respiration");
        if (organs.size() > MAX_ORGANS) throw new IllegalArgumentException("too many organs");
        Set<UUID> ids = new HashSet<>();
        Set<OrganCategory> categories = new HashSet<>();
        for (OrganInstance organ : organs) {
            Objects.requireNonNull(organ, "organ");
            if (!ids.add(organ.id()) || !categories.add(organ.category()))
                throw new IllegalArgumentException("duplicate organ identity or category");
        }
        organs = List.copyOf(organs);
    }

    public Optional<OrganInstance> find(UUID id) {
        return organs.stream().filter(o -> o.id().equals(id)).findFirst();
    }
    public Optional<OrganInstance> find(OrganCategory category) {
        return organs.stream().filter(o -> o.category() == category).findFirst();
    }
    public static void validate(OrganInstance organ, PrototypeCatalog<OrganData> catalog) {
        Objects.requireNonNull(catalog, "catalog");
        OrganData prototype = catalog.get(organ.prototype());
        if (prototype == null || prototype.category() != organ.category())
            throw new IllegalArgumentException("missing or mismatched organ prototype: " + organ.prototype());
    }
    public BodyState attach(OrganInstance organ, PrototypeCatalog<OrganData> catalog) {
        validate(Objects.requireNonNull(organ), catalog);
        List<OrganInstance> next = new ArrayList<>(organs);
        next.add(organ);
        return new BodyState(next, respiration);
    }
    public BodyState detach(UUID id) {
        if (find(id).isEmpty()) throw new IllegalArgumentException("organ not attached: " + id);
        return new BodyState(organs.stream().filter(o -> !o.id().equals(id)).toList(), respiration);
    }
    public BodyState updateLung(UUID id, LungOrganState lung, PrototypeCatalog<OrganData> catalog) {
        OrganInstance current = find(id).orElseThrow(() -> new IllegalArgumentException("organ not attached: " + id));
        OrganInstance updated = current.withLung(lung);
        validate(updated, catalog);
        List<OrganInstance> next = new ArrayList<>(organs);
        next.set(next.indexOf(current), updated);
        return new BodyState(next, respiration);
    }
    public BodyState updateRespiration(RespirationState next) {
        return new BodyState(organs, Objects.requireNonNull(next));
    }
    public void validateAll(PrototypeCatalog<OrganData> catalog) { organs.forEach(o -> validate(o, catalog)); }
}
