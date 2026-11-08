package com.juicyslew.moonstation14.ms14.singularity;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SingularityGeneratorStartupTest {
    private final SingularityGeneratorProfile profile = new SingularityGeneratorProfile(16, false, 16, 10);

    @Test void inheritedComponentDefaultsAreExtractedAndResourceResolves() throws Exception {
        assertEquals(16, SingularityGeneratorProfile.DEFAULT_THRESHOLD);
        assertEquals(16, SingularityGeneratorProfile.DEFAULT_FAILSAFE_DISTANCE);
        assertEquals(10, SingularityGeneratorProfile.DEFAULT_FAILSAFE_COOLDOWN_SECONDS);
        try (var stream = getClass().getResourceAsStream("/data/moonstation14/moonstation14/singularity_generator/default.json")) {
            assertNotNull(stream);
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream));
            var decoded = SingularityGeneratorProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            assertEquals(profile, decoded);
            assertTrue(decoded.failsafeEnabled());
            assertEquals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "singularity"), decoded.spawnProfile());
        }
        assertTrue(SingularityGeneratorProfile.CODEC.parse(JsonOps.INSTANCE, new com.google.gson.JsonObject()).getOrThrow().equals(
                new SingularityGeneratorProfile(16, false, 16, 10)));
        var customized=SingularityGeneratorProfile.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(
                "{\"startupThreshold\":20,\"failsafeEnabled\":false,\"failsafeDistance\":8,\"failsafeCooldownSeconds\":3,\"spawnProfile\":\"moonstation14:singularity\"}")).getOrThrow();
        assertEquals(20,customized.startupThreshold()); assertFalse(customized.failsafeEnabled());
        assertEquals(8,customized.failsafeDistance()); assertEquals(3,customized.failsafeCooldownSeconds());
    }

    @Test void localPrototypeCandidateResolvesBaseInheritanceAndCrossReference() throws Exception {
        var baseId=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","base");
        var defaultId=ModSingularityGenerators.DEFAULT_ID;
        var raw=new java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation,com.google.gson.JsonObject>();
        try(var in=getClass().getResourceAsStream("/data/moonstation14/moonstation14/singularity_generator/base.json")) {
            assertNotNull(in); raw.put(baseId,com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject());
        }
        try(var in=getClass().getResourceAsStream("/data/moonstation14/moonstation14/singularity_generator/default.json")) {
            assertNotNull(in); raw.put(defaultId,com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject());
        }
        var manager=new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager(); manager.register(SingularityGeneratorProfile.TYPE);
        manager.reload(SingularityGeneratorProfile.TYPE,raw);
        var resolved=manager.snapshot(SingularityGeneratorProfile.TYPE).get(defaultId);
        assertEquals(profile,resolved);
        assertEquals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","singularity"),resolved.spawnProfile());
    }

    @Test void thresholdBoundaryAndStandbyStrength() {
        var state = new SingularityGeneratorStartup(profile);
        assertFalse(state.accept(15, true, 0, profile));
        assertFalse(state.accept(0, true, 0, profile));
        assertTrue(state.accept(1, true, 0, profile));
        assertTrue(state.inert());
        assertEquals(0, state.charge());
    }

    @Test void resolvedStartsInertDefaultIsHonoredOnFirstInitialization() {
        var inertProfile=new SingularityGeneratorProfile(16,true,true,16,10,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","singularity"));
        var state=new SingularityGeneratorStartup(inertProfile);
        assertTrue(state.inert());
        assertFalse(state.accept(16,true,0,inertProfile));
        assertEquals(0,state.charge());
    }

    @Test void blockedAndCooldownAttemptsCannotAddChargeAndIntentIsUnique() {
        var state = new SingularityGeneratorStartup(profile);
        assertFalse(state.accept(8, false, 0, profile));
        assertEquals(0, state.charge());
        assertFalse(state.accept(15, true, 120, profile));
        assertFalse(state.accept(15, true, 199, profile));
        assertEquals(0, state.charge());
        assertTrue(state.accept(16, true, 200, profile));
        assertFalse(state.accept(100, true, 201, profile));
        assertEquals(0, state.charge());
    }

    @Test void persistentStateRestoresCooldownAndInert() {
        var pending = new SingularityGeneratorStartup(profile);
        pending.accept(1, false, 10, profile);
        var restored = new SingularityGeneratorStartup(profile, pending.snapshot().charge(), pending.snapshot().inert(), pending.snapshot().nextFailsafeTick());
        assertFalse(restored.accept(16, true, 209, profile));
        assertTrue(restored.accept(16, true, 210, profile));
        var inertRestored = new SingularityGeneratorStartup(profile, restored.snapshot().charge(), restored.snapshot().inert(), restored.snapshot().nextFailsafeTick());
        assertFalse(inertRestored.accept(16, true, 500, profile));
    }

    @Test void blockedParticlesDuringFailsafeDoNotExtendCooldown() {
        var state = new SingularityGeneratorStartup(profile);
        assertFalse(state.accept(1, false, 10, profile));
        assertFalse(state.accept(1, false, 100, profile));
        assertEquals(210, state.nextFailsafeTick());
        assertTrue(state.accept(16, true, 210, profile));
    }

    @Test void threeDimensionalFailsafeRatesEveryFaceDistance() {
        var bounds=new com.juicyslew.moonstation14.ms14.singularity.containment.ClosedFieldVolume.Bounds(
                new net.minecraft.core.BlockPos(0,0,0),new net.minecraft.core.BlockPos(4,4,4));
        // Block bounds [0,4] resolve to face planes at block centers .5 and 4.5.
        assertTrue(com.juicyslew.moonstation14.ms14.singularity.containment.ClosedFieldVolume.containsWithinFaceDistance(bounds,new net.minecraft.world.phys.Vec3(2.5,2.5,2.5),2));
        assertFalse(com.juicyslew.moonstation14.ms14.singularity.containment.ClosedFieldVolume.containsWithinFaceDistance(bounds,new net.minecraft.world.phys.Vec3(2.5,2.5,2.5),1.9));
        assertFalse(com.juicyslew.moonstation14.ms14.singularity.containment.ClosedFieldVolume.containsWithinFaceDistance(bounds,new net.minecraft.world.phys.Vec3(5,2,2),16));
    }
}
