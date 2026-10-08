package com.juicyslew.moonstation14.ms14.medical;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** Persistent selected operating mode and dose for an individual syringe. */
public record SyringeStackData(ResourceLocation mode, float transferAmountUnits) {
    public static final Codec<SyringeStackData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("mode").forGetter(SyringeStackData::mode),
            Codec.FLOAT.fieldOf("transfer_amount_units").forGetter(SyringeStackData::transferAmountUnits)
    ).apply(i, SyringeStackData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyringeStackData> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> { ResourceLocation.STREAM_CODEC.encode(buf, value.mode); buf.writeFloat(value.transferAmountUnits); },
            buf -> new SyringeStackData(ResourceLocation.STREAM_CODEC.decode(buf), buf.readFloat()));

    public SyringeStackData {
        if (mode == null || !Float.isFinite(transferAmountUnits) || transferAmountUnits <= 0f)
            throw new IllegalArgumentException("Syringe mode and positive finite transfer amount are required");
    }
}
