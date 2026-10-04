package com.juicyslew.moonstation14.ms14.loose_objects.interaction;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import net.minecraft.resources.ResourceKey;

import java.util.Map;

/** Shared bounded arithmetic for liquid interactions. Plans are snapshots only; callers publish them. */
public final class LiquidInteractionCapability {
    public static final long DOSE_CENTS = 500;
    public static final long STOMACH_CAPACITY_CENTS = 5_000;

    private LiquidInteractionCapability() { }

    public record TransferPlan(Map<ResourceKey<ReagentData>, Long> sourceBefore,
                               Map<ResourceKey<ReagentData>, Long> sourceAfter,
                               Map<ResourceKey<ReagentData>, Long> destinationBefore,
                               Map<ResourceKey<ReagentData>, Long> destinationAfter,
                               long transferredCents) {
        public TransferPlan {
            sourceBefore = Map.copyOf(sourceBefore);
            sourceAfter = Map.copyOf(sourceAfter);
            destinationBefore = Map.copyOf(destinationBefore);
            destinationAfter = Map.copyOf(destinationAfter);
            if (transferredCents < 0 || transferredCents > DOSE_CENTS) throw new IllegalArgumentException("invalid dose");
        }
    }

    /** Stage up to five units from source into destination without mutating either holder. */
    public static TransferPlan plan(Map<ResourceKey<ReagentData>, Long> source,
                                    Map<ResourceKey<ReagentData>, Long> destination, long capacityCents) {
        return plan(source, destination, capacityCents, DOSE_CENTS);
    }

    public static TransferPlan plan(Map<ResourceKey<ReagentData>, Long> source,
                                    Map<ResourceKey<ReagentData>, Long> destination, long capacityCents, long doseCents) {
        ReagentUnits.validateCents(doseCents, "doseCents");
        Map<ResourceKey<ReagentData>, Long> sourceBefore = Map.copyOf(source);
        Map<ResourceKey<ReagentData>, Long> destinationBefore = Map.copyOf(destination);
        long available = ReagentUnits.total(sourceBefore.values());
        long occupied = ReagentUnits.total(destinationBefore.values());
        long accepted = ReagentUnits.admit(doseCents, Math.min(available, Math.max(0, capacityCents - occupied)));
        Map<ResourceKey<ReagentData>, Long> removed = ReagentUnits.split(sourceBefore, accepted);
        java.util.Map<ResourceKey<ReagentData>, Long> nextSource = new java.util.LinkedHashMap<>(sourceBefore);
        java.util.Map<ResourceKey<ReagentData>, Long> nextDestination = new java.util.LinkedHashMap<>(destinationBefore);
        removed.forEach((key, amount) -> {
            long left = nextSource.getOrDefault(key, 0L) - amount;
            if (left == 0) nextSource.remove(key); else nextSource.put(key, left);
            if (amount > 0) nextDestination.merge(key, amount, Math::addExact);
        });
        return new TransferPlan(sourceBefore, nextSource, destinationBefore, nextDestination, accepted);
    }
}
