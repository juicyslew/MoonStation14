package com.juicyslew.moonstation14.util.enums;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public enum DamageEnum implements StringRepresentable {
    // TODO: Probably eventually replace with a Reagent class, and hydrate it all from Json files rather than this enum list.
    BLUNT("blunt"),
    PIERCE("pierce"),
    SLASH("slash"),
    HEAT("heat"),
    COLD("cold"),
    SHOCK("shock"),
    ASPHYXIATION("asphyxiation"),
    BLOODLOSS("bloodloss"),
    CAUSTIC("caustic"),
    POISON("poison"),
    RADIATION("radiation"),
    CELLULAR("cellular"),
    HOLY("holy");


    // TODO: Figure out the best way to make use of these groups
    public final static Map<String, List<DamageEnum>> DAMAGE_GROUPS = Map.of(
        "brute", List.of(BLUNT, PIERCE, SLASH),
        "burn", List.of(HEAT, COLD, SHOCK, CAUSTIC),
        "airloss", List.of(ASPHYXIATION, BLOODLOSS),
        "toxin", List.of(POISON, RADIATION),
        "genetic", List.of(CELLULAR),
        "metaphysical", List.of(HOLY)
    );
    // TODO: Implement modifier sets, and damage container types

    private final String id;

    private DamageEnum(final String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static DamageEnum getEnum(String id) {
        for (DamageEnum type : values()) {
            if (Objects.equals(type.getId(), id)) {
                return type;
            }
        }
        return null;
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }

    public static final Codec<DamageEnum> CODEC =
            StringRepresentable.fromEnum(DamageEnum::values);
}

