package com.juicyslew.moonstation14.ms14.jug;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.examine.prototype.ExamineMetadata;
import com.juicyslew.moonstation14.ms14.reagent.DrainableSolutionPolicy;
import com.juicyslew.moonstation14.ms14.reagent.RefillableSolutionPolicy;
import com.juicyslew.moonstation14.ms14.reagent.SolutionTransferPolicy;
import com.juicyslew.moonstation14.ms14.reagent.DrinkSolutionPolicy;

/** Supported Jug composition: metadata, logical drink solution and its examine policy. */
public record JugData(ExamineMetadata metadata, SolutionContainer solutionContainer,
                      ExaminableSolution examinableSolution, DrainableSolutionPolicy drainableSolution,
                      RefillableSolutionPolicy refillableSolution, SolutionTransferPolicy solutionTransfer,
                      DrinkSolutionPolicy drinkSolution) {
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
            ExaminableSolution.CODEC.optionalFieldOf("examinableSolution", new ExaminableSolution(DEFAULT_SOLUTION_NAME, true, false, true, 3)).forGetter(JugData::examinableSolution),
            DrainableSolutionPolicy.CODEC.optionalFieldOf("drainableSolution", DrainableSolutionPolicy.DISABLED).forGetter(JugData::drainableSolution),
            RefillableSolutionPolicy.CODEC.optionalFieldOf("refillableSolution", RefillableSolutionPolicy.DISABLED).forGetter(JugData::refillableSolution),
            SolutionTransferPolicy.CODEC.optionalFieldOf("solutionTransfer", SolutionTransferPolicy.DISABLED).forGetter(JugData::solutionTransfer),
            DrinkSolutionPolicy.CODEC.optionalFieldOf("drinkSolution", DrinkSolutionPolicy.DISABLED).forGetter(JugData::drinkSolution)
    ).apply(i, JugData::new));
    public static final Codec<JugData> CODEC = STRUCTURAL;

    public JugData {
        if (metadata == null || solutionContainer == null || examinableSolution == null || drainableSolution == null || refillableSolution == null || solutionTransfer == null || drinkSolution == null)
            throw new IllegalArgumentException("missing jug composition");
    }
    public JugData(ExamineMetadata metadata, SolutionContainer solutionContainer, ExaminableSolution examinableSolution) {
        this(metadata, solutionContainer, examinableSolution, new DrainableSolutionPolicy(true, "drink"), RefillableSolutionPolicy.DISABLED, SolutionTransferPolicy.DISABLED, DrinkSolutionPolicy.DISABLED);
    }
    public JugData(ExamineMetadata metadata, SolutionContainer solutionContainer, ExaminableSolution examinableSolution,
                   DrainableSolutionPolicy drainableSolution) {
        this(metadata, solutionContainer, examinableSolution, drainableSolution, RefillableSolutionPolicy.DISABLED, SolutionTransferPolicy.DISABLED, DrinkSolutionPolicy.DISABLED);
    }
}

