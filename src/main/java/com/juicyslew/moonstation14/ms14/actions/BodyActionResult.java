package com.juicyslew.moonstation14.ms14.actions;

import java.util.Optional;

/** Correlated acknowledgement; it does not itself grant action authority. */
public record BodyActionResult(Status status, String reason, long sequence, Optional<BodyActionSnapshot> snapshot) {
    public enum Status { ACCEPTED, DENIED }
    public BodyActionResult {
        if (status == null || reason == null || sequence <= 0 || snapshot == null
                || status == Status.ACCEPTED && !reason.isEmpty())
            throw new IllegalArgumentException("Invalid body action result");
    }
}
