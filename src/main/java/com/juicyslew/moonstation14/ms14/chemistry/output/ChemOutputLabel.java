package com.juicyslew.moonstation14.ms14.chemistry.output;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Raw bounded ChemMaster package label; distinct from the localized display-name modifier. */
public record ChemOutputLabel(String value) {
    public static final int MAX_LENGTH = 50;
    public static final Codec<ChemOutputLabel> CODEC = Codec.STRING.validate(value ->
            value == null || value.length() > MAX_LENGTH ? DataResult.<String>error(() -> "package label exceeds 50 UTF-16 units")
                    : DataResult.success(value)).xmap(ChemOutputLabel::new, ChemOutputLabel::value);
    public static final StreamCodec<io.netty.buffer.ByteBuf, ChemOutputLabel> STREAM_CODEC =
            ByteBufCodecs.fromCodec(CODEC);
    public ChemOutputLabel { if (value == null || value.length() > MAX_LENGTH) throw new IllegalArgumentException("invalid package label"); }
}
