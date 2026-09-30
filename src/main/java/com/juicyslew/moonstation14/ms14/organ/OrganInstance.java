package com.juicyslew.moonstation14.ms14.organ;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Stable identity and state of an organ contained by exactly one body, never a world entity. */
public record OrganInstance(UUID id, ResourceLocation prototype, OrganCategory category, LungOrganState lung) {
    private static final Codec<OrganInstance> RECORD = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.comapFlatMap(s -> {
                try { return DataResult.success(UUID.fromString(s)); }
                catch (IllegalArgumentException e) { return DataResult.error(() -> "invalid organ UUID"); }
            }, UUID::toString).fieldOf("id").forGetter(OrganInstance::id),
            ResourceLocation.CODEC.fieldOf("prototype").forGetter(OrganInstance::prototype),
            OrganCategory.CODEC.fieldOf("category").forGetter(OrganInstance::category),
            LungOrganState.CODEC.optionalFieldOf("lung").forGetter(o -> java.util.Optional.ofNullable(o.lung()))
    ).apply(i, (id, prototype, category, lung) -> new OrganInstance(id, prototype, category, lung.orElse(null))));
    public static final Codec<OrganInstance> CODEC = Codec.of(RECORD, new Decoder<>() {
        @Override public <T> DataResult<Pair<OrganInstance, T>> decode(DynamicOps<T> ops, T input) {
            try {
                JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                if (!json.isJsonObject() || !json.getAsJsonObject().keySet().stream()
                        .allMatch(Set.of("id", "prototype", "category", "lung")::contains))
                    return DataResult.error(() -> "invalid organ fields");
                if (json.getAsJsonObject().has("lung")) {
                    JsonElement lung = json.getAsJsonObject().get("lung");
                    if (!lung.isJsonObject() || !lung.getAsJsonObject().keySet().stream().allMatch(
                             Set.of("gas_moles", "temperature_kelvin")::contains))
                        return DataResult.error(() -> "invalid lung fields");
                }
                return RECORD.decode(ops, input);
            } catch (RuntimeException e) { return DataResult.error(() -> "invalid organ: " + e.getMessage()); }
        }
    });

    public OrganInstance {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(prototype, "prototype");
        Objects.requireNonNull(category, "category");
        if ((category == OrganCategory.LUNGS) != (lung != null))
            throw new IllegalArgumentException("only Lungs have lung state; Lungs require it");
    }

    public OrganInstance withLung(LungOrganState next) {
        if (category != OrganCategory.LUNGS) throw new IllegalArgumentException("not lungs");
        return new OrganInstance(id, prototype, category, Objects.requireNonNull(next));
    }
}
