package com.juicyslew.moonstation14.ms14.stripping.client;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.UUID;

/** Minimal identity captured at nomination, independent of private strip-view contents. */
public record StripTargetBinding(UUID uuid, int entityId, ResourceKey<Level> dimension) {
    public StripTargetBinding {
        if (uuid == null || entityId < 0 || dimension == null) throw new IllegalArgumentException("Invalid strip target binding");
    }

    public boolean matches(UUID candidateUuid, int candidateEntityId, ResourceKey<Level> candidateDimension) {
        return uuid.equals(candidateUuid) && entityId == candidateEntityId && dimension.equals(candidateDimension);
    }
}
