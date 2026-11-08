package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** Independent codecs shared by profile records without initializing their enclosing profile. */
final class ProfileCodecHelpers {
    private ProfileCodecHelpers() { }

    static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.comapFlatMap(value -> {
            for (E element : type.getEnumConstants())
                if (element.name().equalsIgnoreCase(value.replace('-', '_'))) return DataResult.success(element);
            return DataResult.error(() -> "unknown " + type.getSimpleName() + ": " + value);
        }, value -> value.name().toLowerCase(java.util.Locale.ROOT));
    }

    static final Codec<GasType> GAS_CODEC = Codec.STRING.comapFlatMap(value -> {
        try { return DataResult.success(GasType.fromId(value)); }
        catch (IllegalArgumentException exception) { return DataResult.error(exception::getMessage); }
    }, GasType::id);
}
