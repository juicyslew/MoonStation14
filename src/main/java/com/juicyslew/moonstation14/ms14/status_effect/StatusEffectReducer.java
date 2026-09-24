package com.juicyslew.moonstation14.ms14.status_effect;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Pure implementation of status-effect duration operations.
 *
 * <p>An empty incoming duration means permanent. Delay is used when creating
 * an instance and when reconciling pending instances; an active instance is
 * never made pending again.</p>
 */
public final class StatusEffectReducer {
    private StatusEffectReducer() {
    }

    /**
     * Applies one operation without mutating either the current instance or
     * any caller-owned value.
     *
     * @param operation operation to apply
     * @param current current instance, or empty when absent
     * @param incomingDuration finite duration, or empty for permanent
     * @param delayTicks nonnegative application delay
     * @return reduction result and optional next instance
     * @throws IllegalArgumentException for a negative delay or nonpositive
     *                                  finite duration
     * @throws ArithmeticException when ADD overflows an integer duration
     */
    public static StatusEffectReduction reduce(
            StatusEffectOperation operation,
            Optional<StatusEffectInstance> current,
            OptionalInt incomingDuration,
            int delayTicks
    ) {
        return reduce(operation, current, incomingDuration, delayTicks, StatusEffectPayload.none());
    }

    /**
     * Applies an operation with an optional typed payload.  {@link
     * StatusEffectPayload.None} means that the caller supplied no
     * application-specific data; it never erases an existing payload.
     */
    public static StatusEffectReduction reduce(
            StatusEffectOperation operation,
            Optional<StatusEffectInstance> current,
            OptionalInt incomingDuration,
        int delayTicks,
             StatusEffectPayload incomingPayload
    ) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(incomingDuration, "incomingDuration");
        Objects.requireNonNull(incomingPayload, "incomingPayload");
        validateInput(incomingDuration, delayTicks);

