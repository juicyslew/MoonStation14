package com.juicyslew.moonstation14.ms14.doors.client;

import com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Dispatch/lifecycle index for overlays owned by the currently committed local CHARACTER. */
public final class ClientAirlockPredictionSystem {
    private OwnerKey owner;
    private final Set<AirlockBlockEntity> active = new HashSet<>();
    private long lastTick = Long.MIN_VALUE;

    /** Exact committed-body lease; tested without constructing a Minecraft client world. */
    public record OwnerKey(long epoch, int bodyId, UUID bodyUuid) {
        public boolean isCurrent(long currentEpoch, int currentBodyId, UUID currentBodyUuid) {
            return epoch > 0 && bodyUuid != null && epoch == currentEpoch
                    && bodyId == currentBodyId && bodyUuid.equals(currentBodyUuid);
        }
    }

    public static boolean sameOwner(OwnerKey owner, long epoch, int bodyId, UUID bodyUuid) {
        return owner != null && owner.isCurrent(epoch, bodyId, bodyUuid);
    }

    public void activate(AirlockBlockEntity door) {
        if (door != null && door.hasClientPrediction()) active.add(door);
    }

    public void tick(long ownerEpoch, int ownerBodyId, UUID ownerBody, long gameTime) {
        if (ownerEpoch <= 0 || ownerBodyId < 0 || ownerBody == null
                || !sameOwner(owner, ownerEpoch, ownerBodyId, ownerBody)) {
            clear();
            if (ownerEpoch <= 0 || ownerBodyId < 0 || ownerBody == null) return;
            owner = new OwnerKey(ownerEpoch, ownerBodyId, ownerBody);
        }
        if (lastTick == gameTime) return;
        lastTick = gameTime;
        active.removeIf(door -> {
            if (door == null || door.isRemoved() || !door.hasClientPrediction()) return true;
            door.tickClientPrediction(gameTime);
            return !door.hasClientPrediction();
        });
    }

    public void clear() {
        for (AirlockBlockEntity door : active) if (door != null) door.clearClientPrediction();
        active.clear();
        owner = null;
        lastTick = Long.MIN_VALUE;
    }

    int activeCount() { return active.size(); }
}
