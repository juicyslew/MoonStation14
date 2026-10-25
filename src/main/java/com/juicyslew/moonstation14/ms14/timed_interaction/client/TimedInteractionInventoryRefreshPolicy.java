package com.juicyslew.moonstation14.ms14.timed_interaction.client;

import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSnapshot;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Pure selector for accepted world-operation terminal changes that may stale owned material display. */
public final class TimedInteractionInventoryRefreshPolicy {
    private TimedInteractionInventoryRefreshPolicy() { }

    public static boolean shouldRefresh(TimedInteractionSnapshot previous, TimedInteractionSnapshot incoming,
            UUID ownedBody, ResourceLocation ownedDimension) {
        if (incoming == null || ownedBody == null || ownedDimension == null
                || !ownedBody.equals(incoming.bodyUuid()) || !ownedDimension.equals(incoming.dimension())) return false;
        if (previous != null && (!previous.bodyUuid().equals(incoming.bodyUuid())
                || !previous.dimension().equals(incoming.dimension()) || incoming.revision() <= previous.revision())) return false;
        return incoming.views().stream().anyMatch(view -> view.worldTarget().isPresent()
                && view.phase() != TimedOperation.Phase.ACTIVE
                && (previous == null || previous.views().stream().noneMatch(old -> old.operationID() == view.operationID()
                        && old.phase() != TimedOperation.Phase.ACTIVE)));
    }
}