        Optional<StatusEffectInstance> next = switch (operation) {
            case UPDATE -> update(current, incomingDuration, delayTicks, incomingPayload);
            case ADD -> add(current, incomingDuration, delayTicks, incomingPayload);
            case REMOVE -> remove(current, incomingDuration, incomingPayload);
            case SET -> set(current, incomingDuration, delayTicks, incomingPayload);
        };
        return resultFor(current, next);
    }

    private static Optional<StatusEffectInstance> update(
            Optional<StatusEffectInstance> current,
            OptionalInt incomingDuration,
            int delayTicks,
            StatusEffectPayload incomingPayload
    ) {
        if (current.isEmpty()) {
            return Optional.of(create(delayTicks, incomingDuration, incomingPayload));
        }

        StatusEffectInstance existing = current.get();
        OptionalInt duration = maximumDuration(existing.remainingDurationTicks(), incomingDuration);
        return Optional.of(rebuild(earliestDelay(existing, delayTicks), duration,
                mergedPayload(existing.payload(), incomingPayload)));
    }

    private static Optional<StatusEffectInstance> add(
            Optional<StatusEffectInstance> current,
            OptionalInt incomingDuration,
            int delayTicks,
            StatusEffectPayload incomingPayload
    ) {
        if (current.isEmpty()) {
            return Optional.of(create(delayTicks, incomingDuration, incomingPayload));
        }

        StatusEffectInstance existing = current.get();
        OptionalInt duration;
        if (existing.isPermanent() || incomingDuration.isEmpty()) {
            duration = OptionalInt.empty();
        } else {
            duration = OptionalInt.of(Math.addExact(
                    existing.remainingDurationTicks().getAsInt(),
                    incomingDuration.getAsInt()
            ));
        }
        return Optional.of(rebuild(earliestDelay(existing, delayTicks), duration,
                mergedPayload(existing.payload(), incomingPayload)));
    }

    private static Optional<StatusEffectInstance> remove(
            Optional<StatusEffectInstance> current,
            OptionalInt incomingDuration,
            StatusEffectPayload incomingPayload
    ) {
        if (current.isEmpty()) {
            return Optional.empty();
        }

        StatusEffectInstance existing = current.get();
        if (incomingDuration.isEmpty() || existing.isPermanent()) {
            // REMOVE always removes a permanent status.  A finite incoming
            // duration is still meaningful for finite statuses, but it must
            // not turn permanent removal into a no-op.
            return Optional.empty();
        }

        int remaining = existing.remainingDurationTicks().getAsInt() - incomingDuration.getAsInt();
        if (remaining <= 0) {
            return Optional.empty();
        }
        return Optional.of(StatusEffectInstance.finite(existing.delayTicks(), remaining,
                durationOnlyPayload(existing.payload(), incomingPayload)));
    }

    private static Optional<StatusEffectInstance> set(
            Optional<StatusEffectInstance> current,
            OptionalInt incomingDuration,
            int delayTicks,
            StatusEffectPayload incomingPayload
    ) {
        if (current.isEmpty()) {
            return Optional.of(create(delayTicks, incomingDuration,
                    durationOnlyPayload(StatusEffectPayload.none(), incomingPayload)));
        }

        StatusEffectInstance existing = current.get();
        // SET changes only duration/timing for movement payloads.  Other
        // payload families retain their established reducer semantics.
        return Optional.of(rebuild(earliestDelay(existing, delayTicks), incomingDuration,
                durationOnlyPayload(existing.payload(), incomingPayload)));
    }

    private static StatusEffectInstance create(int delayTicks, OptionalInt duration,
                                               StatusEffectPayload payload) {
        return rebuild(delayTicks, duration, payload);
    }

    private static StatusEffectInstance rebuild(int delayTicks, OptionalInt duration,
                                                StatusEffectPayload payload) {
        return duration.isPresent()
                ? StatusEffectInstance.finite(delayTicks, duration.getAsInt(), payload)
                : StatusEffectInstance.permanent(delayTicks, payload);
    }

    private static StatusEffectPayload mergedPayload(StatusEffectPayload existing,
                                                     StatusEffectPayload incoming) {
        if (incoming.isNone()) {
            return existing;
        }
        if (existing instanceof StatusEffectPayload.Jitter currentJitter
                && incoming instanceof StatusEffectPayload.Jitter incomingJitter) {
            return new StatusEffectPayload.Jitter(
                    Math.max(currentJitter.amplitude(), incomingJitter.amplitude()),
                    Math.max(currentJitter.frequency(), incomingJitter.frequency()));
        }
        return incoming;
    }

    private static StatusEffectPayload durationOnlyPayload(StatusEffectPayload existing,
                                                           StatusEffectPayload incoming) {
        return incoming instanceof StatusEffectPayload.MovementSpeedModifier
                ? existing : mergedPayload(existing, incoming);
    }

    private static int earliestDelay(StatusEffectInstance existing, int incomingDelay) {
        return existing.isActive() ? 0 : Math.min(existing.delayTicks(), incomingDelay);
    }

    private static OptionalInt maximumDuration(OptionalInt current, OptionalInt incoming) {
        if (current.isEmpty() || incoming.isEmpty()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Math.max(current.getAsInt(), incoming.getAsInt()));
    }

    private static void validateInput(OptionalInt incomingDuration, int delayTicks) {
        if (delayTicks < 0) {
            throw new IllegalArgumentException("delayTicks must be nonnegative");
        }
        if (incomingDuration.isPresent() && incomingDuration.getAsInt() <= 0) {
            throw new IllegalArgumentException("finite duration must be positive");
        }
    }

    private static StatusEffectReduction resultFor(
            Optional<StatusEffectInstance> current,
            Optional<StatusEffectInstance> next
    ) {
        StatusEffectChangeKind kind;
        if (current.isEmpty()) {
            kind = next.isEmpty() ? StatusEffectChangeKind.UNCHANGED : StatusEffectChangeKind.CREATED;
        } else if (next.isEmpty()) {
            kind = StatusEffectChangeKind.REMOVED;
        } else {
            kind = current.get().equals(next.get())
                    ? StatusEffectChangeKind.UNCHANGED
                    : StatusEffectChangeKind.CHANGED;
        }
        return new StatusEffectReduction(kind, next);
    }
}
