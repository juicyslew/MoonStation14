package com.juicyslew.moonstation14.ms14.botany;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Optional packet-level planting health; intentionally separate from inherited seed genetics. */
public record SeedPacketHealthOverride(float health) {
    public static final Codec<SeedPacketHealthOverride> CODEC = Codec.FLOAT
            .xmap(SeedPacketHealthOverride::new, SeedPacketHealthOverride::health);
    public static final StreamCodec<io.netty.buffer.ByteBuf, SeedPacketHealthOverride> STREAM_CODEC =
            ByteBufCodecs.FLOAT.map(SeedPacketHealthOverride::new, SeedPacketHealthOverride::health);
}
