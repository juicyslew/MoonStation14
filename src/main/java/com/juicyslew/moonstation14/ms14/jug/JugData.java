package com.juicyslew.moonstation14.ms14.jug;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.examine.prototype.ExamineMetadata;

/** Supported Jug composition: metadata, logical drink solution and its examine policy. */
public record JugData(ExamineMetadata metadata, SolutionContainer solutionContainer,
                      ExaminableSolution examinableSolution) {
    public static final float DEFAULT_CAPACITY = 200f;
    public static final String DEFAULT_SOLUTION_NAME = "drink";

    public record SolutionContainer(String solutionName, float maxVolume) {
        public static final Codec<SolutionContainer> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("solutionName", DEFAULT_SOLUTION_NAME).forGetter(SolutionContainer::solutionName),
                Codec.FLOAT.optionalFieldOf("maxVolume", DEFAULT_CAPACITY).forGetter(SolutionContainer::maxVolume)
        ).apply(i, SolutionContainer::new));
        public SolutionContainer {
            if (solutionName == null || solutionName.isBlank() || !Float.isFinite(maxVolume) || maxVolume <= 0)
                throw new IllegalArgumentException("invalid jug solution container");
        }
    }

    public record ExaminableSolution(String solutionName, boolean exactVolume,
                                     boolean heldOnly, boolean examinableWhileClosed, int maxDetails) {
        public static final Codec<ExaminableSolution> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("solutionName", DEFAULT_SOLUTION_NAME).forGetter(ExaminableSolution::solutionName),
                Codec.BOOL.optionalFieldOf("exactVolume", true).forGetter(ExaminableSolution::exactVolume),
                Codec.BOOL.optionalFieldOf("heldOnly", false).forGetter(ExaminableSolution::heldOnly),
                Codec.BOOL.optionalFieldOf("examinableWhileClosed", true).forGetter(ExaminableSolution::examinableWhileClosed),
                Codec.INT.optionalFieldOf("maxDetails", 3).forGetter(ExaminableSolution::maxDetails)
        ).apply(i, ExaminableSolution::new));
        public ExaminableSolution {
            if (solutionName == null || solutionName.isBlank() || maxDetails < 0)
                throw new IllegalArgumentException("invalid jug examinable solution");
        }
    }

    private static final Codec<JugData> STRUCTURAL = RecordCodecBuilder.create(i -> i.group(
            ExamineMetadata.CODEC.optionalFieldOf("metadata", ExamineMetadata.EMPTY).forGetter(JugData::metadata),
            SolutionContainer.CODEC.optionalFieldOf("solutionContainer", new SolutionContainer(DEFAULT_SOLUTION_NAME, DEFAULT_CAPACITY)).forGetter(JugData::solutionContainer),
            ExaminableSolution.CODEC.optionalFieldOf("examinableSolution", new ExaminableSolution(DEFAULT_SOLUTION_NAME, true, false, true, 3)).forGetter(JugData::examinableSolution)
    ).apply(i, JugData::new));
    public static final Codec<JugData> CODEC = STRUCTURAL;

    public JugData {
        if (metadata == null || solutionContainer == null || examinableSolution == null)
            throw new IllegalArgumentException("missing jug composition");
    }
}
