package com.juicyslew.moonstation14.ms14.status_effect;

import java.util.List;
import java.util.Objects;

/** Result of clearing every status in one attachment. */
public record StatusEffectClearReport(
        List<StatusEffectClearChange> changes,
        boolean synchronizationRequested
) {
    public StatusEffectClearReport {
        changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
    }
}
