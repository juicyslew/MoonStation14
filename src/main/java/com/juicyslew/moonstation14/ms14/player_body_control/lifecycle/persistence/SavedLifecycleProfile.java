package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.TreeMap;

/** Immutable schema-v1 durable identity/recovery description. This is not a world-save. */
public record SavedLifecycleProfile(int schemaVersion, UUID accountId, String profileKey, UUID mindId,
                                    UUID bodyId, String dimension, Location location,
                                    Map<String, String> appearance, UUID connectionEpoch,
                                    long connectionGeneration, State state, long revision,
                                    Long offlineSinceMillis) {
    public static final int CURRENT_SCHEMA = 1;

    public SavedLifecycleProfile {
        if (schemaVersion != CURRENT_SCHEMA) throw new IllegalArgumentException("unsupported schema version");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(mindId, "mindId");
        Objects.requireNonNull(bodyId, "bodyId");
        Objects.requireNonNull(connectionEpoch, "connectionEpoch");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(state, "state");
        profileKey = requiredText(profileKey, "profileKey");
        dimension = requiredText(dimension, "dimension");
        if (connectionGeneration < 0 || revision < 0) throw new IllegalArgumentException("negative generation or revision");
        if (offlineSinceMillis != null && offlineSinceMillis < 0) throw new IllegalArgumentException("negative offline timestamp");
        if ((state == State.OFFLINE || state == State.DEAD_CLAIM || state == State.GHOST_OFFLINE)
                != (offlineSinceMillis != null))
            throw new IllegalArgumentException("offline timestamp must match offline/death-claim state");
        Objects.requireNonNull(appearance, "appearance");
        TreeMap<String, String> copy = new TreeMap<>();
        appearance.forEach((key, value) -> copy.put(requiredText(key, "appearance key"),
                requiredText(value, "appearance value")));
        appearance = Map.copyOf(copy);
    }

    private static String requiredText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " is blank");
        return value;
    }

    /** GHOST means the recorded Mind is currently on a ghost body; it is not an offline death claim. */
    /** PREPARING reserves a first-enrollment account/body/Mind claim; it is never an active session. */
    public enum State { PREPARING, ACTIVE, OFFLINE, DEAD_CLAIM, GHOST, GHOST_OFFLINE, RECOVERY_REQUIRED }

    public record Location(double x, double y, double z) {
        public Location {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("non-finite location");
        }
    }
}
