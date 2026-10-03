package com.juicyslew.moonstation14.ms14.hands.live;

import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/** Package-boundary access for GameTests exercising the trusted pickup seam. */
public final class BodyHandItemTransferGameTestAccess {
    private BodyHandItemTransferGameTestAccess() { }

    public static BodyHandItemTransfer.Result pickupOwned(LivingEntity body, UUID target, String handId,
                                                           long expectedRevision) {
        return BodyHandItemTransfer.pickupOwned(body, target, handId, expectedRevision, () -> true);
    }
}
