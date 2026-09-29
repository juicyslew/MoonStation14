package com.juicyslew.moonstation14.ms14.character.components;

import com.google.gson.JsonElement;
import com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Objects;

/** Upstream BarotraumaComponent damage spec and maxDamage; protectionSlots are not implemented. */
public record BarotraumaComponent(Damage damage, double maxDamage) implements CharacterComponent {
    public static final String TYPE = "Barotrauma";
    private static final Codec<BarotraumaComponent> STRUCTURAL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(BarotraumaComponent::type),
            Damage.CODEC.fieldOf("damage").forGetter(BarotraumaComponent::damage),
            Codec.DOUBLE.fieldOf("maxDamage").forGetter(BarotraumaComponent::maxDamage)
    ).apply(instance, (type, damage, maxDamage) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new BarotraumaComponent(damage, maxDamage);
    }));

    /** Audit the raw value before JsonOps can convert nested nulls, and audit encoded values too. */
    public static final Codec<BarotraumaComponent> CODEC = Codec.of(new Encoder<>() {
        @Override public <T> DataResult<T> encode(BarotraumaComponent component, DynamicOps<T> ops, T prefix) {
            try {
                T encoded = STRUCTURAL_CODEC.encode(component, ops, prefix).getOrThrow();
                JsonElement json = ops.convertTo(JsonOps.INSTANCE, encoded);
                if (!json.isJsonObject()) return DataResult.error(() -> "Barotrauma component must be an object");
                CharacterSchemaAudit.auditComponent(json.getAsJsonObject(), "$.components[0]");
                return DataResult.success(encoded);
            } catch (RuntimeException exception) {
                return DataResult.error(() -> exception.getMessage() == null ? "invalid Barotrauma component" : exception.getMessage());
            }
        }
    }, new Decoder<>() {
        @Override public <T> DataResult<Pair<BarotraumaComponent, T>> decode(DynamicOps<T> ops, T input) {
            try {
                JsonElement json = input instanceof JsonElement element ? element : ops.convertTo(JsonOps.INSTANCE, input);
                if (!json.isJsonObject()) return DataResult.error(() -> "Character schema at $.components[0]: expected object");
                CharacterSchemaAudit.auditComponent(json.getAsJsonObject(), "$.components[0]");
                return STRUCTURAL_CODEC.decode(ops, input);
            } catch (RuntimeException exception) {
                return DataResult.error(() -> exception.getMessage() == null ? "invalid Barotrauma component" : exception.getMessage());
            }
        }
    });

    public BarotraumaComponent {
        Objects.requireNonNull(damage, "damage");
        if (!Double.isFinite(maxDamage) || maxDamage < 0 || maxDamage > 1_000_000)
            throw new IllegalArgumentException("maxDamage must be finite and nonnegative");
    }

    @Override public String type() { return TYPE; }

    public record Damage(Map<String, Double> types) {
        public static final Codec<Damage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("types").forGetter(Damage::types)
        ).apply(instance, Damage::new));

        public Damage { types = Map.copyOf(Objects.requireNonNull(types, "types")); }
    }
}
