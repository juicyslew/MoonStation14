package com.juicyslew.moonstation14.ms14.hands;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable, bounded domain snapshot of named hands and their opaque item identities. */
public final class HandState {
    public static final int MAX_HANDS = 16;
    public static final int MAX_ID_LENGTH = 64;

    private final Map<String, Optional<ItemToken>> hands;
    private final String activeHand;

    private HandState(Map<String, Optional<ItemToken>> hands, String activeHand) {
        this.hands = Collections.unmodifiableMap(new LinkedHashMap<>(hands));
        this.activeHand = activeHand;
    }

    /** Creates an ordered set of empty hands. The first ID is active unless explicitly named. */
    public static HandState create(List<String> handIds) {
        if (handIds == null || handIds.isEmpty()) {
            throw new IllegalArgumentException("At least one hand ID is required");
        }
        if (handIds.size() > MAX_HANDS) {
            throw new IllegalArgumentException("Hand count exceeds maximum of " + MAX_HANDS);
        }

        LinkedHashMap<String, Optional<ItemToken>> hands = new LinkedHashMap<>();
        for (String handId : handIds) {
            validateHandId(handId);
            if (hands.putIfAbsent(handId, Optional.empty()) != null) {
                throw new IllegalArgumentException("Duplicate hand ID: " + handId);
            }
        }
        return new HandState(hands, handIds.getFirst());
    }

    public static HandState create(List<String> handIds, String activeHand) {
        HandState state = create(handIds);
        if (!state.hands.containsKey(activeHand)) {
            throw new IllegalArgumentException("Active hand is not in the hand set: " + activeHand);
        }
        return new HandState(state.hands, activeHand);
    }

    private static void validateHandId(String handId) {
        if (handId == null || handId.isBlank() || handId.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("Hand IDs must be nonblank and at most " + MAX_ID_LENGTH + " characters");
        }
    }

    public List<String> handIds() {
        return List.copyOf(hands.keySet());
    }

    /** Immutable deterministic snapshot in configured hand order. */
    public Map<String, Optional<ItemToken>> snapshot() {
        return hands;
    }

    public String activeHand() {
        return activeHand;
    }

    public Optional<ItemToken> occupant(String handId) {
        return Optional.ofNullable(hands.get(handId)).orElseThrow(
                () -> new IllegalArgumentException("Unknown hand: " + handId));
    }

    public HandOperation selectActive(String handId) {
        if (!hands.containsKey(handId)) {
            return HandOperation.rejected(this, HandOperation.Rejection.UNKNOWN_HAND);
        }
        if (activeHand.equals(handId)) {
            return HandOperation.rejected(this, HandOperation.Rejection.ALREADY_ACTIVE);
        }
        return HandOperation.success(new HandState(hands, handId));
    }

    public HandOperation place(String handId, ItemToken token) {
        Objects.requireNonNull(token, "token");
        if (!hands.containsKey(handId)) {
            return HandOperation.rejected(this, HandOperation.Rejection.UNKNOWN_HAND);
        }
        if (hands.values().stream().anyMatch(item -> item.filter(token::equals).isPresent())) {
            return HandOperation.rejected(this, HandOperation.Rejection.DUPLICATE_ITEM);
        }
        if (hands.get(handId).isPresent()) {
            return HandOperation.rejected(this, HandOperation.Rejection.HAND_NOT_EMPTY);
        }
        LinkedHashMap<String, Optional<ItemToken>> next = new LinkedHashMap<>(hands);
        next.put(handId, Optional.of(token));
        return HandOperation.success(new HandState(next, activeHand));
    }

    public HandOperation remove(String handId) {
        if (!hands.containsKey(handId)) {
            return HandOperation.rejected(this, HandOperation.Rejection.UNKNOWN_HAND);
        }
        Optional<ItemToken> occupant = hands.get(handId);
        if (occupant.isEmpty()) {
            return HandOperation.rejected(this, HandOperation.Rejection.HAND_EMPTY);
        }
        LinkedHashMap<String, Optional<ItemToken>> next = new LinkedHashMap<>(hands);
        next.put(handId, Optional.empty());
        return HandOperation.removed(new HandState(next, activeHand), occupant.orElseThrow());
    }

    /** Moves one occupant to an empty different hand; never overwrites a target. */
    public HandOperation move(String sourceHand, String targetHand) {
        if (!hands.containsKey(sourceHand) || !hands.containsKey(targetHand)) {
            return HandOperation.rejected(this, HandOperation.Rejection.UNKNOWN_HAND);
        }
        if (sourceHand.equals(targetHand)) {
            return HandOperation.rejected(this, HandOperation.Rejection.SAME_HAND);
        }
        Optional<ItemToken> source = hands.get(sourceHand);
        if (source.isEmpty()) {
            return HandOperation.rejected(this, HandOperation.Rejection.HAND_EMPTY);
        }
        if (hands.get(targetHand).isPresent()) {
            return HandOperation.rejected(this, HandOperation.Rejection.TARGET_NOT_EMPTY);
        }
        LinkedHashMap<String, Optional<ItemToken>> next = new LinkedHashMap<>(hands);
        next.put(sourceHand, Optional.empty());
        next.put(targetHand, source);
        return HandOperation.success(new HandState(next, activeHand));
    }

    /** Atomically swaps two occupied hands. Empty/occupied combinations are not implicit moves. */
    public HandOperation swap(String firstHand, String secondHand) {
        if (!hands.containsKey(firstHand) || !hands.containsKey(secondHand)) {
            return HandOperation.rejected(this, HandOperation.Rejection.UNKNOWN_HAND);
        }
        if (firstHand.equals(secondHand)) {
            return HandOperation.rejected(this, HandOperation.Rejection.SAME_HAND);
        }
        Optional<ItemToken> first = hands.get(firstHand);
        Optional<ItemToken> second = hands.get(secondHand);
        if (first.isEmpty() && second.isEmpty()) {
            return HandOperation.rejected(this, HandOperation.Rejection.BOTH_HANDS_EMPTY);
        }
        if (first.isEmpty() || second.isEmpty()) {
            return HandOperation.rejected(this, HandOperation.Rejection.TARGET_NOT_EMPTY);
        }
        LinkedHashMap<String, Optional<ItemToken>> next = new LinkedHashMap<>(hands);
        next.put(firstHand, second);
        next.put(secondHand, first);
        return HandOperation.success(new HandState(next, activeHand));
    }
}
