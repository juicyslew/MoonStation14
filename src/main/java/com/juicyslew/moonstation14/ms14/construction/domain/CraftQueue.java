package com.juicyslew.moonstation14.ms14.construction.domain;

import java.util.Objects;

/** Bounded, non-reserving queue. A successful completion permits exactly one item attempt. */
public final class CraftQueue {
    public static final int MAX_QUANTITY = 64;
    private final String recipe;
    private int remaining;
    private boolean stopped;

    public CraftQueue(String recipe, int quantity) {
        this.recipe = Objects.requireNonNull(recipe);
        if (recipe.isBlank() || quantity < 1 || quantity > MAX_QUANTITY)
            throw new IllegalArgumentException("invalid craft queue request");
        remaining = quantity;
    }

    public String recipe() { return recipe; }
    public int remaining() { return remaining; }
    public boolean active() { return !stopped && remaining > 0; }

    /** Call only after one atomic item transaction succeeds. */
    public void completedOne() {
        if (!active()) throw new IllegalStateException("queue is not active");
        remaining--;
    }

    /** Any failed validation/transaction stops the queue without reserving future materials. */
    public void stop() { stopped = true; }
}
