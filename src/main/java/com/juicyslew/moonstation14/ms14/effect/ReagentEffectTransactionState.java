package com.juicyslew.moonstation14.ms14.effect;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.minecraft.resources.ResourceKey;

import java.util.Objects;

/** Mutable, invocation-local bookkeeping for virtual pre-removal effect ordering. */
public final class ReagentEffectTransactionState {
    private final ResourceKey<ReagentData> metabolizedReagent;
    private final float ordinaryRemoved;
    private float remainingReservation;

    public ReagentEffectTransactionState(ResourceKey<ReagentData> metabolizedReagent,
                                         float ordinaryRemoved) {
        this.metabolizedReagent = Objects.requireNonNull(metabolizedReagent, "metabolizedReagent");
        if (!Float.isFinite(ordinaryRemoved) || ordinaryRemoved < 0f) {
            throw new IllegalArgumentException("ordinaryRemoved must be finite and nonnegative");
        }
        this.ordinaryRemoved = ordinaryRemoved;
        this.remainingReservation = ordinaryRemoved;
    }

    public ResourceKey<ReagentData> metabolizedReagent() {
        return metabolizedReagent;
    }

    public float ordinaryRemoved() {
        return ordinaryRemoved;
    }

    public float remainingReservation() {
        return remainingReservation;
    }

    /**
     * Previews one positive adjustment without changing either side of the
     * transaction. Same-reagent additions may restore reservation released by
     * an earlier negative adjustment; other reagents cannot do so.
     */
    PositiveAdjustmentPlan previewPositiveAdjustment(ResourceKey<ReagentData> reagent,
                                                     ReagentAttachment solution,
                                                     float capacity, float amount) {
        Objects.requireNonNull(reagent, "reagent");
        Objects.requireNonNull(solution, "solution");
        if (!Float.isFinite(capacity) || capacity < 0f
                || !Float.isFinite(amount) || amount <= 0f
                || !Float.isFinite(remainingReservation) || remainingReservation < 0f
                || remainingReservation > ordinaryRemoved) {
            throw new IllegalArgumentException("positive adjustment state is invalid");
        }

        double currentTotal = 0d;
        for (var entry : solution.getMap().entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null
                    || !Float.isFinite(entry.getValue()) || entry.getValue() < 0f) {
                throw new IllegalArgumentException("solution amounts must be finite and nonnegative");
            }
            currentTotal += entry.getValue();
        }
        if (!Double.isFinite(currentTotal) || currentTotal < 0d || currentTotal > capacity) {
            throw new IllegalArgumentException("solution exceeds finite capacity");
        }

        float reservationToRestore = 0f;
        if (metabolizedReagent.equals(reagent)) {
            float releasedReservation = ordinaryRemoved - remainingReservation;
            double freeForReservation = (double) capacity - currentTotal - remainingReservation;
            if (!Float.isFinite(releasedReservation) || releasedReservation < 0f
                    || !Double.isFinite(freeForReservation)) {
                throw new IllegalArgumentException("released reservation is invalid");
            }
            double maxRestore = Math.min(amount, releasedReservation);
            maxRestore = Math.min(maxRestore, Math.max(0d, freeForReservation));
            if (maxRestore > 0d) {
                reservationToRestore = (float) maxRestore;
                if (!Float.isFinite(reservationToRestore) || reservationToRestore < 0f) {
                    throw new IllegalArgumentException("reservation restoration is invalid");
                }
                float available = ordinaryRemoved - remainingReservation;
                if (reservationToRestore > available) {
                    reservationToRestore = available;
                }
            }
        }

        float resultingReservation = remainingReservation + reservationToRestore;
        float effectiveCapacity = capacity - resultingReservation;
        float physicalAmount = amount - reservationToRestore;
        if (!Float.isFinite(resultingReservation) || resultingReservation < 0f
                || resultingReservation > ordinaryRemoved
                || !Float.isFinite(effectiveCapacity) || effectiveCapacity < 0f
                || !Float.isFinite(physicalAmount) || physicalAmount < 0f
                || currentTotal + resultingReservation > capacity) {
            throw new IllegalArgumentException("positive adjustment would exceed capacity");
        }
        return new PositiveAdjustmentPlan(resultingReservation, physicalAmount, effectiveCapacity);
    }

    void commitPositiveAdjustment(PositiveAdjustmentPlan plan) {
        Objects.requireNonNull(plan, "plan");
        // The caller owns this state on the server thread and commits the
        // plan immediately after ReagentAttachment's transactional add API.
        remainingReservation = plan.resultingReservation();
    }

    /** Consumes reserved quantity only for the currently metabolized reagent. */
    float consumeReservation(ResourceKey<ReagentData> reagent, float amount) {
        Objects.requireNonNull(reagent, "reagent");
        if (!Float.isFinite(amount) || amount < 0f) {
            throw new IllegalArgumentException("amount must be finite and nonnegative");
        }
        if (!metabolizedReagent.equals(reagent) || amount == 0f) {
            return 0f;
        }

        float consumed = Math.min(remainingReservation, amount);
        float remaining = remainingReservation - consumed;
        if (!Float.isFinite(consumed) || consumed < 0f
                || !Float.isFinite(remaining) || remaining < 0f) {
            throw new IllegalStateException("reservation became invalid");
        }
        remainingReservation = remaining;
        return consumed;
    }

    record PositiveAdjustmentPlan(float resultingReservation,
                                  float physicalAmount, float effectiveCapacity) {
    }
}
