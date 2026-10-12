package com.juicyslew.moonstation14.ms14.devicenet.item.client;

/** Shared client-only lifecycle reset for retained configurator presentations. */
public final class NetworkConfiguratorClientState {
    private NetworkConfiguratorClientState() { }

    public static void clear() {
        NetworkConfiguratorLinkWindowState.INSTANCE.clear();
        NetworkConfiguratorListWindowState.INSTANCE.clear();
        NetworkConfiguratorClientReceiver.clear();
    }
}
