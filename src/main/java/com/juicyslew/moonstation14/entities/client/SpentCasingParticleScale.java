package com.juicyslew.moonstation14.entities.client;

import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;

/** Converts the resolved loose-item visual canvas width/height into particle half extent. */
public final class SpentCasingParticleScale {
    private SpentCasingParticleScale() { }
    public static float halfExtent(LooseObjectProfile resolvedProfile) {
        if(resolvedProfile==null)throw new IllegalArgumentException("resolved loose-object profile is required");
        float extent=Math.max(resolvedProfile.visualWidth(),resolvedProfile.visualHeight())*.5f;
        if(!Float.isFinite(extent)||extent<=0)throw new IllegalArgumentException("invalid resolved particle visual extent");
        return extent;
    }
}
