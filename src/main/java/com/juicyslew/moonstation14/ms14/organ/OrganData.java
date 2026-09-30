package com.juicyslew.moonstation14.ms14.organ;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.Optional;
import java.util.Map;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;

/** Prototype identity comes from its catalog key; Lung owns gas processing tuning. */
public record OrganData(OrganCategory category, Optional<Lung> lung) {
    public record Lung(double maxLungMoles, double breathMolesToSaturationMultiplier,
                       Map<String, Map<String, Double>> toxicGasDamagePerMole, double toxicGasDamageCapPerInhale) {
        private static final Codec<Lung> STRUCTURAL = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("max_lung_moles").forGetter(Lung::maxLungMoles),
                Codec.DOUBLE.fieldOf("breath_moles_to_saturation_multiplier").forGetter(Lung::breathMolesToSaturationMultiplier),
                Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.DOUBLE))
                        .fieldOf("toxic_gas_damage_per_mole").forGetter(Lung::toxicGasDamagePerMole),
                Codec.DOUBLE.fieldOf("toxic_gas_damage_cap_per_inhale").forGetter(Lung::toxicGasDamageCapPerInhale)
        ).apply(i, Lung::new));
        public static final Codec<Lung> CODEC = Codec.of(STRUCTURAL, new Decoder<>() {
            @Override public <T> DataResult<Pair<Lung, T>> decode(DynamicOps<T> ops, T input) {
                try {
                    JsonElement json = input instanceof JsonElement element ? element : ops.convertTo(JsonOps.INSTANCE, input);
                    auditLung(json);
                    return STRUCTURAL.decode(ops, input);
                } catch (RuntimeException ex) {
                    return DataResult.error(() -> ex.getMessage() == null ? "invalid Lung" : ex.getMessage());
                }
            }
        });

        public Lung {
            if (!Double.isFinite(maxLungMoles) || maxLungMoles <= 0 || maxLungMoles > 1_000_000
                    || !Double.isFinite(breathMolesToSaturationMultiplier) || breathMolesToSaturationMultiplier <= 0
                    || breathMolesToSaturationMultiplier > 1_000_000
                    || !Double.isFinite(toxicGasDamageCapPerInhale) || toxicGasDamageCapPerInhale < 0
                    || toxicGasDamageCapPerInhale > 1_000_000) throw new IllegalArgumentException("invalid Lung tuning");
            if (toxicGasDamagePerMole == null) throw new IllegalArgumentException("missing toxic gas map");
            for (var entry : toxicGasDamagePerMole.entrySet()) {
                if (java.util.Arrays.stream(GasType.values()).noneMatch(g -> g.id().equals(entry.getKey())))
                    throw new IllegalArgumentException("unknown Lung gas: " + entry.getKey());
                if (entry.getValue() == null) throw new IllegalArgumentException("invalid Lung damage map");
                for (var damage : entry.getValue().entrySet())
                    if (!DamageKeys.ALL.contains(damage.getKey()) || damage.getValue() == null
                            || !Double.isFinite(damage.getValue()) || damage.getValue() < 0 || damage.getValue() > 1_000_000)
                        throw new IllegalArgumentException("invalid Lung damage: " + damage.getKey());
            }
            toxicGasDamagePerMole = toxicGasDamagePerMole.entrySet().stream().collect(
                    java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> Map.copyOf(e.getValue())));
        }
    }

    private static final Codec<OrganData> STRUCTURAL = RecordCodecBuilder.create(instance -> instance.group(
            OrganCategory.CODEC.fieldOf("category").forGetter(OrganData::category),
            Lung.CODEC.optionalFieldOf("Lung").forGetter(OrganData::lung)
    ).apply(instance, OrganData::new));

    public static final Codec<OrganData> CODEC = Codec.of(STRUCTURAL, new Decoder<>() {
        @Override public <T> DataResult<Pair<OrganData, T>> decode(DynamicOps<T> ops, T input) {
            try {
                JsonElement json = input instanceof JsonElement element ? element : ops.convertTo(JsonOps.INSTANCE, input);
                if (!json.isJsonObject()) return DataResult.error(() -> "organ must be an object");
                JsonObject object = json.getAsJsonObject();
                for (String key : object.keySet())
                    if (!Set.of("category", "Lung").contains(key)) throw new IllegalArgumentException("unknown organ field: " + key);
                if (object.has("Lung")) {
                    auditLung(object.get("Lung"));
                }
                return STRUCTURAL.decode(ops, input);
            } catch (RuntimeException ex) {
                return DataResult.error(() -> ex.getMessage() == null ? "invalid organ" : ex.getMessage());
            }
        }
    });

    private static void auditLung(JsonElement json) {
        if (json == null || !json.isJsonObject()) throw new IllegalArgumentException("Lung must be an object");
        JsonObject fields = json.getAsJsonObject();
        Set<String> allowed = Set.of("max_lung_moles", "breath_moles_to_saturation_multiplier",
                "toxic_gas_damage_per_mole", "toxic_gas_damage_cap_per_inhale");
        for (String key : fields.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("unknown Lung field: " + key);
        for (String key : allowed) if (!fields.has(key) || fields.get(key).isJsonNull())
            throw new IllegalArgumentException("missing or null Lung field: " + key);
        if (!fields.get("toxic_gas_damage_per_mole").isJsonObject()) throw new IllegalArgumentException("invalid Lung gas map");
        for (var gas : fields.getAsJsonObject("toxic_gas_damage_per_mole").entrySet()) {
            if (!gas.getValue().isJsonObject()) throw new IllegalArgumentException("invalid Lung gas damage: " + gas.getKey());
            for (var damage : gas.getValue().getAsJsonObject().entrySet())
                if (!damage.getValue().isJsonPrimitive() || !damage.getValue().getAsJsonPrimitive().isNumber())
                    throw new IllegalArgumentException("invalid Lung damage: " + damage.getKey());
        }
        for (String key : allowed) if (!key.equals("toxic_gas_damage_per_mole")
                && (!fields.get(key).isJsonPrimitive() || !fields.get(key).getAsJsonPrimitive().isNumber()))
            throw new IllegalArgumentException("invalid Lung number: " + key);
    }

    public OrganData {
        if (category == null || lung == null || (category == OrganCategory.LUNGS) != lung.isPresent())
            throw new IllegalArgumentException("Lungs category requires Lung marker; other categories forbid Lung marker");
    }
}
