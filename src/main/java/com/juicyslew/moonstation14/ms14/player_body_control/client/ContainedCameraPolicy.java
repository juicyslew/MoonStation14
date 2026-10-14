package com.juicyslew.moonstation14.ms14.player_body_control.client;

import net.minecraft.client.CameraType;

import java.util.Objects;
import java.util.UUID;

/** Presentation-only perspective lease keyed to one authoritative body and mind epoch. */
public final class ContainedCameraPolicy {
    private UUID body;
    private long epoch;
    private CameraType previous;

    public boolean active() { return body != null; }

    public CameraType reconcile(UUID nextBody, long nextEpoch, boolean contained, CameraType current) {
        Objects.requireNonNull(current);
        boolean sameLease = nextBody != null && nextEpoch > 0 && body != null
                && body.equals(nextBody) && epoch == nextEpoch;
        if (!contained || nextBody == null || nextEpoch <= 0) {
            if (body == null) return current;
            CameraType restore = previous;
            clear();
            return restore;
        }
        if (!sameLease) {
            CameraType baseline = body != null && current == CameraType.THIRD_PERSON_BACK ? previous : current;
            body = nextBody;
            epoch = nextEpoch;
            previous = baseline;
        }
        return CameraType.THIRD_PERSON_BACK;
    }

    /** The saved perspective belongs to the lease, not an individual owner view transfer. */
    public void clear() { body = null; epoch = 0; previous = null; }

    public boolean matches(UUID cameraBody, UUID ownedBody, long ownedEpoch, boolean contained) {
        return contained && body != null && body.equals(cameraBody) && body.equals(ownedBody)
                && epoch == ownedEpoch && epoch > 0;
    }
}

