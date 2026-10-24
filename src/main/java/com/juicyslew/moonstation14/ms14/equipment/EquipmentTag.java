package com.juicyslew.moonstation14.ms14.equipment;
import com.mojang.serialization.Codec;
/** Only tags with an implemented equipment-policy meaning are admitted here. */
public enum EquipmentTag {
    PET_ONLY, HARDSUIT;
    public static final Codec<EquipmentTag> CODEC=Codec.STRING.comapFlatMap(value->{
        if("PetOnly".equals(value))return com.mojang.serialization.DataResult.success(PET_ONLY);
        if("Hardsuit".equals(value))return com.mojang.serialization.DataResult.success(HARDSUIT);
        return com.mojang.serialization.DataResult.error(()->"unsupported equipment tag "+value);
    },tag->tag==PET_ONLY?"PetOnly":tag==HARDSUIT?"Hardsuit":tag.name());
}
