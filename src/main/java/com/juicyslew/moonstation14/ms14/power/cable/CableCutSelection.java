package com.juicyslew.moonstation14.ms14.power.cable;

import java.util.Set;

/** Pure choice of which tier, if any, a cutter may remove from one clicked face. */
public final class CableCutSelection {
    private CableCutSelection() { }

    public enum Refusal { NONE, MISSING, AMBIGUOUS }

    public record Result(CableTier tier, Refusal refusal) {
        public boolean allowed() { return tier != null; }
    }

    public static Result select(Set<CableTier> existingTiers, CableTier selected) {
        if (selected != null) {
            return existingTiers.contains(selected)
                    ? new Result(selected, Refusal.NONE) : new Result(null, Refusal.MISSING);
        }
        if (existingTiers.size() > 1) return new Result(null, Refusal.AMBIGUOUS);
        if (existingTiers.isEmpty()) return new Result(null, Refusal.MISSING);
        return new Result(existingTiers.iterator().next(), Refusal.NONE);
    }
}
