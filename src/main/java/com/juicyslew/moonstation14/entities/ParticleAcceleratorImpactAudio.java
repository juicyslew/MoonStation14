package com.juicyslew.moonstation14.entities;

import net.minecraft.resources.ResourceLocation;

/** Pure qualification policy for the projectile's one-shot contact impact cue. */
final class ParticleAcceleratorImpactAudio {
    enum Contact { LIVING, GENERATOR, SINGULARITY, WORLD }
    private ParticleAcceleratorImpactAudio() { }

    static ResourceLocation sound(String configuredSound, Contact contact, boolean spent, boolean removed) {
        if (configuredSound == null || configuredSound.isBlank() || contact == null || removed
                || contact == Contact.SINGULARITY || contact == Contact.WORLD
                || contact == Contact.LIVING && spent) return null;
        return ResourceLocation.tryParse(configuredSound);
    }
}
