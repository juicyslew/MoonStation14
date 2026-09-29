package com.juicyslew.moonstation14.ms14.character.components;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.JsonOps;
import java.util.Map;

/** Explicit bounded discriminator registry; new component types register one codec and one schema audit. */
public final class CharacterComponentRegistry {
    private CharacterComponentRegistry() { }
    private record Entry<T extends CharacterComponent>(Class<T> javaType, Codec<T> codec) {
        <O> DataResult<O> encode(CharacterComponent component, DynamicOps<O> ops) {
            if (!javaType.isInstance(component)) return DataResult.error(() -> "component type/class mismatch");
            return codec.encodeStart(ops, javaType.cast(component));
        }

        DataResult<CharacterComponent> parse(JsonElement json) {
            return codec.parse(JsonOps.INSTANCE, json).map(component -> component);
        }
    }

    private static final Map<String, Entry<?>> TYPES = Map.of(
            BarotraumaComponent.TYPE, new Entry<>(BarotraumaComponent.class, BarotraumaComponent.CODEC));

    public static boolean registered(String type) { return TYPES.containsKey(type); }

    public static final Codec<CharacterComponent> CODEC = Codec.of(new Encoder<>() {
        @Override public <T> DataResult<T> encode(CharacterComponent component, DynamicOps<T> ops, T prefix) {
            if (component == null || !registered(component.type()))
                return DataResult.error(() -> "unknown character component");
            return TYPES.get(component.type()).encode(component, ops);
        }
    }, new Decoder<>() {
        @Override public <T> DataResult<Pair<CharacterComponent, T>> decode(DynamicOps<T> ops, T input) {
            try {
                JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                if (!json.isJsonObject()) return DataResult.error(() -> "component must be an object");
                JsonObject object = json.getAsJsonObject();
                CharacterSchemaAudit.auditComponent(object, "$.components[0]");
                String type = object.get("type").getAsString();
                return TYPES.get(type).parse(json).map(component -> Pair.of(component, input));
            } catch (RuntimeException exception) {
                return DataResult.error(() -> exception.getMessage() == null ? "invalid component" : exception.getMessage());
            }
        }
    });
}
