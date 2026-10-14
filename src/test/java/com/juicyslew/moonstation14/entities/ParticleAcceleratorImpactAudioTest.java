package com.juicyslew.moonstation14.entities;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParticleAcceleratorImpactAudioTest {
    private static final String SOUND = "moonstation14:pa_particle_hit";

    @Test void qualifiedLivingContactSoundsRegardlessOfDamageAdmissionOrPostHitAliveState() {
        assertEquals(ResourceLocation.parse(SOUND), ParticleAcceleratorImpactAudio.sound(SOUND,
                ParticleAcceleratorImpactAudio.Contact.LIVING, false, false));
        // Damage result and post-hit alive state do not gate audio; removal does.
        assertNull(ParticleAcceleratorImpactAudio.sound(SOUND, ParticleAcceleratorImpactAudio.Contact.LIVING, false, true));
        assertNull(ParticleAcceleratorImpactAudio.sound(SOUND, ParticleAcceleratorImpactAudio.Contact.LIVING, true, false));
    }

    @Test void acceptedOrRejectedGeneratorContactSoundsButSingularityAndWorldDoNot() {
        assertEquals(ResourceLocation.parse(SOUND), ParticleAcceleratorImpactAudio.sound(SOUND,
                ParticleAcceleratorImpactAudio.Contact.GENERATOR, false, false));
        assertNull(ParticleAcceleratorImpactAudio.sound(SOUND, ParticleAcceleratorImpactAudio.Contact.SINGULARITY, false, false));
        assertNull(ParticleAcceleratorImpactAudio.sound(SOUND, ParticleAcceleratorImpactAudio.Contact.WORLD, false, false));
        assertNull(ParticleAcceleratorImpactAudio.sound(" ", ParticleAcceleratorImpactAudio.Contact.GENERATOR, false, false));
    }
}
