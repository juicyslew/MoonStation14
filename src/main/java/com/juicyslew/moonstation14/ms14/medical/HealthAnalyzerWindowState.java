package com.juicyslew.moonstation14.ms14.medical;

import java.util.Optional;
import java.util.UUID;

/** Actor-qualified server state for one persistent analyzer workspace. */
public record HealthAnalyzerWindowState(UUID scannerId, long epoch, UUID actorId, long frame,
                                        boolean open, Optional<HealthAnalyzerSnapshot> readings) {
    public HealthAnalyzerWindowState {
        if (scannerId == null || actorId == null || epoch < 0 || frame < 0 || readings == null
                || readings.filter(r -> !r.scannerId().equals(scannerId) || r.epoch() != epoch
                || !r.actorId().equals(actorId) || r.frame() != frame).isPresent())
            throw new IllegalArgumentException("invalid analyzer window state");
        if (!open && readings.isPresent()) throw new IllegalArgumentException("closed window cannot carry readings");
    }
}
