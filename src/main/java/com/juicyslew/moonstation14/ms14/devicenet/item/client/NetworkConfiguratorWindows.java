package com.juicyslew.moonstation14.ms14.devicenet.item.client;

/** Shared visibility query for configurator windows that keep pointer mode open. */
public final class NetworkConfiguratorWindows {
    private NetworkConfiguratorWindows() { }
    public static boolean anyVisible() {
        return NetworkConfiguratorLinkWindowState.INSTANCE.window().visible()
                || NetworkConfiguratorListWindowState.INSTANCE.window().visible();
    }
}

