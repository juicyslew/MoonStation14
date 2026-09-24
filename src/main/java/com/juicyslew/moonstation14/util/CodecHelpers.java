package com.juicyslew.moonstation14.util;

import com.mojang.serialization.*;
import net.minecraft.resources.ResourceLocation;

public final class CodecHelpers {
    private CodecHelpers() {}

    public static final Codec<ResourceLocation> LENIENT_ID_CODEC = Codec.STRING.comapFlatMap(
            value -> {
                try {
                    return com.mojang.serialization.DataResult.success(value.contains(":")
                            ? ResourceLocation.parse(value)
                            : ResourceLocation.fromNamespaceAndPath("moonstation14", value));
                } catch (RuntimeException exception) {
                    String message = exception.getMessage() == null
                            ? exception.getClass().getSimpleName() : exception.getMessage();
                    return com.mojang.serialization.DataResult.error(() -> message);
                }
            },
            ResourceLocation::toString
    );
}
