package com.juicyslew.moonstation14.ms14.organ;

import com.google.gson.JsonElement;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.lung.LungComponent;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;

/** Organ-owned inventory only; physiological status belongs to the body. */
public record LungOrganState(Map<String, Double> gasMoles, double temperatureKelvin) {
    public static final LungOrganState EMPTY = new LungOrganState(Map.of(), 2.7);
    private static final Codec<Double> NONNEGATIVE = Codec.DOUBLE.comapFlatMap(v ->
            Double.isFinite(v) && v >= 0 ? DataResult.success(v) : DataResult.error(() -> "invalid nonnegative value"), v -> v);
    private static final Codec<LungOrganState> RECORD = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, NONNEGATIVE).fieldOf("gas_moles").forGetter(LungOrganState::gasMoles),
            NONNEGATIVE.fieldOf("temperature_kelvin").forGetter(LungOrganState::temperatureKelvin)
    ).apply(i, LungOrganState::new));
    public static final Codec<LungOrganState> CODEC = Codec.of(RECORD, new Decoder<>() {
        @Override public <T> DataResult<Pair<LungOrganState, T>> decode(DynamicOps<T> ops, T input) {
            try {
                JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                if (!json.isJsonObject() || !json.getAsJsonObject().keySet().equals(Set.of("gas_moles", "temperature_kelvin")))
                    return DataResult.error(() -> "invalid lung inventory fields");
                return RECORD.decode(ops, input);
            } catch (RuntimeException e) { return DataResult.error(() -> "invalid lung inventory: " + e.getMessage()); }
        }
    });
    public LungOrganState {
        Objects.requireNonNull(gasMoles, "gasMoles");
        if (!Double.isFinite(temperatureKelvin) || temperatureKelvin < 0) throw new IllegalArgumentException("invalid temperature");
        if (gasMoles.size() > GasType.values().length) throw new IllegalArgumentException("too many gases");
        TreeMap<String, Double> copy = new TreeMap<>();
        EnumMap<GasType, Double> gases = new EnumMap<>(GasType.class);
        gasMoles.forEach((id, amount) -> {
            GasType gas = GasType.fromId(Objects.requireNonNull(id));
            if (!id.equals(gas.id())) throw new IllegalArgumentException("noncanonical gas id: " + id);
            if (amount == null || !Double.isFinite(amount) || amount <= 0) throw new IllegalArgumentException("invalid gas quantity");
            if (gases.putIfAbsent(gas, amount) != null) throw new IllegalArgumentException("duplicate gas species: " + id);
            copy.put(id, amount);
        });
        new GasMixture(gases, temperatureKelvin); // Reject overflowing aggregates.
        gasMoles = Collections.unmodifiableMap(copy);
    }
    public GasMixture mixture() {
        EnumMap<GasType, Double> gases = new EnumMap<>(GasType.class);
        gasMoles.forEach((id, amount) -> gases.put(GasType.fromId(id), amount));
        return new GasMixture(gases, temperatureKelvin);
    }
    public static LungOrganState from(LungComponent legacy) {
        return new LungOrganState(legacy.gasMoles(), legacy.temperatureKelvin());
    }
}
