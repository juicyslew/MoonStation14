package com.juicyslew.moonstation14.ms14.examine.scene;

import com.juicyslew.moonstation14.ms14.examine.prototype.ExamineMetadata;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Resolved Minecraft sky-scene profile; this deliberately has no SS14 prototype equivalent. */
public record CelestialSceneData(String dimension, Side side, double radius, double skyDistance,
                                 int red, int green, int blue, ExamineMetadata metadata) {
    public enum Side { SUN, EARTH }
    public static final Codec<CelestialSceneData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("dimension", "minecraft:overworld").forGetter(CelestialSceneData::dimension),
            Codec.STRING.xmap(Side::valueOf, Side::name).optionalFieldOf("side", Side.SUN).forGetter(CelestialSceneData::side),
            Codec.DOUBLE.optionalFieldOf("radius", 9.0).forGetter(CelestialSceneData::radius),
            Codec.DOUBLE.optionalFieldOf("skyDistance", 80.0).forGetter(CelestialSceneData::skyDistance),
            Codec.INT.optionalFieldOf("red", 255).forGetter(CelestialSceneData::red),
            Codec.INT.optionalFieldOf("green", 255).forGetter(CelestialSceneData::green),
            Codec.INT.optionalFieldOf("blue", 245).forGetter(CelestialSceneData::blue),
            ExamineMetadata.CODEC.optionalFieldOf("metadata", ExamineMetadata.EMPTY).forGetter(CelestialSceneData::metadata)
    ).apply(i, CelestialSceneData::new));
    public CelestialSceneData {
        if (dimension == null || side == null || metadata == null || !Double.isFinite(radius) || radius <= 0
                || !Double.isFinite(skyDistance) || skyDistance <= 0 || red < 0 || red > 255
                || green < 0 || green > 255 || blue < 0 || blue > 255)
            throw new IllegalArgumentException("invalid celestial scene profile");
    }
}

