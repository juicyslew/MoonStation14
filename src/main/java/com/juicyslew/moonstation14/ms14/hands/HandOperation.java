package com.juicyslew.moonstation14.ms14.hands;

import java.util.Objects;
import java.util.Optional;

/** Result of a pure hand-state transition. A rejected transition retains its input state. */
public record HandOperation(HandState state, Rejection rejection, Optional<ItemToken> removedItem) {
    public enum Rejection {
        NONE,
        UNKNOWN_HAND,
        ALREADY_ACTIVE,
        HAND_NOT_EMPTY,
        HAND_EMPTY,
        DUPLICATE_ITEM,
        SAME_HAND,
        TARGET_NOT_EMPTY,
        BOTH_HANDS_EMPTY
    }

    public HandOperation {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(rejection, "rejection");
        removedItem = Objects.requireNonNull(removedItem, "removedItem");
        if (rejection != Rejection.NONE && removedItem.isPresent()) {
            throw new IllegalArgumentException("Rejected operation cannot remove an item");
        }
    }

    public boolean succeeded() {
        return rejection == Rejection.NONE;
    }

    static HandOperation success(HandState state) {
        return new HandOperation(state, Rejection.NONE, Optional.empty());
    }

    static HandOperation removed(HandState state, ItemToken token) {
        return new HandOperation(state, Rejection.NONE, Optional.of(token));
    }

    static HandOperation rejected(HandState original, Rejection rejection) {
        return new HandOperation(original, rejection, Optional.empty());
    }
}
