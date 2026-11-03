package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.entities.ItemAttachment;
import java.util.Optional;
import java.util.UUID;

/** Shared visibility policy for ordinary world-item targeting versus deliberate attachment removal. */
public final class AttachedItemTargeting {
    private AttachedItemTargeting() { }

    public static boolean eligible(UUID bodyId, Optional<ItemAttachment> attachment, boolean explicitRemoval) {
        if (attachment == null || attachment.isEmpty()) return true;
        return bodyId == null || !attachment.get().targetUUID().equals(bodyId);
    }
}
