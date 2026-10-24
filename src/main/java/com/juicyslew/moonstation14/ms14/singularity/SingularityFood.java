package com.juicyslew.moonstation14.ms14.singularity;

/** Optional server-side food credit for entities and block entities consumed by a singularity. */
public interface SingularityFood extends SingularityConsumable {
    double singularityFoodEnergy();
    double singularityFoodFactor();
}
