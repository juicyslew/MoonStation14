package com.juicyslew.moonstation14.ms14.actions;

import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Client intent only. providerId is an identifier, never proof of authorization. */
public record BodyActionRequest(UUID bodyId, long bodyEpoch, long revision, ResourceLocation actionId,
                                String providerId, long sequence) {
    public BodyActionRequest {
        if (bodyId == null || bodyEpoch <= 0 || revision < 0 || actionId == null || providerId == null
                || providerId.isBlank() || sequence <= 0) throw new IllegalArgumentException("Invalid body action request");
    }
}
