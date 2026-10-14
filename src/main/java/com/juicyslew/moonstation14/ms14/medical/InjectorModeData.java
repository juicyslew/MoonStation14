package com.juicyslew.moonstation14.ms14.medical;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Typed transfer mode used by injector prototypes. */
public record InjectorModeData(Kind kind, java.util.List<Float> transferAmounts, float ignoreDelayForVolume, float mobTimeSeconds,
                               float containerDrawTimeSeconds, float additionalDelayPerUnitSeconds,
                               float selfModifier, float downedModifier) {
    public enum Kind { DRAW, INJECT }
    public static final Codec<Kind> KIND_CODEC = Codec.STRING.comapFlatMap(value -> switch (value) {
        case "draw" -> com.mojang.serialization.DataResult.success(Kind.DRAW);
        case "inject" -> com.mojang.serialization.DataResult.success(Kind.INJECT);
        default -> com.mojang.serialization.DataResult.error(() -> "unknown injector mode kind: " + value);
    }, kind -> kind == Kind.DRAW ? "draw" : "inject");
    public static final Codec<InjectorModeData> CODEC = RecordCodecBuilder.create(i -> i.group(
            KIND_CODEC.fieldOf("kind").forGetter(InjectorModeData::kind),
            Codec.FLOAT.listOf().fieldOf("transfer_amounts").forGetter(InjectorModeData::transferAmounts),
            Codec.FLOAT.fieldOf("ignore_delay_for_volume").forGetter(InjectorModeData::ignoreDelayForVolume),
            Codec.FLOAT.fieldOf("mob_time_seconds").forGetter(InjectorModeData::mobTimeSeconds),
            Codec.FLOAT.fieldOf("container_draw_time_seconds").forGetter(InjectorModeData::containerDrawTimeSeconds),
            Codec.FLOAT.fieldOf("additional_delay_per_unit_seconds").forGetter(InjectorModeData::additionalDelayPerUnitSeconds),
            Codec.FLOAT.fieldOf("self_modifier").forGetter(InjectorModeData::selfModifier),
            Codec.FLOAT.fieldOf("downed_modifier").forGetter(InjectorModeData::downedModifier)
    ).apply(i, InjectorModeData::new));
    public InjectorModeData {
        transferAmounts = java.util.List.copyOf(transferAmounts);
        if (kind == null || transferAmounts.isEmpty() || transferAmounts.stream().anyMatch(amount -> amount == null || !positive(amount))
                || transferAmounts.stream().distinct().count() != transferAmounts.size()
                || !transferAmounts.equals(transferAmounts.stream().sorted().toList())
                || !nonnegative(ignoreDelayForVolume) || !transferAmounts.contains(ignoreDelayForVolume)
                || !nonnegative(mobTimeSeconds) || !nonnegative(containerDrawTimeSeconds)
                || !nonnegative(additionalDelayPerUnitSeconds) || !positive(selfModifier) || !positive(downedModifier))
            throw new IllegalArgumentException("invalid injector mode");
    }
    private static boolean positive(float f) { return Float.isFinite(f) && f > 0; }
    private static boolean nonnegative(float f) { return Float.isFinite(f) && f >= 0; }
}

