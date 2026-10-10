package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmDeviceLinks;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AirAlarmControlProtocolTest {
    @org.junit.jupiter.api.Test
    void alarmAdmissionRequiresFreshFullDelivery() {
        var valid = new com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.VentMeterObservation(true, 100);
        var depowered = new com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.VentMeterObservation(true, 0);
        var stale = com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.VentMeterObservation.UNKNOWN;
        assertTrue(AirAlarmMenuService.powered(100, valid));
        assertFalse(AirAlarmMenuService.powered(100, depowered));
        assertFalse(AirAlarmMenuService.powered(100, stale));
        assertFalse(AirAlarmMenuService.powered(50, valid));
    }

    private static final UUID SESSION = UUID.randomUUID(), DEVICE = UUID.randomUUID();

    @Test void immediateRefreshIsForMatchedCommandResponsesOnly() {
        assertTrue(AirAlarmResponseQueryPolicy.shouldQuery(false, true));
        assertFalse(AirAlarmResponseQueryPolicy.shouldQuery(true, false));
        // A query response never schedules another immediate query; periodic refresh remains external.
        assertFalse(AirAlarmResponseQueryPolicy.shouldQuery(false, false));
    }

    private static AirAlarmControlRequest request(long id) {
        return new AirAlarmControlRequest(12, SESSION, id, DEVICE, 4,
                AirAlarmControlRequest.Operation.SET_FILTER_GAS, true, 0);
    }

    @Test void intentRoundTripsWithoutClientCoordinatesOrSettings() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        AirAlarmControlRequest.STREAM_CODEC.encode(buffer, request(8));
        assertEquals(request(8), AirAlarmControlRequest.STREAM_CODEC.decode(buffer));
        assertThrows(IllegalArgumentException.class, () -> new AirAlarmControlRequest(12, SESSION, 0, DEVICE, 0,
                AirAlarmControlRequest.Operation.SET_FILTER_GAS, true, GasType.values().length));
        assertThrows(IllegalArgumentException.class, () -> new AirAlarmControlRequest(12, SESSION, -1, DEVICE, 0,
                AirAlarmControlRequest.Operation.QUERY, false, 0));
    }

    @Test void numericIntentRoundTripsAndRejectsInvalidValues() {
        assertFalse(AirAlarmNumericBounds.valid(VentilationDeviceConfig.Kind.SCRUBBER, Double.NaN));
        assertFalse(AirAlarmNumericBounds.valid(VentilationDeviceConfig.Kind.SCRUBBER, -1));
        assertFalse(AirAlarmNumericBounds.valid(VentilationDeviceConfig.Kind.SCRUBBER, Double.POSITIVE_INFINITY));
        assertFalse(AirAlarmNumericBounds.valid(VentilationDeviceConfig.Kind.SCRUBBER, 0.201));
        assertTrue(AirAlarmNumericBounds.valid(VentilationDeviceConfig.Kind.SCRUBBER, 0.2));
        assertTrue(AirAlarmNumericBounds.valid(VentilationDeviceConfig.Kind.VENT, 4500));
        var numeric = new AirAlarmControlRequest(12, SESSION, 8, DEVICE, 4,
                AirAlarmControlRequest.Operation.SET_VOLUME_RATE, false, 0, 0.15);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        AirAlarmControlRequest.STREAM_CODEC.encode(buffer, numeric);
        assertEquals(numeric, AirAlarmControlRequest.STREAM_CODEC.decode(buffer));
        assertThrows(IllegalArgumentException.class, () -> new AirAlarmControlRequest(12, SESSION, 8, DEVICE, 4,
                AirAlarmControlRequest.Operation.SET_VOLUME_RATE, false, 0, Double.NaN));
        var vent = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT);
        var stale = new AirAlarmControlRequest(12, SESSION, 8, DEVICE, 3,
                AirAlarmControlRequest.Operation.SET_EXTERNAL_PRESSURE_BOUND, false, 0, 100);
        assertTrue(AirAlarmMenuService.propose(vent.settings(), vent.kind(), stale).isEmpty());
        var wrongKind = new AirAlarmControlRequest(12, SESSION, 8, DEVICE, 0,
                AirAlarmControlRequest.Operation.SET_VOLUME_RATE, false, 0, 0.1);
        assertTrue(AirAlarmMenuService.propose(vent.settings(), vent.kind(), wrongKind).isEmpty());
        assertTrue(AirAlarmMenuService.propose(vent.settings(), vent.kind(),
                new AirAlarmControlRequest(12, SESSION, 8, DEVICE, 0,
                        AirAlarmControlRequest.Operation.SET_EXTERNAL_PRESSURE_BOUND, false, 0, 4501)).isEmpty());
    }

    @Test void containerSessionAndReplayBoundIndependentlyOfBlockUseAdmission() {
        assertTrue(AirAlarmMenuService.admit(12, SESSION, -1, request(0)));
        assertTrue(AirAlarmMenuService.admit(12, SESSION, 7, request(8)));
        assertFalse(AirAlarmMenuService.admit(12, SESSION, 8, request(8)));
        assertFalse(AirAlarmMenuService.admit(12, SESSION, 9, request(8)));
        assertFalse(AirAlarmMenuService.admit(13, SESSION, 7, request(8)));
        assertFalse(AirAlarmMenuService.admit(12, UUID.randomUUID(), 7, request(8)));
    }

    @Test void snapshotIncludesServerOwnedLinkedOfflineStateAndCapsDecode() {
        var device = new AirAlarmControlSnapshot.Device(DEVICE, VentilationDeviceConfig.Kind.VENT,
                new BlockPos(3, 5, 8), true, false, false, 0, 0, 0);
        var snapshot = new AirAlarmControlSnapshot(12, SESSION, 8, true, false, List.of(device));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        AirAlarmControlSnapshot.STREAM_CODEC.encode(buffer, snapshot);
        assertEquals(snapshot, AirAlarmControlSnapshot.STREAM_CODEC.decode(buffer));
        var overflow = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        overflow.writeVarInt(12); overflow.writeUUID(SESSION); overflow.writeVarLong(8);
        overflow.writeBoolean(true); overflow.writeBoolean(false); overflow.writeVarInt(33);
        assertThrows(IllegalArgumentException.class, () -> AirAlarmControlSnapshot.STREAM_CODEC.decode(overflow));
    }

    @Test void mutationIntentReusesRevisionedVentilationPolicy() {
        var config = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.SCRUBBER);
        var current = config.settings();
        var change = new AirAlarmControlRequest(12, SESSION, 1, DEVICE, current.revision(),
                AirAlarmControlRequest.Operation.SET_SCRUBBER_MODE, false, 1);
        var proposed = AirAlarmMenuService.propose(current, config.kind(), change).orElseThrow();
        assertEquals(VentilationDeviceConfig.ScrubberMode.SIPHON, proposed.scrubberMode());
        assertTrue(config.compareAndSet(current.revision(), proposed));
        assertFalse(config.compareAndSet(current.revision(), proposed));
        assertTrue(AirAlarmMenuService.propose(config.settings(), config.kind(), change).isEmpty());
        var vent = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT);
        assertTrue(AirAlarmMenuService.propose(vent.settings(), vent.kind(), change).isEmpty());
        var pair = new AirAlarmControlRequest(12, SESSION, 2, DEVICE, config.settings().revision(),
                AirAlarmControlRequest.Operation.PAIR, false, 0);
        assertTrue(AirAlarmMenuService.propose(config.settings(), config.kind(), pair).isEmpty());
    }

    @Test void offlineLinkRemovalTargetsOnlyPersistedUuid() {
        var entries = new ListTag();
        var entry = new CompoundTag();
        entry.putString("Dimension", "minecraft:overworld");
        entry.putInt("X", 1); entry.putInt("Y", 70); entry.putInt("Z", 2);
        entry.putString("Kind", "VENT"); entry.putUUID("DeviceId", DEVICE);
        entries.add(entry);
        var saved = new CompoundTag(); saved.put("AirAlarmDeviceLinks", entries);
        var links = new AirAlarmDeviceLinks(); links.load(saved, 32);
        assertFalse(links.remove(UUID.randomUUID()));
        assertEquals(1, links.snapshot().size());
        assertTrue(links.remove(DEVICE));
        assertTrue(links.snapshot().isEmpty());
        assertFalse(links.remove(DEVICE));
    }
}
