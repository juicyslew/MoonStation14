package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.client;

import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmMode;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmControlRequest;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmControlSnapshot;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.atmos.monitor.AtmosAlarmSeverity;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AirAlarmScreenTest {
    private static final UUID SESSION = UUID.randomUUID(), DEVICE = UUID.randomUUID();

    @Test void controllerIntentsUseControllerRevisionAndDoNotDependOnDeviceRevision() {
        var snapshot = new AirAlarmControlSnapshot(4, SESSION, 8, true, true, AtmosAlarmSeverity.WARNING,
                AirAlarmMode.FILL, true, 23, List.of(), List.of());
        var device = new AirAlarmControlSnapshot.Device(DEVICE, VentilationDeviceConfig.Kind.VENT,
                new BlockPos(1,2,3), true, true, true, 0, 0, 900, 0, 0);
        for (var mode : AirAlarmMode.values()) {
            var request = AirAlarmScreen.buildIntent(4, SESSION, mode.ordinal(),
                    AirAlarmControlRequest.Operation.SET_ALARM_MODE, false, mode.ordinal(), 0, snapshot, device);
            assertEquals(23, request.expectedRevision());
            assertEquals(new UUID(0,0), request.deviceId());
            assertEquals(mode.ordinal(), request.enumIndex());
        }
        var toggle = AirAlarmScreen.buildIntent(4, SESSION, 10, AirAlarmControlRequest.Operation.SET_AUTO_MODE,
                false, 0, 0, snapshot, device);
        assertEquals(23, toggle.expectedRevision());
        assertFalse(toggle.desired());
        var configIntent = AirAlarmScreen.buildIntent(4, SESSION, 11,
                AirAlarmControlRequest.Operation.SET_ENABLED, false, 0, 0, snapshot, device);
        assertEquals(900, configIntent.expectedRevision());
        assertEquals(DEVICE, configIntent.deviceId());
    }

    @Test void onlyCorrelatedPendingAcceptedOrRejectedSnapshotsCanReplaceState() {
        var accepted = new AirAlarmControlSnapshot(4, SESSION, 9, true, true, AtmosAlarmSeverity.DANGER,
                AirAlarmMode.WIDE_FILTERING, false, 24, List.of(), List.of());
        var rejected = new AirAlarmControlSnapshot(4, SESSION, 10, false, false, AtmosAlarmSeverity.NORMAL,
                AirAlarmMode.FILTERING, true, 23, List.of(), List.of());
        assertTrue(AirAlarmScreen.correlatedResponse(4, SESSION, 9, -1, -1, accepted));
        assertTrue(AirAlarmScreen.correlatedResponse(4, SESSION, 10, -1, -1, rejected));
        assertFalse(AirAlarmScreen.correlatedResponse(5, SESSION, 9, -1, -1, accepted));
        assertFalse(AirAlarmScreen.correlatedResponse(4, UUID.randomUUID(), 9, -1, -1, accepted));
        assertFalse(AirAlarmScreen.correlatedResponse(4, SESSION, 8, -1, -1, accepted));
    }
}
