package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.TreeMap;

/** Damage totals at which the corresponding state becomes applicable. */
public record MobThresholdsComponent(Map<Double, MobStateComponent.State> thresholds, boolean allowRevives) implements CharacterComponent {
    public static final String TYPE = "MobThresholds";
    private static final Codec<Double> THRESHOLD_KEY = Codec.STRING.comapFlatMap(value -> {
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) && parsed >= 0
                    ? DataResult.success(parsed == 0d ? 0d : parsed)
                    : DataResult.error(() -> "threshold key must be finite and nonnegative: " + value);
        } catch (NumberFormatException exception) {
            return DataResult.error(() -> "invalid numeric threshold key: " + value);
        }
    }, value -> Double.toString(value == 0d ? 0d : value));
    private static final Codec<Map<Double, MobStateComponent.State>> THRESHOLDS = Codec.unboundedMap(THRESHOLD_KEY, MobStateComponent.STATE_CODEC);
    private record Fields(String type, Map<Double, MobStateComponent.State> thresholds, boolean allowRevives) {}
    private static final Codec<Fields> FIELDS_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(Fields::type),
            THRESHOLDS.fieldOf("thresholds").forGetter(Fields::thresholds),
            Codec.BOOL.optionalFieldOf("allow_revives", false).forGetter(Fields::allowRevives)
    ).apply(i, Fields::new));
    public static final Codec<MobThresholdsComponent> CODEC = FIELDS_CODEC.flatXmap(fields -> {
        if (!TYPE.equals(fields.type())) return DataResult.error(() -> "unknown component type: " + fields.type());
        try { return DataResult.success(new MobThresholdsComponent(fields.thresholds(), fields.allowRevives())); }
        catch (IllegalArgumentException exception) { return DataResult.error(exception::getMessage); }
    }, component -> DataResult.success(new Fields(component.type(), component.thresholds(), component.allowRevives())));
    public MobThresholdsComponent {
        if (thresholds == null || thresholds.isEmpty()) throw new IllegalArgumentException("thresholds must not be empty");
        TreeMap<Double, MobStateComponent.State> sorted = new TreeMap<>();
        thresholds.forEach((amount, state) -> {
            if (amount == null || !Double.isFinite(amount) || amount < 0 || state == null || sorted.put(amount, state) != null)
                throw new IllegalArgumentException("thresholds require unique finite nonnegative values and states");
        });
        if (!MobStateComponent.State.ALIVE.equals(sorted.get(0d))) throw new IllegalArgumentException("thresholds require Alive at 0");
        thresholds = java.util.Collections.unmodifiableMap(sorted);
    }
    @Override public String type() { return TYPE; }
}

