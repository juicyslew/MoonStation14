package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable durable store metadata and its complete profile set. */
public record LifecycleStoreEnvelope(int schemaVersion, long storeRevision, UUID epochToken,
                                     long generationCounter, List<SavedLifecycleProfile> profiles) {
    public static final int CURRENT_SCHEMA = 1;

    public LifecycleStoreEnvelope {
        if (schemaVersion != CURRENT_SCHEMA) throw new IllegalArgumentException("unsupported store schema version");
        if (storeRevision < 0 || generationCounter < 0) throw new IllegalArgumentException("negative store metadata");
        Objects.requireNonNull(epochToken, "epochToken");
        profiles = List.copyOf(Objects.requireNonNull(profiles, "profiles"));
        LifecycleProfileCodec.validateStoreProfiles(epochToken, generationCounter, profiles);
    }
}
