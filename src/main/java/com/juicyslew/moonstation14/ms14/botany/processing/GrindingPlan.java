package com.juicyslew.moonstation14.ms14.botany.processing;

import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Pure staged whole-source transfer plan; caller owns committing item removals and target publication. */
public final class GrindingPlan {
    public record Source(ReagentAttachment solution, boolean grindable) {
        public Source { Objects.requireNonNull(solution); }
    }
    public record Result(ReagentAttachment beaker, List<Integer> consumedSourceIndexes) {
        public Result { beaker = new ReagentAttachment(beaker.toComponent()); consumedSourceIndexes = List.copyOf(consumedSourceIndexes); }
        @Override public ReagentAttachment beaker() { return new ReagentAttachment(beaker.toComponent()); }
    }
    private GrindingPlan() { }

    /** Reactions are evaluated after each accepted source, so subsequent whole sources see real remaining capacity. */
    public static Result prepare(List<Source> sources, ReagentAttachment beaker, long capacityCents,
                                 Consumer<ReagentAttachment> stagedReactions) {
        Objects.requireNonNull(sources); Objects.requireNonNull(beaker); Objects.requireNonNull(stagedReactions);
        ReagentAttachment target = new ReagentAttachment(beaker.toComponent());
        List<Integer> consumed = new ArrayList<>();
        for (int index = 0; index < sources.size(); index++) {
            Source source = Objects.requireNonNull(sources.get(index));
            long amount = source.solution().totalUnits();
            if (!source.grindable() || amount <= 0 || amount > capacityCents - target.totalUnits()) continue;
            ReagentAttachment candidate = new ReagentAttachment(target.toComponent());
            ReagentAttachment stagedSource = new ReagentAttachment(source.solution().toComponent());
            long moved = ReagentAttachment.transferUnits(stagedSource, candidate, amount, capacityCents);
            if (moved != amount) throw new IllegalStateException("whole-source preflight mismatch");
            // Reactions run against this source's isolated candidate. A local reaction
            // that grows beyond the exact container limit rejects only this source.
            stagedReactions.accept(candidate);
            if (candidate.totalUnits() > capacityCents) continue;
            target = candidate;
            consumed.add(index);
        }
        return new Result(target, consumed);
    }
}
