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
            var request = AirAlarmOverlay.buildIntent(4, SESSION, mode.ordinal(),
                    AirAlarmControlRequest.Operation.SET_ALARM_MODE, false, mode.ordinal(), 0, snapshot, device.id(), device.revision());
            assertEquals(23, request.expectedRevision());
            assertEquals(new UUID(0,0), request.deviceId());
            assertEquals(mode.ordinal(), request.enumIndex());
        }
        var toggle = AirAlarmOverlay.buildIntent(4, SESSION, 10, AirAlarmControlRequest.Operation.SET_AUTO_MODE,
                false, 0, 0, snapshot, device.id(), device.revision());
        assertEquals(23, toggle.expectedRevision());
        assertFalse(toggle.desired());
        var configIntent = AirAlarmOverlay.buildIntent(4, SESSION, 11,
                AirAlarmControlRequest.Operation.SET_ENABLED, false, 0, 0, snapshot, device.id(), device.revision());
        assertEquals(900, configIntent.expectedRevision());
        assertEquals(DEVICE, configIntent.deviceId());
    }

    @Test void overlaySnapshotAdmissionUsesSessionAndRequestOrdering() {
        var sent = new java.util.ArrayList<AirAlarmControlRequest>();
        var overlay = new AirAlarmOverlay(null, SESSION, 400, 300, () -> true, sent::add, id -> {}, () -> 1L, org.mockito.Mockito.mock(net.minecraft.client.gui.Font.class));
        var refresh = AirAlarmPaintLayout.forContent(304,224,0,0).refresh();
        overlay.mouseClicked(80+refresh.x()+1,75+refresh.y()+1,0);
        assertTrue(overlay.testView().pending());
        var accepted = new AirAlarmControlSnapshot(0, SESSION, sent.get(0).requestId(), true, true, AtmosAlarmSeverity.DANGER,
                AirAlarmMode.WIDE_FILTERING, false, 24, List.of(), List.of());
        overlay.snapshot(new AirAlarmControlSnapshot(0, UUID.randomUUID(), accepted.requestId(), true, true,
                AtmosAlarmSeverity.NORMAL, AirAlarmMode.FILTERING, true, 23, List.of(), List.of()));
        assertTrue(overlay.testView().pending());
        overlay.snapshot(accepted);
        assertFalse(overlay.testView().pending());
        assertEquals(AirAlarmMode.WIDE_FILTERING, overlay.testView().snapshot().currentMode());
        overlay.mouseClicked(80+refresh.x()+1,75+refresh.y()+1,0);
        var stale = new AirAlarmControlSnapshot(0, SESSION, accepted.requestId(), true, false, AtmosAlarmSeverity.NORMAL,
                AirAlarmMode.FILTERING, true, 23, List.of(), List.of());
        overlay.snapshot(stale);
        assertTrue(overlay.testView().pending());
        assertEquals(AirAlarmMode.WIDE_FILTERING, overlay.testView().snapshot().currentMode());
    }
}
