package com.juicyslew.moonstation14.ms14.combat.client;

import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeleeArcClientPolicyTest {
    @Test void predictionTogglesFrozenSourceBit() {
        assertTrue(MeleeArcClient.predictedSwingLeft(false));
        assertFalse(MeleeArcClient.predictedSwingLeft(true));
    }
    @org.junit.jupiter.api.Test void observerEligibilityFiltersOwnPacketsAndWrongDimension() {
        var dimension = ResourceLocation.withDefaultNamespace("overworld");
        var payload = new com.juicyslew.moonstation14.ms14.combat.MeleeArcPayload(2, java.util.UUID.randomUUID(), dimension, java.util.UUID.randomUUID(),
                ResourceLocation.withDefaultNamespace("test"), 1, 60, 0, 0, net.minecraft.world.phys.Vec3.ZERO,
                net.minecraft.world.phys.Vec3.ZERO, net.minecraft.world.phys.Vec3.ZERO, null, false);
        assertTrue(MeleeArcClient.observerPayloadEligible(payload, java.util.UUID.randomUUID(), dimension));
        assertFalse(MeleeArcClient.observerPayloadEligible(payload, payload.initiatingPlayerUUID(), dimension));
        assertFalse(MeleeArcClient.observerPayloadEligible(payload, java.util.UUID.randomUUID(), ResourceLocation.withDefaultNamespace("nether")));
    }
    @Test void arcContextRequiresSameBodyEpochAndDimension() {
        var dimension = ResourceLocation.withDefaultNamespace("overworld");
        assertTrue(MeleeArcClient.contextMatches(java.util.UUID.randomUUID(), 2, dimension,
                java.util.UUID.randomUUID(), 2, dimension) == false);
        var body = java.util.UUID.randomUUID();
        assertTrue(MeleeArcClient.contextMatches(body, 2, dimension, body, 2, dimension));
        assertFalse(MeleeArcClient.contextMatches(body, 2, dimension, body, 3, dimension));
        assertFalse(MeleeArcClient.contextMatches(body, 2, dimension, body, 2, ResourceLocation.withDefaultNamespace("nether")));
    }
}
