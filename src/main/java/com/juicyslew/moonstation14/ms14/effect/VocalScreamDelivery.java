package com.juicyslew.moonstation14.ms14.effect;

import com.juicyslew.moonstation14.ms14.character.components.VocalComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** Sound resolution and deterministic selection seam for prototype-defined vocal screams. */
public final class VocalScreamDelivery {
    public static final ResourceLocation WILHELM = ResourceLocation.fromNamespaceAndPath("moonstation14", "vocal_wilhelm");
    private VocalScreamDelivery() { }

    public static ResourceLocation select(VocalComponent vocal, double random) {
        if (vocal == null || !Double.isFinite(random) || random < 0 || random >= 1) return null;
        return random < vocal.wilhelmProbability() ? WILHELM : vocal.screamSound();
    }

    public static SoundEvent resolve(VocalComponent vocal, double random) {
        ResourceLocation id = select(vocal, random);
        return id == null ? null : BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
    }

}
