package com.juicyslew.moonstation14.ms14.metabolism;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * The ordered metabolism stages and per-stage work budget for one metabolizer.
 *
 * <p>This is deliberately only a scheduling description.  It does not model
 * organs, anatomy, or a particular kind of creature.</p>
 */
public record MetabolizerProfile(List<MetabolismStage> stages, int perStageProcessCap) {
    public static final int SHARED_LIVING_PROCESS_CAP = 2;
    public static final List<MetabolismStage> SHARED_LIVING_STAGE_ORDER = List.of(
            MetabolismStage.RESPIRATION,
            MetabolismStage.DIGESTION,
            MetabolismStage.BLOODSTREAM,
            MetabolismStage.METABOLITES
    );
    public static final MetabolizerProfile SHARED_LIVING =
            new MetabolizerProfile(SHARED_LIVING_STAGE_ORDER, SHARED_LIVING_PROCESS_CAP);
    public static final MetabolizerProfile SHARED_BODY = new MetabolizerProfile(
            List.of(MetabolismStage.RESPIRATION, MetabolismStage.BLOODSTREAM, MetabolismStage.METABOLITES),
            SHARED_LIVING_PROCESS_CAP);
    public static final MetabolizerProfile STOMACH = new MetabolizerProfile(
            List.of(MetabolismStage.DIGESTION), SHARED_LIVING_PROCESS_CAP);

    public MetabolizerProfile {
        Objects.requireNonNull(stages, "stages");
        stages = List.copyOf(stages);
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("stages must not be empty");
        }
        if (stages.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("stage");
        }
        if (new HashSet<>(stages).size() != stages.size()) {
            throw new IllegalArgumentException("stages must not contain duplicates");
        }
        if (perStageProcessCap <= 0) {
            throw new IllegalArgumentException("perStageProcessCap must be strictly positive");
        }
    }

    public static MetabolizerProfile sharedLiving() {
        return SHARED_LIVING;
    }

    public int processCap() {
        return perStageProcessCap;
    }
}
