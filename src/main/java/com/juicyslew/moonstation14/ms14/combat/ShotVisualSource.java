package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.resources.ResourceLocation;
/** Public presentation correlation only; never contains action authority credentials. */
public record ShotVisualSource(String handId, ResourceLocation itemId, long shotSequence) {
    public ShotVisualSource { if (handId == null || handId.isBlank() || itemId == null || shotSequence < 0) throw new IllegalArgumentException("invalid visual source"); }
}
