package com.juicyslew.moonstation14.ms14.chemistry.beaker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.reagent.DrainableSolutionPolicy;
import com.juicyslew.moonstation14.ms14.reagent.RefillableSolutionPolicy;
import com.juicyslew.moonstation14.ms14.reagent.SolutionTransferPolicy;
import com.juicyslew.moonstation14.ms14.reagent.DrinkSolutionPolicy;

/** Supported inherited beaker composition; unsupported SS14 component machinery is not fabricated. */
public record BeakerData(SolutionContainer solutionContainer, boolean fitsInDispenser,
                         ExaminableSolution examinableSolution, DrainableSolutionPolicy drainableSolution,
                          SolutionVisuals solutionVisuals, RefillableSolutionPolicy refillableSolution,
                          SolutionTransferPolicy solutionTransfer, DrinkSolutionPolicy drinkSolution) {
    public static final String BASE_ID = "beaker_base";
    public static final String SMALL_ID = "beaker";
    public static final String LARGE_ID = "beaker_large";

    public record SolutionContainer(String solutionName, int maxVolume) {
        public static final Codec<SolutionContainer> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("solutionName", "beaker").forGetter(SolutionContainer::solutionName),
                Codec.INT.fieldOf("maxVolume").forGetter(SolutionContainer::maxVolume)
        ).apply(i, SolutionContainer::new));
        public SolutionContainer {
            if (solutionName == null || solutionName.isBlank() || maxVolume <= 0)
                throw new IllegalArgumentException("invalid beaker solution container");
        }
    }

    /** Supported projection of SS14 SolutionContainerVisuals; null means no visual policy. */
    public record SolutionVisuals(String solutionName, String baseState, String fillStatePrefix,
                                  int maxFillLevels, boolean changeColor) {
        public static final Codec<SolutionVisuals> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("solutionName").forGetter(SolutionVisuals::solutionName),
                Codec.STRING.fieldOf("baseState").forGetter(SolutionVisuals::baseState),
                Codec.STRING.fieldOf("fillStatePrefix").forGetter(SolutionVisuals::fillStatePrefix),
                Codec.INT.optionalFieldOf("maxFillLevels", 6).forGetter(SolutionVisuals::maxFillLevels),
                Codec.BOOL.optionalFieldOf("changeColor", true).forGetter(SolutionVisuals::changeColor)
        ).apply(i, SolutionVisuals::new));
        public SolutionVisuals {
            if (solutionName == null || solutionName.isBlank() || baseState == null || baseState.isBlank()
                    || fillStatePrefix == null || fillStatePrefix.isBlank() || maxFillLevels <= 1 || maxFillLevels > 64)
                throw new IllegalArgumentException("invalid beaker solution visuals");
        }
    }

    /** SS14 ExaminableSolution subset. Upstream component defaults: heldOnly=false,
     * exactVolume=false, examinableWhileClosed=true; BaseBeaker explicitly sets exactVolume=true. */
    public record ExaminableSolution(boolean enabled, String solutionName, boolean exactVolume,
                                     boolean heldOnly, boolean examinableWhileClosed) {
        public static final Codec<ExaminableSolution> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("enabled", false).forGetter(ExaminableSolution::enabled),
                Codec.STRING.optionalFieldOf("solutionName", "beaker").forGetter(ExaminableSolution::solutionName),
                Codec.BOOL.optionalFieldOf("exactVolume", false).forGetter(ExaminableSolution::exactVolume),
                Codec.BOOL.optionalFieldOf("heldOnly", false).forGetter(ExaminableSolution::heldOnly),
                Codec.BOOL.optionalFieldOf("examinableWhileClosed", true).forGetter(ExaminableSolution::examinableWhileClosed)
        ).apply(i, ExaminableSolution::new));
        public ExaminableSolution {
            if (solutionName == null || solutionName.isBlank()) throw new IllegalArgumentException("invalid beaker examine policy");
        }
    }

    private static final Codec<BeakerData> STRUCTURAL = RecordCodecBuilder.create(i -> i.group(
            SolutionContainer.CODEC.optionalFieldOf("solutionContainer", new SolutionContainer("beaker", 50))
                    .forGetter(BeakerData::solutionContainer),
            Codec.BOOL.optionalFieldOf("fitsInDispenser", true).forGetter(BeakerData::fitsInDispenser),
            ExaminableSolution.CODEC.optionalFieldOf("examinableSolution", new ExaminableSolution(false, "beaker", false, false, true)).forGetter(BeakerData::examinableSolution),
            DrainableSolutionPolicy.CODEC.optionalFieldOf("drainableSolution", DrainableSolutionPolicy.DISABLED).forGetter(BeakerData::drainableSolution),
            SolutionVisuals.CODEC.optionalFieldOf("solutionVisuals").forGetter(d -> java.util.Optional.ofNullable(d.solutionVisuals)),
            RefillableSolutionPolicy.CODEC.optionalFieldOf("refillableSolution", RefillableSolutionPolicy.DISABLED).forGetter(BeakerData::refillableSolution),
            SolutionTransferPolicy.CODEC.optionalFieldOf("solutionTransfer", SolutionTransferPolicy.DISABLED).forGetter(BeakerData::solutionTransfer),
            DrinkSolutionPolicy.CODEC.optionalFieldOf("drinkSolution", DrinkSolutionPolicy.DISABLED).forGetter(BeakerData::drinkSolution)
    ).apply(i, (container, fits, examine, drainable, visuals, refill, transfer, drink) -> new BeakerData(container, fits, examine, drainable, visuals.orElse(null), refill, transfer, drink)));
    public static final Codec<BeakerData> CODEC = STRUCTURAL;

    public BeakerData {
        if (solutionContainer == null || examinableSolution == null || drainableSolution == null || refillableSolution == null || solutionTransfer == null || drinkSolution == null) throw new IllegalArgumentException("missing beaker composition");
    }
    public BeakerData(SolutionContainer solutionContainer, boolean fitsInDispenser, ExaminableSolution examine) {
        this(solutionContainer, fitsInDispenser, examine, new DrainableSolutionPolicy(true, "beaker"), null, RefillableSolutionPolicy.DISABLED, SolutionTransferPolicy.DISABLED, DrinkSolutionPolicy.DISABLED);
    }
    public BeakerData(SolutionContainer solutionContainer, boolean fitsInDispenser, ExaminableSolution examine,
                      DrainableSolutionPolicy drainableSolution) {
        this(solutionContainer, fitsInDispenser, examine, drainableSolution, null, RefillableSolutionPolicy.DISABLED, SolutionTransferPolicy.DISABLED, DrinkSolutionPolicy.DISABLED);
    }
    public BeakerData(SolutionContainer solutionContainer, boolean fitsInDispenser, ExaminableSolution examine,
                      DrainableSolutionPolicy drainableSolution, SolutionVisuals visuals) {
        this(solutionContainer, fitsInDispenser, examine, drainableSolution, visuals, RefillableSolutionPolicy.DISABLED, SolutionTransferPolicy.DISABLED, DrinkSolutionPolicy.DISABLED);
    }

}

