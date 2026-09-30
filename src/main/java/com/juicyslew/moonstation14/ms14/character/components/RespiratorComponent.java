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

/** Typed mob component, not an organ or a mutable attachment. */
public record RespiratorComponent(RespiratorPolicy policy) implements CharacterComponent {
    public static final String TYPE = "Respirator";
    public RespiratorComponent { Objects.requireNonNull(policy, "policy"); }
    @Override public String type() { return TYPE; }
    public static final Codec<RespiratorComponent> CODEC = Codec.of(new Encoder<>() {
        @Override public <T> DataResult<T> encode(RespiratorComponent component, DynamicOps<T> ops, T prefix) {
            JsonObject json = RespiratorPolicy.CODEC.encodeStart(JsonOps.INSTANCE, component.policy()).getOrThrow().getAsJsonObject();
            json.addProperty("type", TYPE);
            CharacterSchemaAudit.auditComponent(json, "$.components[0]");
            return DataResult.success(JsonOps.INSTANCE.convertTo(ops, json));
        }
    }, new Decoder<>() {
        @Override public <T> DataResult<Pair<RespiratorComponent, T>> decode(DynamicOps<T> ops, T input) {
            try {
                JsonElement json = input instanceof JsonElement element ? element : ops.convertTo(JsonOps.INSTANCE, input);
                if (!json.isJsonObject()) return DataResult.error(() -> "Respirator must be an object");
                CharacterSchemaAudit.auditComponent(json.getAsJsonObject(), "$.components[0]");
                JsonObject fields = json.getAsJsonObject().deepCopy();
                fields.remove("type");
                return RespiratorPolicy.CODEC.parse(JsonOps.INSTANCE, fields).map(p -> Pair.of(new RespiratorComponent(p), input));
            } catch (RuntimeException ex) {
                return DataResult.error(() -> ex.getMessage() == null ? "invalid Respirator" : ex.getMessage());
            }
        }
    });
}
