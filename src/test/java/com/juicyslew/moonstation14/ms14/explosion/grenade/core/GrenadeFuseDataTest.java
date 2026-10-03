package com.juicyslew.moonstation14.ms14.explosion.grenade.core;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrenadeFuseDataTest {
    @Test
    void codecRoundTripsSafeArmedAndSpent() {
        for (GrenadeFuseData data : new GrenadeFuseData[] {
                GrenadeFuseData.SAFE,
                new GrenadeFuseData(GrenadeFuse.Phase.ARMED, 160, true, false),
                new GrenadeFuseData(GrenadeFuse.Phase.SPENT, 160, true, true) }) {
            var encoded = GrenadeFuseData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
            assertEquals(data, GrenadeFuseData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        }
    }

    @Test
    void rejectsInvalidDeadlinesAndMilestones() {
        for (String json : new String[] {
                "{phase:'SAFE',deadline:-2,beeped_at_prime:false,beeped_at_forty:false}",
                "{phase:'ARMED',deadline:-1,beeped_at_prime:false,beeped_at_forty:false}",
                "{phase:'ARMED',deadline:59,beeped_at_prime:false,beeped_at_forty:false}",
                "{phase:'ARMED',deadline:60,beeped_at_prime:false,beeped_at_forty:true}",
                "{phase:'UNKNOWN',deadline:60,beeped_at_prime:false,beeped_at_forty:false}" }) {
            assertTrue(GrenadeFuseData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent(), json);
        }
        assertTrue(GrenadeFuseData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"phase\":\"ARMED\",\"deadline\":9223372036854775808,\"beeped_at_prime\":false,\"beeped_at_forty\":false}"))
                .error().isPresent());
    }

    @Test
    void convertsRuntimeStateWhenItsMilestonesAreAvailable() {
        GrenadeFuse fuse = GrenadeFuse.safe().prime(100).evaluate(120).state();
        GrenadeFuse restored = GrenadeFuseData.fromFuse(fuse).toFuse();
        assertEquals(fuse.phase(), restored.phase());
        assertEquals(fuse.deadline(), restored.deadline());
        assertEquals(fuse.beepedAtPrime(), restored.beepedAtPrime());
        assertEquals(fuse.beepedAtForty(), restored.beepedAtForty());
    }
}
