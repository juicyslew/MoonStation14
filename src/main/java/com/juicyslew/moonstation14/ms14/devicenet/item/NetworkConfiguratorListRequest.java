package com.juicyslew.moonstation14.ms14.devicenet.item;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Body-hand authenticated list editor command. Membership identifiers are addresses, never entity IDs. */
public record NetworkConfiguratorListRequest(UUID session, long requestId, long epoch, long revision,
                                             String handId, String handToken, Operation operation,
                                             List<String> members, String member) {
    public enum Operation { SET, ADD, CLEAR, COPY, REMOVE, SHOW, QUERY, DONE }
    public NetworkConfiguratorListRequest {
        Objects.requireNonNull(session); Objects.requireNonNull(handId); Objects.requireNonNull(handToken);
        Objects.requireNonNull(operation); members = members == null ? List.of() : List.copyOf(members);
        if (requestId < 0 || epoch < 0 || revision < 0) throw new IllegalArgumentException("negative request counter");
    }
}
