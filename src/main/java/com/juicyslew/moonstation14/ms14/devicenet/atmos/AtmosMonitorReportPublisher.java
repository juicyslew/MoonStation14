package com.juicyslew.moonstation14.ms14.devicenet.atmos;

import com.juicyslew.moonstation14.ms14.devicenet.DeviceEndpoint;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkPacket;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkTransport;
import com.juicyslew.moonstation14.ms14.devicenet.DevicePayload;
import java.util.Collection;

/** Sends current sensor state to transient subscriber addresses through the canonical deferred transport. */
public final class AtmosMonitorReportPublisher {
    private AtmosMonitorReportPublisher() { }
    public static void publish(DeviceNetworkTransport transport, DeviceEndpoint sender,
                               Collection<String> addresses, DevicePayload payload) {
        if (transport == null || sender == null || addresses == null || payload == null || !sender.available()) return;
        var source = sender.composition().network().orElse(null);
        if (source == null || !source.transmit() || source.address().isBlank() || source.transmitFrequency() < 1) return;
        for (String address : addresses) if (address != null && !address.isBlank())
            transport.enqueue(new DeviceNetworkPacket(sender.identity(), source.networkId(), source.transmitFrequency(),
                    source.address(), address, false, payload, source.networkId(), source.transmitFrequency()));
    }
}
