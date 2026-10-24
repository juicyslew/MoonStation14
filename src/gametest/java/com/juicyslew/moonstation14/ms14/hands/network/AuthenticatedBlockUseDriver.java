package com.juicyslew.moonstation14.ms14.hands.network;

import net.minecraft.server.level.ServerPlayer;

/** Shared GameTest bridge to the production authenticated body block-use handler. */
public final class AuthenticatedBlockUseDriver {
    private AuthenticatedBlockUseDriver() { }

    public static BodyBlockUseResult.Outcome use(ServerPlayer actor, BodyBlockUseRequest request) {
        return BodyHandActionNetworking.handleBlockUse(actor, request);
    }
}
