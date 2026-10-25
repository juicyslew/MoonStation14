package com.juicyslew.moonstation14.ms14.botany.processing;

import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Pure staged source transfer plan; caller owns committing item removals and target publication. */
public final class GrindingPlan {
    public record Source(ReagentAttachment solution, boolean grindable, int count, boolean stack) {
        public Source { Objects.requireNonNull(solution); if (count < 1) throw new IllegalArgumentException("count must be positive"); }
        public Source(ReagentAttachment solution, boolean grindable) { this(solution, grindable, 1, false); }
    }
    public record Result(ReagentAttachment beaker, List<Integer> consumedSourceIndexes, List<Integer> consumedCounts) {
        public Result { beaker = new ReagentAttachment(beaker.toComponent()); consumedSourceIndexes = List.copyOf(consumedSourceIndexes); consumedCounts = List.copyOf(consumedCounts); }
        @Override public ReagentAttachment beaker() { return new ReagentAttachment(beaker.toComponent()); }
        public int consumedCount(int index) { int at = consumedSourceIndexes.indexOf(index); return at < 0 ? 0 : consumedCounts.get(at); }
    }
    private GrindingPlan() { }

    /** Reactions are evaluated after each accepted source, so subsequent whole sources see real remaining capacity. */
    public static Result prepare(List<Source> sources, ReagentAttachment beaker, long capacityCents,
                                 Consumer<ReagentAttachment> stagedReactions) {
        Objects.requireNonNull(sources); Objects.requireNonNull(beaker); Objects.requireNonNull(stagedReactions);
        ReagentAttachment target = new ReagentAttachment(beaker.toComponent());
        List<Integer> consumed = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (int index = 0; index < sources.size(); index++) {
            Source source = Objects.requireNonNull(sources.get(index));
            if (source.stack() && source.solution().totalUnits() == 0) continue;
            long perItem = source.solution().totalUnits();
            long amount = perItem;
            int fits = source.count();
            if (source.stack()) {
                long total = Math.multiplyExact(perItem, source.count());
                long available = capacityCents - target.totalUnits();
                long ratio = Math.min(100, available * 100 / total + 1);
                fits = (int) ((long) source.count() * ratio / 100);
                if (fits <= 0) continue;
                amount = Math.multiplyExact(perItem, fits);
            }
            if (!source.grindable() || !source.stack() && amount > capacityCents - target.totalUnits()) continue;
            // An eligible, present solution may legitimately be empty. It still represents
            // a whole source accepted by the grinder and must be consumed.
            if (amount == 0) { consumed.add(index); counts.add(fits); continue; }
            ReagentAttachment candidate = new ReagentAttachment(target.toComponent());
            ReagentAttachment stagedSource = new ReagentAttachment(source.solution().toComponent());
            if (source.stack()) stagedSource.scale((float) fits);
            long moved = ReagentAttachment.transferUnits(stagedSource, candidate, amount, capacityCents);
            if (source.stack() && moved != amount) {
                // SS14 decrements the selected stack units before TryAddSolution; a
                // rejected over-capacity addition consumes them but publishes nothing.
                consumed.add(index); counts.add(fits); continue;
            }
            if (!source.stack() && moved != amount) throw new IllegalStateException("whole-source preflight mismatch");
            // Reactions run against this source's isolated candidate. A local reaction
            // that grows beyond the exact container limit rejects only this source.
            stagedReactions.accept(candidate);
            if (candidate.totalUnits() > capacityCents) continue;
            target = candidate;
            consumed.add(index); counts.add(fits);
        }
        return new Result(target, consumed, counts);
    }
}
