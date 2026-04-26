package com.juicyslew.moonstation14.util.enums;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Objects;

public enum ContrabandSeverityEnum implements StringRepresentable {
    NONE("none"),
    MINOR("minor"),
    MAJOR("major"),
    SYNDICATE("syndicate");

    private final String id;

    private ContrabandSeverityEnum(final String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static ContrabandSeverityEnum getEnum(String id) {
        for (ContrabandSeverityEnum type : values()) {
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

    public static final Codec<ContrabandSeverityEnum> CODEC =
            StringRepresentable.fromEnum(ContrabandSeverityEnum::values);
}
