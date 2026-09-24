package com.juicyslew.moonstation14.component.codec.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/** Data-only representation of a reagent tile reaction. */
public record TileReactionData(
        String type,
        Optional<Float> temperatureMultiplier,
        Optional<Float> cleanCost,
        Optional<String> entity,
        Optional<Float> usage,
        Optional<Integer> maxOnTile,
        Optional<Float> randomOffsetMax,
        Optional<TileReactionWhitelistData> maxOnTileWhitelist
) {
    public static final MapCodec<TileReactionData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(TileReactionData::type),
            Codec.FLOAT.optionalFieldOf("temperaturemultiplier").forGetter(TileReactionData::temperatureMultiplier),
            Codec.FLOAT.optionalFieldOf("cleancost").forGetter(TileReactionData::cleanCost),
            Codec.STRING.optionalFieldOf("entity").forGetter(TileReactionData::entity),
            Codec.FLOAT.optionalFieldOf("usage").forGetter(TileReactionData::usage),
            Codec.INT.optionalFieldOf("maxontile").forGetter(TileReactionData::maxOnTile),
            Codec.FLOAT.optionalFieldOf("randomoffsetmax").forGetter(TileReactionData::randomOffsetMax),
            TileReactionWhitelistData.CODEC.codec().optionalFieldOf("maxontilewhitelist").forGetter(TileReactionData::maxOnTileWhitelist)
    ).apply(instance, TileReactionData::new));
}
