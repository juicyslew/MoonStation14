package com.juicyslew.moonstation14.ms14.organ;

import com.google.gson.JsonElement;
import com.juicyslew.moonstation14.ms14.lung.LungComponent;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Set;

/** Mob-owned respiratory status, independent of whether lungs are attached. */
public record RespirationState(double saturation, boolean initialized, LungComponent.Phase phase) {
    public static final RespirationState UNINITIALIZED = new RespirationState(0, false, LungComponent.Phase.INHALING);
    private static final Codec<Double> SATURATION = Codec.DOUBLE.comapFlatMap(v ->
            Double.isFinite(v) && v >= -2 ? DataResult.success(v) : DataResult.error(() -> "invalid saturation"), v -> v);
    private static final Codec<LungComponent.Phase> PHASE = Codec.STRING.comapFlatMap(v -> {
        try { return DataResult.success(LungComponent.Phase.valueOf(v)); }
        catch (IllegalArgumentException e) { return DataResult.error(() -> "invalid phase"); }
    }, LungComponent.Phase::name);
    private static final Codec<RespirationState> RECORD = RecordCodecBuilder.create(i -> i.group(
            SATURATION.fieldOf("saturation").forGetter(RespirationState::saturation),
            Codec.BOOL.fieldOf("initialized").forGetter(RespirationState::initialized),
            PHASE.fieldOf("phase").forGetter(RespirationState::phase)
    ).apply(i, RespirationState::new));
    public static final Codec<RespirationState> CODEC = Codec.of(RECORD, new Decoder<>() {
        @Override public <T> DataResult<Pair<RespirationState, T>> decode(DynamicOps<T> ops, T input) {
            try {
                JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                if (!json.isJsonObject() || !json.getAsJsonObject().keySet().equals(Set.of("saturation", "initialized", "phase")))
                    return DataResult.error(() -> "invalid respiration fields");
                return RECORD.decode(ops, input);
            } catch (RuntimeException e) { return DataResult.error(() -> "invalid respiration: " + e.getMessage()); }
        }
    });
    public RespirationState {
        if (!Double.isFinite(saturation) || saturation < -2) throw new IllegalArgumentException("invalid saturation");
        Objects.requireNonNull(phase, "phase");
    }
    public static RespirationState from(LungComponent legacy) {
        return new RespirationState(legacy.saturation(), legacy.initialized(), legacy.phase());
    }
}
