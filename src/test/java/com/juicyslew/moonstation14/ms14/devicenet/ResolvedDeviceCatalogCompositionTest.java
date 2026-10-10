package com.juicyslew.moonstation14.ms14.devicenet;

import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.prototype.ModAirAlarms;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.ModVentilationDevices;
import com.juicyslew.moonstation14.ms14.devicenet.control.ModSignalControls;
import com.juicyslew.moonstation14.ms14.doors.prototype.ModAirlocks;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResolvedDeviceCatalogCompositionTest {
    @Test void everyShippedDeviceFamilyResolvesAgainstSharedCatalogs() {
        var manager = new PrototypeManager();
        manager.register(ModDeviceNetworks.FREQUENCY_TYPE);
        manager.register(ModDeviceNetworks.NETWORK_TYPE);
        manager.register(ModDeviceNetworks.PORT_TYPE);
        manager.register(ModSignalControls.TYPE);
        manager.register(ModAirlocks.TYPE);
        manager.register(ModAirAlarms.AIR_ALARM_TYPE);
        manager.register(ModVentilationDevices.VENTILATION_DEVICE_TYPE);
        manager.register(ModNetworkDeviceItems.TYPE);
        PrototypeResourceFixture.loadCatalog(manager, ModDeviceNetworks.FREQUENCY_TYPE);
        PrototypeResourceFixture.loadCatalog(manager, ModDeviceNetworks.NETWORK_TYPE);
        PrototypeResourceFixture.loadCatalog(manager, ModDeviceNetworks.PORT_TYPE);
        PrototypeResourceFixture.loadCatalog(manager, ModSignalControls.TYPE);
        PrototypeResourceFixture.loadCatalog(manager, ModAirlocks.TYPE);
        PrototypeResourceFixture.loadCatalog(manager, ModAirAlarms.AIR_ALARM_TYPE);
        PrototypeResourceFixture.loadCatalog(manager, ModVentilationDevices.VENTILATION_DEVICE_TYPE);
        PrototypeResourceFixture.loadCatalog(manager, ModNetworkDeviceItems.TYPE);

        for (var id : java.util.List.of(ModSignalControls.SIGNAL_BUTTON, ModSignalControls.SIGNAL_SWITCH)) {
            var data = manager.snapshot(ModSignalControls.TYPE).get(id);
            var net = data.deviceComponents().resolve(manager).network().orElseThrow();
            assertEquals(ModDeviceNetworks.WIRELESS_NETWORK_ID, net.networkId());
            assertEquals(1280, net.transmitFrequency());
            assertEquals(1280, net.receiveFrequency());
            assertEquals("", net.addressPrefix());
        }
        var remote = manager.snapshot(ModNetworkDeviceItems.TYPE).get(ModDeviceNetworks.id("standardwireless15")).deviceComponents().resolve(manager);
        assertEquals(2, remote.network().orElseThrow().networkId());
        assertEquals(1280, remote.network().orElseThrow().receiveFrequency());
        assertEquals(0, remote.network().orElseThrow().transmitFrequency());
        assertEquals("", remote.network().orElseThrow().addressPrefix());
        assertEquals(15, remote.connection().orElseThrow().wirelessRange());
        var advanced = manager.snapshot(ModNetworkDeviceItems.TYPE).get(ModDeviceNetworks.id("advancedwireless50")).deviceComponents().resolve(manager);
        assertEquals(50, advanced.connection().orElseThrow().wirelessRange());

        var alarm = manager.snapshot(ModAirAlarms.AIR_ALARM_TYPE).get(ModAirAlarms.AIR_ALARM)
                .deviceComponents().resolve(manager);
        assertEquals(4, alarm.network().orElseThrow().networkId());
        assertEquals(1621, alarm.network().orElseThrow().receiveFrequency());
        assertEquals(32, alarm.deviceList().orElseThrow().capacity());
        assertTrue(alarm.network().orElseThrow().requiresPower());
        assertEquals("AIR-", alarm.network().orElseThrow().addressPrefix());
        assertTrue(alarm.connection().orElseThrow().wired());
        assertTrue(alarm.connection().orElseThrow().wireless());

        var vents = manager.snapshot(ModVentilationDevices.VENTILATION_DEVICE_TYPE);
        for (var entry : vents.asMap().values()) {
            var composition = entry.deviceComponents().resolve(manager);
            assertEquals(4, composition.network().orElseThrow().networkId());
            assertEquals(1621, composition.network().orElseThrow().receiveFrequency());
            assertTrue(composition.connection().orElseThrow().wired());
            assertFalse(composition.connection().orElseThrow().wireless());
            assertFalse(composition.network().orElseThrow().requiresPower());
            assertEquals(entry.kind()==com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData.Kind.VENT
                    ? "VNT-" : "SCR-", composition.network().orElseThrow().addressPrefix());
        }
        var airlock = manager.snapshot(ModAirlocks.TYPE).get(ModAirlocks.STANDARD);
        var airlockComposition = airlock.deviceComponents().resolve(manager);
        assertEquals(2, airlockComposition.network().orElseThrow().networkId());
        assertEquals(1280, airlockComposition.network().orElseThrow().receiveFrequency());
        var source = airlockComposition.sourceConfig().orElseThrow();
        assertTrue(airlockComposition.sources().stream().anyMatch(port -> port.id().equals("DoorStatus")));
        assertTrue(source.lastSignals().keySet().stream()
                .allMatch(portId -> airlockComposition.sources().stream().anyMatch(port -> port.id().equals(portId))));
        assertEquals(DeviceSignal.LOW, source.lastSignals().get("DoorStatus"));
    }
}
