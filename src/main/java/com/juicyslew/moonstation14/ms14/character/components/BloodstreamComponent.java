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
import java.util.Objects;

/** Prototype-owned bloodstream physiology, not an entity's mutable blood volume. */
public record BloodstreamComponent(BloodstreamPolicy policy) implements CharacterComponent {
    public static final String TYPE = "Bloodstream";

    public BloodstreamComponent { Objects.requireNonNull(policy, "policy"); }

    @Override public String type() { return TYPE; }

    public static final Codec<BloodstreamComponent> CODEC = Codec.of(new Encoder<>() {
        @Override public <T> DataResult<T> encode(BloodstreamComponent component, DynamicOps<T> ops, T prefix) {
            try {
                JsonObject json = BloodstreamPolicy.CODEC.encodeStart(JsonOps.INSTANCE, component.policy()).getOrThrow().getAsJsonObject();
                json.addProperty("type", TYPE);
                CharacterSchemaAudit.auditComponent(json, "$.components[0]");
                return DataResult.success(JsonOps.INSTANCE.convertTo(ops, json));
            } catch (RuntimeException exception) {
                return DataResult.error(() -> exception.getMessage() == null ? "invalid Bloodstream component" : exception.getMessage());
            }
        }
    }, new Decoder<>() {
        @Override public <T> DataResult<Pair<BloodstreamComponent, T>> decode(DynamicOps<T> ops, T input) {
            try {
                JsonElement json = input instanceof JsonElement element ? element : ops.convertTo(JsonOps.INSTANCE, input);
                if (!json.isJsonObject()) return DataResult.error(() -> "Bloodstream component must be an object");
                CharacterSchemaAudit.auditComponent(json.getAsJsonObject(), "$.components[0]");
                JsonObject fields = json.getAsJsonObject().deepCopy();
                fields.remove("type");
                return BloodstreamPolicy.CODEC.parse(JsonOps.INSTANCE, fields).map(policy -> Pair.of(new BloodstreamComponent(policy), input));
            } catch (RuntimeException exception) {
                return DataResult.error(() -> exception.getMessage() == null ? "invalid Bloodstream component" : exception.getMessage());
            }
        }
    });
}
