package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.ms14.combat.PositionalSoundCue;
import com.juicyslew.moonstation14.ms14.combat.throwing.ThrownItemProfile;
import com.juicyslew.moonstation14.ms14.combat.throwing.LooseItemCollisionSoundPolicy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class LooseItemCollisionSoundPolicyTest {
    private static final ThrownItemProfile PROFILE = new ThrownItemProfile(Map.of(), false, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.of(new ThrownItemProfile.CollisionSoundData(new PositionalSoundCue(ResourceLocation.withDefaultNamespace("entity.item.pickup"), 0), 3, .2f)), 2);

    @Test void thresholdGainCooldownAndSharedBodyTerrainAdmission() {
        var policy = new LooseItemCollisionSoundPolicy();
        assertTrue(policy.terrain(PROFILE, 1, new Vec3(.149, 0, 0), 1).isEmpty());
        assertTrue(policy.terrain(PROFILE, 0, Vec3.ZERO, 4).isEmpty());
        assertEquals(-10f, policy.terrain(PROFILE, 1, new Vec3(.15, 0, 0), 5).orElseThrow().volumeDb(), .01f);
        assertTrue(policy.body(PROFILE, new Vec3(1, 0, 0), 6).isEmpty());
        assertEquals(2f, policy.body(PROFILE, new Vec3(.65, 0, 0), 9).orElseThrow().volumeDb(), .01f);
        assertTrue(policy.terrain(PROFILE, 1, new Vec3(.5, 0, 0), 10).isEmpty());
    }

    @Test void terrainRestDoesNotRepeatAndSeparationAllowsNewEntry() {
        var policy = new LooseItemCollisionSoundPolicy();
        assertTrue(policy.terrain(PROFILE, 2, new Vec3(1, 0, 0), 1).isPresent());
        for (int t = 2; t < 100; t++) assertTrue(policy.terrain(PROFILE, 2, new Vec3(1, 0, 0), t).isEmpty());
        assertTrue(policy.terrain(PROFILE, 0, Vec3.ZERO, 100).isEmpty());
        assertTrue(policy.terrain(PROFILE, 2, new Vec3(1, 0, 0), 101).isPresent());
        assertTrue(new LooseItemCollisionSoundPolicy().body(null, Vec3.ZERO, 1).isEmpty());
    }

    @Test void explicitLandingCueUsesTerrainEntryAndSharedCooldown() {
        var policy = new LooseItemCollisionSoundPolicy();
        var cue = new ThrownItemProfile.CollisionSoundData(new PositionalSoundCue(ResourceLocation.withDefaultNamespace("entity.item.pickup"), -2), .5f, .2f);
        assertTrue(policy.terrain(cue, 2, new Vec3(0, -.025, 0), 1).isPresent());
        assertTrue(policy.terrain(cue, 2, new Vec3(0, -.5, 0), 2).isEmpty());
        assertTrue(policy.terrain(cue, 0, Vec3.ZERO, 3).isEmpty());
        assertTrue(policy.terrain(cue, 2, new Vec3(0, -.5, 0), 4).isEmpty());
    }
}
