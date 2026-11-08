package com.juicyslew.moonstation14.ms14.animals;

import com.juicyslew.moonstation14.ms14.character.components.UdderComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UdderStateTest {
    @Test void growthIntervalAndPersistentDepletionState() {
        assertEquals(1200, UdderSystem.delayTicks(UdderComponent.DEFAULT));
        assertEquals(600, UdderSystem.delayTicks(new UdderComponent(25, 10, 30, 250, "udder")));
        var depleted = new UdderAttachment(25, 1500).withAvailable(0);
        assertEquals(0, depleted.available());
        assertEquals(1500, depleted.nextGrowthTick());
        assertEquals(depleted, UdderAttachment.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"available\":0,\"next_growth_tick\":1500}"))
                .result().orElseThrow());
    }

    @Test void solutionIdentifierPersistsSeparatelyFromMilkReagentIdentity() {
        var cow = new UdderAttachment(25, 900, "udder");
        var custom = new UdderAttachment(25, 900, "reservoir");
        assertEquals("udder", cow.solutionName());
        assertEquals("reservoir", custom.solutionName());
        assertEquals(25f, custom.available()); // amount is still the milk reagent quantity, not a reagent named reservoir
        assertNotEquals(cow, custom);
    }
}
