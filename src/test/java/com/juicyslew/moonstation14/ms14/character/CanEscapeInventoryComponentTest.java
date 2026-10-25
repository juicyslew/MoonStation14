package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.character.components.CanEscapeInventoryComponent;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CanEscapeInventoryComponentTest {
    @Test void missingDurationUsesPinnedDefaultAndExplicitDurationOverrides() {
        var codec = CanEscapeInventoryComponent.CODEC;
        var defaulted = codec.parse(JsonOps.INSTANCE, JsonParser.parseString("{type:'CanEscapeInventory'}")).getOrThrow();
        assertEquals(5f, defaulted.baseResistTimeSeconds());
        var overridden = codec.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{type:'CanEscapeInventory',baseResistTime:3}")).getOrThrow();
        assertEquals(3f, overridden.baseResistTimeSeconds());
    }

    @Test void invalidDurationIsRejected() {
        assertTrue(CanEscapeInventoryComponent.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{type:'CanEscapeInventory',baseResistTime:0}")).result().isEmpty());
    }
}
