package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable SS14 gas reaction gates and whitelisted effect identities; no executable reaction state.
 * Requirements are absolute moles per local 1 m³ cell (0.01 mol in bundled data),
 * deliberately not scaled from SS14's 2.5 m³ cell.
 */
public record GasReactionData(Map<GasType, Double> minimumRequirements, double minimumTemperature,
                              double maximumTemperature, double minimumEnergy, int priority, List<Effect> effects) {
    /** Upstream effect names are explicit data discriminators, not class names to instantiate. */
    public enum EffectType {
        PLASMA_FIRE("PlasmaFireReaction"), TRITIUM_FIRE("TritiumFireReaction"),
        FREZON_COOLANT("FrezonCoolantReaction"), FREZON_PRODUCTION("FrezonProductionReaction"),
        AMMONIA_OXYGEN("AmmoniaOxygenReaction"), N2O_DECOMPOSITION("N2ODecompositionReaction");

        private final String id;
        EffectType(String id) { this.id = id; }
        public String id() { return id; }

        public static final Codec<EffectType> CODEC = Codec.STRING.comapFlatMap(id -> {
            for (EffectType type : values()) if (type.id.equals(id)) return DataResult.success(type);
            return DataResult.error(() -> "unknown reaction effect: " + id);
        }, EffectType::id);
    }

    public record Effect(EffectType type) {
        public static final Codec<Effect> CODEC = RecordCodecBuilder.create(i -> i.group(
                EffectType.CODEC.fieldOf("type").forGetter(Effect::type)).apply(i, Effect::new));
        public Effect {
            if (type == null) throw new IllegalArgumentException("missing reaction effect type");
        }
    }

    private static final Codec<GasType> GAS = Codec.STRING.comapFlatMap(id -> {
        for (GasType gas : GasType.values()) if (gas.id().equals(id)) return DataResult.success(gas);
        return DataResult.error(() -> "unknown reaction gas: " + id);
    }, GasType::id);

    private static final Codec<GasReactionData> STRUCTURAL = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(GAS, Codec.DOUBLE).fieldOf("minimumRequirements").forGetter(GasReactionData::minimumRequirements),
            Codec.DOUBLE.optionalFieldOf("minimumTemperature", 2.7).forGetter(GasReactionData::minimumTemperature),
            Codec.DOUBLE.optionalFieldOf("maximumTemperature", (double) Float.MAX_VALUE).forGetter(GasReactionData::maximumTemperature),
            Codec.DOUBLE.optionalFieldOf("minimumEnergy", 0.0).forGetter(GasReactionData::minimumEnergy),
            Codec.INT.fieldOf("priority").forGetter(GasReactionData::priority),
            Effect.CODEC.listOf().fieldOf("effects").forGetter(GasReactionData::effects)
    ).apply(i, GasReactionData::new));

    public static final Codec<GasReactionData> CODEC = Codec.of(STRUCTURAL, new Decoder<>() {
        @Override public <T> DataResult<Pair<GasReactionData, T>> decode(DynamicOps<T> ops, T input) {
            try {
                JsonElement json = input instanceof JsonElement element ? element : ops.convertTo(JsonOps.INSTANCE, input);
                audit(json);
                return STRUCTURAL.decode(ops, input);
            } catch (RuntimeException ex) {
                return DataResult.error(() -> ex.getMessage() == null ? "invalid gas reaction" : ex.getMessage());
            }
        }
    });

    public GasReactionData {
        if (minimumRequirements == null || minimumRequirements.isEmpty())
            throw new IllegalArgumentException("reaction requires gas minimums");
        for (var entry : minimumRequirements.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || !Double.isFinite(entry.getValue())
                    || entry.getValue() < 0) throw new IllegalArgumentException("invalid gas requirement");
        }
        minimumRequirements = Map.copyOf(minimumRequirements);
        if (!Double.isFinite(minimumTemperature) || minimumTemperature < 0
                || !Double.isFinite(maximumTemperature) || maximumTemperature < minimumTemperature
                || !Double.isFinite(minimumEnergy) || minimumEnergy < 0)
            throw new IllegalArgumentException("invalid reaction temperature or energy gates");
        if (priority < -1000 || priority > 1000) throw new IllegalArgumentException("reaction priority outside [-1000, 1000]");
        if (effects == null || effects.isEmpty() || effects.stream().anyMatch(e -> e == null || e.type() == null)
                || effects.stream().map(Effect::type).distinct().count() != effects.size())
            throw new IllegalArgumentException("missing or duplicate reaction effects");
        effects = List.copyOf(effects);
    }

    private static void audit(JsonElement json) {
        if (json == null || !json.isJsonObject()) throw new IllegalArgumentException("gas reaction must be an object");
        JsonObject fields = json.getAsJsonObject();
        Set<String> allowed = Set.of("minimumRequirements", "minimumTemperature", "maximumTemperature",
                "minimumEnergy", "priority", "effects");
        for (String key : fields.keySet()) {
            if (!allowed.contains(key)) throw new IllegalArgumentException("unknown gas reaction field: " + key);
            if (fields.get(key).isJsonNull()) throw new IllegalArgumentException("null gas reaction field: " + key);
        }
        for (String key : Set.of("minimumRequirements", "priority", "effects"))
            if (!fields.has(key)) throw new IllegalArgumentException("missing gas reaction field: " + key);
        if (!fields.get("minimumRequirements").isJsonObject()) throw new IllegalArgumentException("invalid gas requirements");
        for (var gas : fields.getAsJsonObject("minimumRequirements").entrySet()) {
            if (!gas.getValue().isJsonPrimitive() || !gas.getValue().getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException("invalid gas requirement: " + gas.getKey());
        }
        for (String key : Set.of("minimumTemperature", "maximumTemperature", "minimumEnergy", "priority"))
            if (fields.has(key) && (!fields.get(key).isJsonPrimitive() || !fields.get(key).getAsJsonPrimitive().isNumber()))
                throw new IllegalArgumentException("invalid reaction number: " + key);
        if (!fields.get("priority").getAsString().matches("-?(0|[1-9][0-9]*)"))
            throw new IllegalArgumentException("reaction priority must be an integer");
        if (!fields.get("effects").isJsonArray()) throw new IllegalArgumentException("invalid reaction effects");
        Set<String> seen = new HashSet<>();
        for (JsonElement effect : fields.getAsJsonArray("effects")) {
            if (!effect.isJsonObject()) throw new IllegalArgumentException("reaction effect must be an object");
            JsonObject object = effect.getAsJsonObject();
            if (object.size() != 1 || !object.has("type") || !object.get("type").isJsonPrimitive()
                    || !object.get("type").getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("invalid reaction effect fields");
            if (!seen.add(object.get("type").getAsString())) throw new IllegalArgumentException("duplicate reaction effect");
        }
    }
}
