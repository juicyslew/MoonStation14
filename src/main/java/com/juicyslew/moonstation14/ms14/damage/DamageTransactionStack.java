package com.juicyslew.moonstation14.ms14.damage;

import java.util.ArrayDeque;
import java.util.Deque;

/** Identity-matched LIFO transactions for nested vanilla damage callbacks. */
final class DamageTransactionStack<S, T> {
    private final Deque<Entry<S, T>> entries = new ArrayDeque<>();

    void push(S source, T value) {
        entries.push(new Entry<>(source, value));
    }

    T removeMatching(S source) {
        var iterator = entries.iterator();
        while (iterator.hasNext()) {
            Entry<S, T> entry = iterator.next();
            if (entry.source == source) {
                iterator.remove();
                return entry.value;
            }
        }
        return null;
    }

    boolean contains(S source) {
        for (Entry<S, T> entry : entries) {
            if (entry.source == source) {
                return true;
            }
        }
        return false;
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    private record Entry<S, T>(S source, T value) {
    }
}
