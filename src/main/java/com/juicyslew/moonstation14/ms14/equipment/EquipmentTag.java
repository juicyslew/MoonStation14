package com.juicyslew.moonstation14.ms14.equipment;
import com.mojang.serialization.Codec;
/** Only tags with an implemented equipment-policy meaning are admitted here. */
public enum EquipmentTag {
    PET_ONLY;
    public static final Codec<EquipmentTag> CODEC=Codec.STRING.comapFlatMap(value->{
        if("PetOnly".equals(value))return com.mojang.serialization.DataResult.success(PET_ONLY);
        return com.mojang.serialization.DataResult.error(()->"unsupported equipment tag "+value);
    },tag->tag==PET_ONLY?"PetOnly":tag.name());
}

