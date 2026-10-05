package com.juicyslew.moonstation14.ms14.power.runtime;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class AirAlarmPowerTest {
    private static final PowerLiveLoop.AlarmLoad ALARM = new PowerLiveLoop.AlarmLoad("alarm", 3, true, 100);

    @Test void alarmIsNotAFreePoweredStatusWithoutMatchingSupply() {
        var noApc = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(ALARM), 20));
        assertEquals(0, noApc.alarmWatts().get("alarm"));
        var wrongApc = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(), List.of(),
                List.of(new PowerLiveLoop.Apc("apc", 2, true, 4, true, true, 10_000)),
                List.of(), List.of(), List.of(ALARM), 20));
        assertEquals(0, wrongApc.alarmWatts().get("alarm"));
        var open = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(), List.of(),
                List.of(new PowerLiveLoop.Apc("apc", 2, true, 3, true, false, 10_000)),
                List.of(), List.of(), List.of(ALARM), 20));
        assertEquals(0, open.alarmWatts().get("alarm"));
        var unknown = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(), List.of(),
                List.of(new PowerLiveLoop.Apc("apc", 2, true, 3, true, true, 10_000)),
                List.of(), List.of(), List.of(new PowerLiveLoop.AlarmLoad("alarm", 3, false, 100)), 20));
        assertEquals(0, unknown.alarmWatts().get("alarm"));
        assertThrows(IllegalArgumentException.class, () -> new PowerLiveLoop.Input(List.of(), List.of(), List.of(),
                List.of(), List.of(PowerLiveLoop.VentLoad.defaultUnaryAtmos("alarm", 3, true)), List.of(ALARM), 20));
    }

    @Test void alarmCompetesWithLampsAndVentsAndBillsBatteryOnce() {
        var apc = List.of(new PowerLiveLoop.Apc("apc", 2, true, 3, true, true, 10_000));
        var result = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(), List.of(), apc,
                List.of(new PowerLiveLoop.Lamp("lamp", 3, true)),
                List.of(PowerLiveLoop.VentLoad.defaultUnaryAtmos("vent", 3, true)), List.of(ALARM), 20));
        assertEquals(100, result.alarmWatts().get("alarm"), 1e-9);
        assertEquals(100, result.lampWatts().get("lamp"), 1e-9);
        assertEquals(5, result.ventWatts().get("vent"), 1e-9);
        assertEquals(205, result.apcOutputWatts().get("apc"), 1e-9);
        assertEquals(9_795, result.batteryEnergyJoules().get("apc"), 1e-9);
        var scarce = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(), List.of(),
                List.of(new PowerLiveLoop.Apc("apc", 2, true, 3, true, true, 50)),
                List.of(new PowerLiveLoop.Lamp("lamp", 3, true)), List.of(), List.of(ALARM), 20));
        assertTrue(scarce.alarmWatts().get("alarm") < 100);
        assertEquals(50, scarce.apcOutputWatts().get("apc"), 1e-9);
        assertEquals(0, scarce.batteryEnergyJoules().get("apc"), 1e-9);
    }
}
