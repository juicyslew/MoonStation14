package com.juicyslew.moonstation14.ms14.audio;

import com.juicyslew.moonstation14.ms14.atmos.tank.GasCanisterProfile;
import com.juicyslew.moonstation14.ms14.hands.live.ItemSlotAudio;
import com.juicyslew.moonstation14.ms14.radiation.RadiationCollectorProfile;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class ItemSlotAudioProfileTest {
    @Test void canisterResolvedDefaultsIncludeInheritedSlotAndValveAudio() {
        var parsed = GasCanisterProfile.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(
                "{\"volumeCubicMeters\":1,\"temperatureKelvin\":293.15}")).result().orElseThrow();
        assertEquals(ItemSlotAudio.defaultInsert(), parsed.slotInsertSound());
        assertEquals(ItemSlotAudio.defaultEject(), parsed.slotEjectSound());
        assertEquals(ResourceLocation.parse("moonstation14:gas_valve_toggle"), parsed.valveSound());
        assertEquals(-5f, parsed.valveVolumeDb());
        assertEquals((float)Math.pow(10, -5.0 / 20), ItemSlotAudio.decibels(parsed.valveVolumeDb()), 1e-6);
        var override = GasCanisterProfile.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(
                "{\"volumeCubicMeters\":1,\"temperatureKelvin\":293.15,\"slotInsertSound\":\"moonstation14:profile_in\",\"slotEjectSound\":\"moonstation14:profile_out\"}")).result().orElseThrow();
        assertEquals(ResourceLocation.parse("moonstation14:profile_in"), override.slotInsertSound());
        assertEquals(ResourceLocation.parse("moonstation14:profile_out"), override.slotEjectSound());
        assertEquals(List.of(override.slotInsertSound()), ItemSlotAudio.committedCues(ItemSlotAudio.Transfer.INSERT,
                com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer.Result.SUCCESS,
                override.slotInsertSound(), override.slotEjectSound()));
    }

    @Test void collectorProfileResolvesItemSlotSchemaDefaults() {
        var json = com.google.gson.JsonParser.parseString("{\"id\":\"moonstation14:test\",\"charge_modifier\":1,\"reactant\":\"plasma\",\"efficiency\":1,\"breakdown_rate\":1,\"power_holdover_ticks\":0}");
        var parsed = RadiationCollectorProfile.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        assertEquals(ItemSlotAudio.defaultInsert(), parsed.slotInsertSound());
        assertEquals(ItemSlotAudio.defaultEject(), parsed.slotEjectSound());
    }

    @Test void successfulCollectorInsertConsumesResolvedOverrideAndRejectedReceiptsHaveNoCue() {
        var insert = ResourceLocation.parse("moonstation14:test_insert");
        var eject = ResourceLocation.parse("moonstation14:test_eject");
        var json = com.google.gson.JsonParser.parseString("{\"id\":\"moonstation14:test\",\"charge_modifier\":1,\"reactant\":\"plasma\",\"efficiency\":1,\"breakdown_rate\":1,\"power_holdover_ticks\":0,\"slotInsertSound\":\"moonstation14:test_insert\",\"slotEjectSound\":\"moonstation14:test_eject\"}");
        var profile = RadiationCollectorProfile.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        assertEquals(List.of(insert), ItemSlotAudio.committedCues(ItemSlotAudio.Transfer.INSERT, com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer.Result.SUCCESS,
                profile.slotInsertSound(), profile.slotEjectSound()));
        assertTrue(ItemSlotAudio.committedCues(ItemSlotAudio.Transfer.INSERT, com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer.Result.DENIED,
                profile.slotInsertSound(), profile.slotEjectSound()).isEmpty());
    }

    @Test void swapUsesResolvedEjectThenInsertAndFailureOrRollbackIsSilent() {
        var insert = ResourceLocation.parse("moonstation14:profile_in");
        var eject = ResourceLocation.parse("moonstation14:profile_out");
        assertEquals(List.of(eject, insert), ItemSlotAudio.committedCues(ItemSlotAudio.Transfer.SWAP,
                com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer.Result.SUCCESS, insert, eject));
        assertTrue(ItemSlotAudio.committedCues(ItemSlotAudio.Transfer.SWAP, com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer.Result.RECOVERY_REQUIRED, insert, eject).isEmpty());
        assertTrue(ItemSlotAudio.committedCues(ItemSlotAudio.Transfer.EJECT, com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer.Result.DENIED, insert, eject).isEmpty());
    }
}

