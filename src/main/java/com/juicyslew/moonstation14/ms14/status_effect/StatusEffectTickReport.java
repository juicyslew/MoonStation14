package com.juicyslew.moonstation14.ms14.status_effect;

import java.util.List;
import java.util.Objects;

/** Result of server-side advancement, including whether a sync update occurred. */
public record StatusEffectTickReport(
        List<StatusEffectTickChange> changes,
        boolean synchronizationRequested
) {
    public StatusEffectTickReport {
        changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
    }
}
