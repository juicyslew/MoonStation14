package com.juicyslew.moonstation14.enums;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Objects;

public enum ReagentEnum implements StringRepresentable {
    WATER("0", 0x75b1f0),
    BICARIDINE("1", 0xFFAA00),
    INAPROVALINE("2", 0x731024),
    KELOTANE("3", 0xBF3D19);

    private final String id;
    private final int color;

    private ReagentEnum(final String id, final int color) {
        this.id = id;
        this.color = color;
    }

    public String getId() {
        return id;
    }
    public int getColor() {
        return color;
    }

    public static ReagentEnum getEnum(String id){
        for (ReagentEnum type : values()) {
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

    public static final Codec<ReagentEnum> CODEC =
            StringRepresentable.fromEnum(ReagentEnum::values);
}

