package com.juicyslew.moonstation14.ms14.singularity.pa;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

/** Contract future PA block entities implement; incomplete construction is not an identity. */
public interface ParticleAcceleratorPartIdentity {
    String role();
    ResourceLocation prototypeId();
    Rotation orientation();
    boolean anchored();
    boolean completed();
}

