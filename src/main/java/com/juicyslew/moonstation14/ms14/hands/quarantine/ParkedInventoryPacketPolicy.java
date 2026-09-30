package com.juicyslew.moonstation14.ms14.hands.quarantine;

/** Packet-only carrier barrier. A present but invalid marker must not become permission to mutate. */
public final class ParkedInventoryPacketPolicy {
    private ParkedInventoryPacketPolicy() { }

    public static boolean deny(boolean connectedRealPlayer, boolean markerPresent, boolean ownedPark) {
        return connectedRealPlayer && (ownedPark || markerPresent);
    }
}
