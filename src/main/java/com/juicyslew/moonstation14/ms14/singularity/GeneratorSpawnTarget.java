package com.juicyslew.moonstation14.ms14.singularity;

import net.minecraft.resources.ResourceLocation;

/** Shared source-profile initialization contract for entities commissioned by generator machinery. */
public interface GeneratorSpawnTarget {
    boolean initializeSourceProfile(ResourceLocation profileId);
}
