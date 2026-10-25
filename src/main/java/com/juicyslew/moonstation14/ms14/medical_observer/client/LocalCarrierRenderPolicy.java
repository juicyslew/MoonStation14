package com.juicyslew.moonstation14.ms14.medical_observer.client;

import com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads;

import java.util.UUID;

/** Pure policy for the native local-player model while a distinct observer body is presented. */
public final class LocalCarrierRenderPolicy {
    private LocalCarrierRenderPolicy() { }

    public static boolean hide(boolean localPlayer, ObserverPayloads.Mode mode, UUID carrier, UUID body) {
        return localPlayer && (mode == ObserverPayloads.Mode.CORPSE || mode == ObserverPayloads.Mode.GHOST)
                && carrier != null && body != null && !carrier.equals(body);
    }
}

