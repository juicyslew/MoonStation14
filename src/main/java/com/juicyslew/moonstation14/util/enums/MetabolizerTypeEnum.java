package com.juicyslew.moonstation14.util.enums;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Objects;

public enum MetabolizerTypeEnum implements StringRepresentable {
    HUMAN("human"),
    ANIMAL("animal"),
    RAT("rat"),
    PLANT("plant"),
    ARACHNID("arachnid"),
    MOTH("moth"),
    VOX("vox"),
    DWARF("dwarf"),
    BLOODSUCKER("bloodsucker"),
    SLIME("slime");

    private final String id;

    private MetabolizerTypeEnum(final String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static MetabolizerTypeEnum getEnum(String id) {
        for (MetabolizerTypeEnum type : values()) {
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

    public static final Codec<MetabolizerTypeEnum> CODEC =
            StringRepresentable.fromEnum(MetabolizerTypeEnum::values);
}