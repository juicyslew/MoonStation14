package com.juicyslew.moonstation14.ms14.hands.network;

import net.minecraft.server.level.ServerPlayer;

/** Test-package bridge to the real authoritative block-use dispatch, with no fake admission path. */
public final class BodyBlockUseGameTestAccess {
    private BodyBlockUseGameTestAccess() { }

    public static BodyBlockUseResult.Outcome dispatch(ServerPlayer actor, BodyBlockUseRequest request) {
        return BodyHandActionNetworking.handleBlockUse(actor, request);
    }
}
