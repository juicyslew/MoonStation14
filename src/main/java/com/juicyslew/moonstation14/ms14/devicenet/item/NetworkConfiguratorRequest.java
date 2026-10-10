package com.juicyslew.moonstation14.ms14.devicenet.item;

import com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity;
import java.util.Objects;
import java.util.UUID;

/** Server-bound configurator command. The hand proof is supplied by the authenticated body route. */
public record NetworkConfiguratorRequest(UUID session, long requestId, long epoch, long revision,
                                         String handId, String handToken, Operation operation,
                                         DeviceIdentity endpoint, String sourcePort, String sinkPort) {
    public enum Operation { SELECT_SOURCE, SELECT_SINK, TOGGLE_PAIR, CLEAR, LINK_DEFAULTS, DONE, QUERY }
    public NetworkConfiguratorRequest {
        Objects.requireNonNull(session); Objects.requireNonNull(handId); Objects.requireNonNull(handToken);
        Objects.requireNonNull(operation);
        if (requestId < 0 || epoch < 0 || revision < 0) throw new IllegalArgumentException("negative request counter");
    }
}
