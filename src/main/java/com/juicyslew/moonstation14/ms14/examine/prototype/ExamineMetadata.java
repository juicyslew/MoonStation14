package com.juicyslew.moonstation14.ms14.examine.prototype;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;

/** Source entity-prototype identity metadata, separate from composed behavior. */
public record ExamineMetadata(String name, String description) {
    public static final ExamineMetadata EMPTY = new ExamineMetadata("", "");
    public static final Codec<ExamineMetadata> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(ExamineMetadata::name),
            Codec.STRING.optionalFieldOf("description", "").forGetter(ExamineMetadata::description)
    ).apply(i, ExamineMetadata::new));

    public ExamineMetadata {
        name = Objects.requireNonNull(name, "name");
        description = Objects.requireNonNull(description, "description");
        if (name.length() > 128 || description.length() > 512)
            throw new IllegalArgumentException("examine metadata exceeds length limit");
    }

    public boolean hasName() { return !name.isBlank(); }
    public boolean hasDescription() { return !description.isBlank(); }
}

