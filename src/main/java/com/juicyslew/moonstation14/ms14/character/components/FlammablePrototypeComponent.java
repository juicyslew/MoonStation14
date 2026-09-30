package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.google.gson.JsonElement;
import com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.component.codec.json.DamageSpecifierData;
import java.util.Map;

/** Fire eligibility and prototype tuning; fire stacks remain mutable attachment state. */
public record FlammablePrototypeComponent(float firestackFade, DamageSpecifierData damage) implements CharacterComponent {
    public static final String TYPE = "Flammable";
    public static final float DEFAULT_FIRESTACK_FADE = -0.1f;
    public static final DamageSpecifierData DEFAULT_DAMAGE = new DamageSpecifierData(Map.of());

    public FlammablePrototypeComponent {
        if (!Float.isFinite(firestackFade) || firestackFade < -10.0f || firestackFade > 0.0f) {
            throw new IllegalArgumentException("firestack_fade must be finite and in [-10, 0]");
        }
        if (damage == null || damage.types().values().stream().anyMatch(value -> !Float.isFinite(value)
                || value < 0.0f || value > 1_000_000.0f || value * 10.0f > Float.MAX_VALUE)) {
            throw new IllegalArgumentException("damage types must be finite, nonnegative, and at most 1000000 per fire stack");
        }
    }

    public FlammablePrototypeComponent(float firestackFade) { this(firestackFade, DEFAULT_DAMAGE); }

    private static final Codec<String> TYPE_CODEC = Codec.STRING.comapFlatMap(type -> TYPE.equals(type)
            ? DataResult.success(type)
            : DataResult.error(() -> "unknown component type: " + type), type -> type);
    private static final Codec<Float> FADE_CODEC = Codec.FLOAT.comapFlatMap(fade -> validFade(fade)
            ? DataResult.success(fade)
            : DataResult.error(() -> "firestack_fade must be finite and in [-10, 0]"), fade -> fade);
    private static final Codec<DamageSpecifierData> DAMAGE_CODEC = DamageSpecifierData.CODEC.comapFlatMap(damage -> {
        boolean valid = damage != null && damage.types().values().stream().allMatch(value -> Float.isFinite(value)
                && value >= 0.0f && value <= 1_000_000.0f && value * 10.0f <= Float.MAX_VALUE);
        return valid ? DataResult.success(damage) : DataResult.error(() -> "damage types must be finite, nonnegative, and at most 1000000 per fire stack");
    }, damage -> damage);

    private static final Codec<FlammablePrototypeComponent> STRUCTURAL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TYPE_CODEC.fieldOf("type").forGetter(FlammablePrototypeComponent::type),
            FADE_CODEC.optionalFieldOf("firestack_fade", DEFAULT_FIRESTACK_FADE).forGetter(FlammablePrototypeComponent::firestackFade),
            DAMAGE_CODEC.optionalFieldOf("damage", DEFAULT_DAMAGE).forGetter(FlammablePrototypeComponent::damage)
    ).apply(instance, (type, firestackFade, damage) -> {
        return new FlammablePrototypeComponent(firestackFade, damage);
    }));

    public static final Codec<FlammablePrototypeComponent> CODEC = Codec.of(new Encoder<>() {
        @Override public <T> DataResult<T> encode(FlammablePrototypeComponent component, DynamicOps<T> ops, T prefix) {
            try {
                T encoded = STRUCTURAL_CODEC.encode(component, ops, prefix).getOrThrow();
                JsonElement json = ops.convertTo(JsonOps.INSTANCE, encoded);
                if (!json.isJsonObject()) return DataResult.error(() -> "Flammable component must be an object");
                CharacterSchemaAudit.auditComponent(json.getAsJsonObject(), "$.components[0]");
                return DataResult.success(encoded);
            } catch (RuntimeException exception) {
                return DataResult.error(() -> exception.getMessage() == null ? "invalid Flammable component" : exception.getMessage());
            }
        }
    }, new Decoder<>() {
        @Override public <T> DataResult<Pair<FlammablePrototypeComponent, T>> decode(DynamicOps<T> ops, T input) {
            try {
                JsonElement json = input instanceof JsonElement element ? element : ops.convertTo(JsonOps.INSTANCE, input);
                if (!json.isJsonObject()) return DataResult.error(() -> "Character schema at $.components[0]: expected object");
                CharacterSchemaAudit.auditComponent(json.getAsJsonObject(), "$.components[0]");
                return STRUCTURAL_CODEC.decode(ops, input);
            } catch (RuntimeException exception) {
                return DataResult.error(() -> exception.getMessage() == null ? "invalid Flammable component" : exception.getMessage());
            }
        }
    });

    private static boolean validFade(float fade) {
        return Float.isFinite(fade) && fade >= -10.0f && fade <= 0.0f;
    }

    @Override public String type() { return TYPE; }
}
