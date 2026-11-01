package com.juicyslew.moonstation14.ms14.atmos.monitor;

import com.juicyslew.moonstation14.ms14.devicenet.DeviceEndpoint;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceLinkService;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceLinkGraph;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal;

/** Maps alarm severity transitions to the existing addressed High/Low device-link ports. */
public final class AtmosAlarmPortSignals {
    private AtmosAlarmPortSignals() { }
    public static void emit(DeviceLinkService links, DeviceLinkGraph graph, DeviceEndpoint endpoint, AtmosAlarmSeverity severity) {
        if (links == null || graph == null || endpoint == null || severity == null) return;
        String active = switch (severity) {
            case DANGER -> "AirDanger";
            case WARNING -> "AirWarning";
            case NORMAL -> "AirNormal";
        };
        for (var port : endpoint.composition().sources()) links.invoke(graph, endpoint, port.id(),
                port.id().equals(active) ? DeviceSignal.HIGH : DeviceSignal.LOW);
    }
}
