package com.juicyslew.moonstation14.ms14.timed_interaction;

import java.util.List;
import java.util.HashSet;

/** Transient immutable actor metadata; not saved with the actor. */
public record TimedOperationState(List<TimedOperation> operations, long revision, long nextId) {
    public static final TimedOperationState EMPTY = new TimedOperationState(List.of(), 0, 1);
    public TimedOperationState {
        operations = List.copyOf(operations);
        if (revision < 0 || nextId < 1) throw new IllegalArgumentException("invalid state counters");
        HashSet<Long> ids = new HashSet<>();
        for (TimedOperation operation : operations) {
            if (!ids.add(operation.id()) || operation.id() >= nextId) throw new IllegalArgumentException("invalid operation ids");
        }
    }
}
