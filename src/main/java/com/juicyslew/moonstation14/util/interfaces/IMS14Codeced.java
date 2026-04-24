package com.juicyslew.moonstation14.util.interfaces;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public interface IMS14Codeced<T> {
    public Codec<T> getCodec();
    public StreamCodec<RegistryFriendlyByteBuf, T> getStreamCodec();
}
