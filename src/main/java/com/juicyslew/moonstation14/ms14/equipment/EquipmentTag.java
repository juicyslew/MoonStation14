package com.juicyslew.moonstation14.ms14.equipment;
import com.mojang.serialization.Codec;
/** Only tags with an implemented equipment-policy meaning are admitted here. */
public enum EquipmentTag {
    PET_ONLY, PET_WEARABLE, HAMSTER_WEARABLE, MONKEY_WEARABLE, KANGAROO_WEARABLE;
    public static final Codec<EquipmentTag> CODEC=Codec.STRING.comapFlatMap(value->{
        if("PetOnly".equals(value))return com.mojang.serialization.DataResult.success(PET_ONLY);
        if("PetWearable".equals(value))return com.mojang.serialization.DataResult.success(PET_WEARABLE);
        if("HamsterWearable".equals(value))return com.mojang.serialization.DataResult.success(HAMSTER_WEARABLE);
        if("MonkeyWearable".equals(value))return com.mojang.serialization.DataResult.success(MONKEY_WEARABLE);
        if("Kangaroo".equals(value))return com.mojang.serialization.DataResult.success(KANGAROO_WEARABLE);
        return com.mojang.serialization.DataResult.error(()->"unsupported equipment tag "+value);
    },tag->switch(tag){case PET_ONLY->"PetOnly";case PET_WEARABLE->"PetWearable";case HAMSTER_WEARABLE->"HamsterWearable";case MONKEY_WEARABLE->"MonkeyWearable";case KANGAROO_WEARABLE->"Kangaroo";});
}
