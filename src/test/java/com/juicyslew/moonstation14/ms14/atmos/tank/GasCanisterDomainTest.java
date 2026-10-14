package com.juicyslew.moonstation14.ms14.atmos.tank;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GasCanisterDomainTest {
    private static final ResourceLocation PROFILE = ResourceLocation.fromNamespaceAndPath("moonstation14", "storage_canister");

    @Test void profileDefaultsAndInitializationUseResolvedComposition() {
        var profile = new GasCanisterProfile(1.5, 293.15, Map.of(GasType.PLASMA, 8.0));
        assertEquals(101.325, profile.releasePressureKpa());
        var changed = new GasCanisterProfile(2.0, 300, Map.of(GasType.OXYGEN, 4.0));
        var state = new GasCanisterState(PROFILE, changed, UUID.randomUUID(), new ItemLocationLedger());
        assertEquals(2.0, state.gas().volumeCubicMeters());
        assertEquals(4.0, state.gas().gasMoles().get(GasType.OXYGEN));
    }

    @Test void appearanceDefaultsAndChildOverridesAreTypedWithoutChangingPhysicalComposition() {
        var base = GasCanisterProfile.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(
                "{\"volumeCubicMeters\":1.5,\"temperatureKelvin\":293.15}")).getOrThrow();
        var storage = GasCanisterProfile.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(
                "{\"volumeCubicMeters\":1.5,\"temperatureKelvin\":293.15,\"modelSkinPrefix\":\"yellow\"}")).getOrThrow();
        var plasma = GasCanisterProfile.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(
                "{\"volumeCubicMeters\":1.5,\"temperatureKelvin\":293.15,\"initialMoles\":{\"plasma\":2769.36},\"modelSkinPrefix\":\"orange\",\"defaultLockedVisual\":true}")).getOrThrow();
        assertEquals("grey", base.modelSkinPrefix());
        assertEquals("yellow", storage.modelSkinPrefix());
        assertFalse(storage.defaultLockedVisual());
        assertEquals("orange", plasma.modelSkinPrefix());
        assertTrue(plasma.defaultLockedVisual());
        assertEquals(1.5, plasma.volumeCubicMeters());
        assertEquals(2769.36, plasma.initialMoles().get(GasType.PLASMA));
    }

    @Test void pressureAppearanceBandsUseExactThresholds() {
        assertEquals(0, com.juicyslew.moonstation14.entities.GasCanisterEntity.pressureBand(0));
        assertEquals(0, com.juicyslew.moonstation14.entities.GasCanisterEntity.pressureBand(Math.nextDown(10.0)));
        assertEquals(1, com.juicyslew.moonstation14.entities.GasCanisterEntity.pressureBand(10.0));
        assertEquals(1, com.juicyslew.moonstation14.entities.GasCanisterEntity.pressureBand(Math.nextDown(101.325)));
        assertEquals(2, com.juicyslew.moonstation14.entities.GasCanisterEntity.pressureBand(101.325));
        assertEquals(2, com.juicyslew.moonstation14.entities.GasCanisterEntity.pressureBand(Math.nextDown(1519.875)));
        assertEquals(3, com.juicyslew.moonstation14.entities.GasCanisterEntity.pressureBand(1519.875));
    }

    @Test void depletedSnapshotRestoresWithoutRefillingAndKeepsOwnerAndImmutableMap() {
        var profile = new GasCanisterProfile(1.5, 293.15, Map.of(GasType.PLASMA, 8.0));
        var owner = UUID.randomUUID();
        var state = new GasCanisterState(PROFILE, profile, owner, new ItemLocationLedger());
        var saved = state.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> saved.moles().put("oxygen", 1.0));
        var depleted = new GasCanisterState.Saved(saved.profile(), owner, Map.of(), saved.temperature(), saved.volume(),
                saved.releasePressure(), saved.valveClosed(), saved.revision(), saved.slot());
        var restored = GasCanisterState.restore(depleted, profile, new ItemLocationLedger());
        assertTrue(restored.gas().gasMoles().isEmpty());
        assertEquals(owner, restored.owner());
        var roundTrip = GasCanisterState.codec().parse(JsonOps.INSTANCE,
                GasCanisterState.codec().encodeStart(JsonOps.INSTANCE, depleted).getOrThrow()).getOrThrow();
        assertEquals(depleted.profile(), roundTrip.profile());
        assertEquals(depleted.owner(), roundTrip.owner());
        assertEquals(depleted.moles(), roundTrip.moles());
        assertNotNull(roundTrip.slot());
    }

    @Test void restoreRejectsCapacityAndPressureOutsideResolvedBounds() {
        var profile = new GasCanisterProfile(1, 293.15, Map.of());
        var state = new GasCanisterState(PROFILE, profile, UUID.randomUUID(), new ItemLocationLedger());
        var snapshot = state.snapshot();
        var mismatched = new GasCanisterProfile(2, 293.15, Map.of());
        assertThrows(IllegalArgumentException.class, () -> GasCanisterState.restore(snapshot, mismatched, new ItemLocationLedger()));
        var invalid = new GasCanisterState.Saved(snapshot.profile(), snapshot.owner(), snapshot.moles(), snapshot.temperature(),
                snapshot.volume(), 2000, snapshot.valveClosed(), snapshot.revision(), snapshot.slot());
        assertThrows(IllegalArgumentException.class, () -> GasCanisterState.restore(invalid, profile, new ItemLocationLedger()));
        for (double pressure : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new GasCanisterState.Saved(snapshot.profile(), snapshot.owner(),
                    snapshot.moles(), snapshot.temperature(), snapshot.volume(), pressure, true, 0, snapshot.slot()));
            var invalidJson = GasCanisterState.codec().encodeStart(JsonOps.INSTANCE, snapshot).getOrThrow();
            invalidJson.getAsJsonObject().addProperty("releasePressure", Double.toString(pressure));
            assertTrue(GasCanisterState.codec().parse(JsonOps.INSTANCE, invalidJson).error().isPresent());
        }
    }

    @Test void pressureAndValveControlsAreRevisionCheckedAndBounded() {
        var profile = new GasCanisterProfile(1.5, 293.15, Map.of(GasType.PLASMA, 8.0), 101.325, 10.1325, 1013.25);
        var state = new GasCanisterState(PROFILE, profile, UUID.randomUUID(), new ItemLocationLedger());
        var initial = state.revision();
        assertFalse(state.setReleasePressure(initial, Double.NaN));
        assertFalse(state.setReleasePressure(initial, 1013.251));
        assertFalse(state.setReleasePressure(initial - 1, 200));
        assertEquals(initial, state.revision());
        assertTrue(state.setReleasePressure(initial, 200));
        assertEquals(initial + 1, state.revision());
        assertFalse(state.setValveClosed(initial, false));
        assertTrue(state.setValveClosed(state.revision(), false));
        var revision = state.revision();
        assertFalse(state.setValveClosed(revision - 1, true));
        assertEquals(revision, state.revision());
        assertFalse(state.valveClosed());
    }
}
