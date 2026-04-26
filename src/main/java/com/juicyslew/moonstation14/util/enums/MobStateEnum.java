package com.juicyslew.moonstation14.util.enums;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Objects;

public enum MobStateEnum implements StringRepresentable {
    ALIVE("alive"),
    CRITICAL("critical"),
    DEAD("dead");

    private final String id;

    private MobStateEnum(final String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static MobStateEnum getEnum(String id) {
        for (MobStateEnum type : values()) {
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

    public static final Codec<MobStateEnum> CODEC =
            StringRepresentable.fromEnum(MobStateEnum::values);
}
