package com.juicyslew.moonstation14.ms14.atmos.tank;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared gas-id/moles representation for tank and canister persistence. */
final class GasCompositionCodec {
    static final Codec<Map<String, Double>> CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE);
    private GasCompositionCodec() { }
    static Map<String, Double> encode(Map<GasType, Double> gases) {
        var result = new LinkedHashMap<String, Double>();
        gases.forEach((gas, amount) -> result.put(gas.id(), amount));
        return result;
    }
    static Map<GasType, Double> decode(Map<String, Double> gases) {
        var result = new EnumMap<GasType, Double>(GasType.class);
        gases.forEach((id, amount) -> result.put(GasType.fromId(id), amount));
        return result;
    }
}

