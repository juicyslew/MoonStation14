package com.juicyslew.moonstation14.component.codec.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record TileReactionWhitelistData(List<String> tags) {
    public static final MapCodec<TileReactionWhitelistData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.list(Codec.STRING).fieldOf("tags").forGetter(TileReactionWhitelistData::tags)
    ).apply(instance, TileReactionWhitelistData::new));
}
