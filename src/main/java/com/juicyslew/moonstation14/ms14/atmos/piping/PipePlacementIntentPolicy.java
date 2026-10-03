package com.juicyslew.moonstation14.ms14.atmos.piping;

/** Deterministic routing for authenticated pipe placement hits. */
final class PipePlacementIntentPolicy {
    enum Intent { EXPOSED, CROSSING, SEALED_WALL, DENY }

    private PipePlacementIntentPolicy() { }

    static Intent choose(boolean shiftHint, boolean appliedSneak, boolean eligibleWall,
                         boolean functionalPipeMissingLane, boolean eligibleAdjacentAir) {
        if (shiftHint && appliedSneak && eligibleWall) return Intent.SEALED_WALL;
        if (shiftHint && eligibleWall) return Intent.DENY;
        if (shiftHint && appliedSneak && functionalPipeMissingLane) return Intent.CROSSING;
        if (eligibleAdjacentAir) return Intent.EXPOSED;
        if (functionalPipeMissingLane) return Intent.CROSSING;
        return Intent.DENY;
    }
}
