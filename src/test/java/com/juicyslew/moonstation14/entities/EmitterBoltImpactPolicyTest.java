package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.ms14.singularity.emitter.EmitterProfile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class EmitterBoltImpactPolicyTest {
    @Test void cuesOnlyQualifiedContactsAndRejectsRemovedTargets() {
        assertTrue(EmitterBoltImpactPolicy.emits(EmitterBoltImpactPolicy.Contact.LIVING, false, false));
        assertFalse(EmitterBoltImpactPolicy.emits(EmitterBoltImpactPolicy.Contact.LIVING, true, false));
        assertTrue(EmitterBoltImpactPolicy.emits(EmitterBoltImpactPolicy.Contact.FIELD_GENERATOR, false, true));
        assertFalse(EmitterBoltImpactPolicy.emits(EmitterBoltImpactPolicy.Contact.BLOCK, false, false));
        // Collision-shape admission is independent of breakability (e.g. bedrock).
        assertTrue(EmitterBoltImpactPolicy.emits(EmitterBoltImpactPolicy.Contact.BLOCK, false, true));
        assertFalse(EmitterBoltImpactPolicy.emits(EmitterBoltImpactPolicy.Contact.NONE, false, true));
    }

    @Test void resolvedProfileImpactSoundOverrideIsCapturedAsProfileValue() {
        var sound = ResourceLocation.fromNamespaceAndPath("moonstation14", "custom_impact");
        var profile = new EmitterProfile(ResourceLocation.fromNamespaceAndPath("moonstation14", "test"),
                600, 1, 3, 40, 80, 200, 40, 60, 14, .2,
                ResourceLocation.fromNamespaceAndPath("moonstation14", "emitter/fire"), sound);
        assertEquals(sound, profile.boltImpactSound());
        var defaultProfile = new EmitterProfile(ResourceLocation.fromNamespaceAndPath("moonstation14", "default"),
                600, 1, 3, 40, 80, 200, 40, 60, 14, .2);
        assertEquals(ResourceLocation.fromNamespaceAndPath("moonstation14", "pa_particle_hit"), defaultProfile.boltImpactSound());
    }

    @Test void spriteFramePixelsConvertToSixteenPixelWorldExtent() {
        assertEquals(.5f, EmitterBoltImpactPolicy.spriteScale(32));
    }
}
