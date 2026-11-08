package com.juicyslew.moonstation14.ms14.doors.ui;

import net.minecraft.server.level.ServerPlayer;

/** Invokes the same registered wire request owner as the production payload handler. */
public final class AirlockWireRequestFixtureBridge {
    private AirlockWireRequestFixtureBridge() { }
    public static AirlockWireSnapshot request(ServerPlayer player, AirlockWireRequest request) {
        return AirlockWireNetworking.handleRequest(player, request);
    }
}
