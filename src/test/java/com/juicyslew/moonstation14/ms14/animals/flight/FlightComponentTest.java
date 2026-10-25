package com.juicyslew.moonstation14.ms14.animals.flight;

import com.juicyslew.moonstation14.ms14.character.components.FlightComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlightComponentTest {
    @Test void markerCapabilityDecodes() {
        var decoded = FlightComponent.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"type\":\"Flight\"}")).result().orElseThrow();
        assertEquals(new FlightComponent(), decoded);
    }

    @Test void rejectsUnknownComponentType() {
        assertThrows(IllegalArgumentException.class, () -> FlightComponent.CODEC.parse(
                com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"type\":\"NotFlight\"}")));
    }

    @Test void cruiseHeightDefaultsAndBoundsMinecraftAdaptation() {
        var defaults = FlightComponent.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"type\":\"Flight\"}")).result().orElseThrow();
        assertEquals(4.0, defaults.npcCruiseHeight());
        assertEquals(.25, defaults.groundedSpeedMultiplier());
        assertEquals(400, defaults.npcAirborneTicks());
        assertEquals(160, defaults.npcGroundedTicks());
        assertEquals(.4, defaults.npcVerticalSpeedMultiplier());
        assertEquals(0.80, defaults.airborneSpeedMultiplier());
        assertEquals(4, defaults.throwRecoveryTicks());
        var policy = FlightComponent.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"type\":\"Flight\",\"npc_airborne_ticks\":800,\"npc_grounded_ticks\":240,\"npc_vertical_speed_multiplier\":0.6}")).result().orElseThrow();
        assertEquals(800, policy.npcAirborneTicks());
        assertEquals(240, policy.npcGroundedTicks());
        assertEquals(.6, policy.npcVerticalSpeedMultiplier());
        var customFlight = FlightComponent.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"type\":\"Flight\",\"airborne_speed_multiplier\":1.5,\"throw_recovery_ticks\":8}")).result().orElseThrow();
        assertEquals(1.5, customFlight.airborneSpeedMultiplier());
        assertEquals(8, customFlight.throwRecoveryTicks());
        assertThrows(IllegalArgumentException.class, () -> new FlightComponent(4, .25, 400, 160, .4, 0, 4));
        assertThrows(IllegalArgumentException.class, () -> new FlightComponent(4, .25, 400, 160, .4, 4.01, 4));
        assertThrows(IllegalArgumentException.class, () -> new FlightComponent(4, .25, 400, 160, .4, Double.NaN, 4));
        assertThrows(IllegalArgumentException.class, () -> new FlightComponent(4, .25, 400, 160, .4, Double.POSITIVE_INFINITY, 4));
        assertThrows(IllegalArgumentException.class, () -> new FlightComponent(4, .25, 400, 160, .4, 1.2, 0));
        assertThrows(IllegalArgumentException.class, () -> new FlightComponent(4, .25, 400, 160, .4, 1.2, 41));
        assertThrows(IllegalArgumentException.class, () -> FlightComponent.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"type\":\"Flight\",\"airborne_speed_multiplier\":4.01}")));
        assertTrue(FlightComponent.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"type\":\"Flight\",\"airborne_speed_multiplier\":\"NaN\"}")).error().isPresent());
        assertTrue(FlightComponent.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"type\":\"Flight\",\"throw_recovery_ticks\":1.5}")).error().isPresent());
        assertThrows(IllegalArgumentException.class, () -> new FlightComponent(4, .25, 19, 160, .4));
        assertThrows(IllegalArgumentException.class, () -> new FlightComponent(4, .25, 400, 2401, .4));
        assertThrows(IllegalArgumentException.class, () -> new FlightComponent(4, .25, 400, 160, 0));
        var custom = FlightComponent.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"type\":\"Flight\",\"npc_cruise_height\":2.1}")).result().orElseThrow();
        assertEquals(2.1, custom.npcCruiseHeight());
        var slowGround = FlightComponent.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"type\":\"Flight\",\"grounded_speed_multiplier\":0.2}")).result().orElseThrow();
        assertEquals(.2, slowGround.groundedSpeedMultiplier());
        assertEquals(FlightComponent.MAX_NPC_CRUISE_HEIGHT, new FlightComponent(6).npcCruiseHeight());
        assertThrows(IllegalArgumentException.class, () -> new FlightComponent(6.01));
        assertThrows(IllegalArgumentException.class, () -> new FlightComponent(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new FlightComponent(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new FlightComponent(1.5, 0));
        assertThrows(IllegalArgumentException.class, () -> com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit
                .auditComponentFragment(com.google.gson.JsonParser.parseString(
                         "{\"type\":\"Flight\",\"npc_cruise_height\":6.01}").getAsJsonObject(), "$.components[0]"));
        assertDoesNotThrow(() -> com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit
                .auditComponentFragment(com.google.gson.JsonParser.parseString(
                        "{\"type\":\"Flight\",\"npc_cruise_height\":2}").getAsJsonObject(), "$.components[0]"));
        assertDoesNotThrow(() -> com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit
                .auditComponentFragment(com.google.gson.JsonParser.parseString(
                        "{\"type\":\"Flight\",\"grounded_speed_multiplier\":0.25}").getAsJsonObject(), "$.components[0]"));
        assertDoesNotThrow(() -> com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit
                .auditComponentFragment(com.google.gson.JsonParser.parseString(
                        "{\"type\":\"Flight\",\"npc_airborne_ticks\":400,\"npc_vertical_speed_multiplier\":0.4}").getAsJsonObject(), "$.components[0]"));
        assertThrows(IllegalArgumentException.class, () -> com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit
                .auditComponentFragment(com.google.gson.JsonParser.parseString(
                        "{\"type\":\"Flight\",\"npc_grounded_ticks\":19}").getAsJsonObject(), "$.components[0]"));
        for (String invalid : java.util.List.of(
                "{\"type\":\"Flight\",\"airborne_speed_multiplier\":4.01}",
                "{\"type\":\"Flight\",\"airborne_speed_multiplier\":1e309}",
                "{\"type\":\"Flight\",\"throw_recovery_ticks\":0}",
                "{\"type\":\"Flight\",\"throw_recovery_ticks\":41}",
                "{\"type\":\"Flight\",\"throw_recovery_ticks\":1.5}"))
            assertThrows(IllegalArgumentException.class, () -> com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit
                    .auditComponentFragment(com.google.gson.JsonParser.parseString(invalid).getAsJsonObject(), "$.components[0]"), invalid);
        assertDoesNotThrow(() -> com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit
                .auditComponentFragment(com.google.gson.JsonParser.parseString(
                        "{\"type\":\"Flight\",\"airborne_speed_multiplier\":4,\"throw_recovery_ticks\":40}").getAsJsonObject(), "$.components[0]"));
    }
}
