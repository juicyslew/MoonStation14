package com.juicyslew.moonstation14.ms14.devicenet.item;

import java.util.List;
import java.util.UUID;

/** Authoritative list-editor state; show controls client visualization only. */
public record NetworkConfiguratorListSnapshot(UUID session, long requestId, long handRevision, long epoch, boolean open,
                                               boolean show, String targetAddress, List<String> members,
                                               List<NetworkConfiguratorStackData.StagedDevice> staged,
                                               List<NetworkConfiguratorStackData.StagedDevice> memberDisplayRows,
                                               List<MemberPosition> positions) {
    public record MemberPosition(UUID identity, String dimension, int x, int y, int z) { }
    public NetworkConfiguratorListSnapshot {
        targetAddress = targetAddress == null ? "" : targetAddress;
        members = List.copyOf(members); staged = List.copyOf(staged);
        memberDisplayRows = List.copyOf(memberDisplayRows); positions = List.copyOf(positions);
    }
}

