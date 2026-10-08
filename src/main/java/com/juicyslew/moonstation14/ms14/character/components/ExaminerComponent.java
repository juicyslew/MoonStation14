package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Resolved SS14 Examiner behavior profile; range values are system policy, not prototype fields. */
public record ExaminerComponent(boolean skipChecks, boolean checkInRangeUnOccluded) implements CharacterComponent {
    public static final String TYPE = "Examiner";
    public static final ExaminerComponent DEFAULT = new ExaminerComponent(false, true);
    public static final Codec<ExaminerComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(ExaminerComponent::type),
            Codec.BOOL.optionalFieldOf("skip_checks", false).forGetter(ExaminerComponent::skipChecks),
            Codec.BOOL.optionalFieldOf("check_in_range_un_occluded", true).forGetter(ExaminerComponent::checkInRangeUnOccluded)
    ).apply(i, (type, skip, range) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown character component type: " + type);
        return new ExaminerComponent(skip, range);
    }));
    @Override public String type() { return TYPE; }
}
