package com.juicyslew.moonstation14.util;

import com.mojang.serialization.*;
import net.minecraft.resources.ResourceLocation;

public final class CodecHelpers {
    private CodecHelpers() {}

    public static final Codec<ResourceLocation> LENIENT_ID_CODEC = Codec.STRING.xmap(
            s -> s.contains(":") ? ResourceLocation.parse(s) : ResourceLocation.fromNamespaceAndPath("moonstation14", s),
            ResourceLocation::toString
    );
}
