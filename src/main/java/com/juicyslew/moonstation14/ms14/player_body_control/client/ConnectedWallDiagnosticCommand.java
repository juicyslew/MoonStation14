package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** Default-off, client-local connected wall observation control. */
@OnlyIn(Dist.CLIENT)
public final class ConnectedWallDiagnosticCommand {
    private ConnectedWallDiagnosticCommand() { }
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(LiteralArgumentBuilder.<CommandSourceStack>literal("ms14_connectedwall")
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("prediction").then(LiteralArgumentBuilder.<CommandSourceStack>literal("start").executes(c -> {
                    boolean started = GhostControlClient.startConnectedPredictionFixture();
                    c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(started
                            ? "Connected prediction fixture started (visual outcome unknown)." : "Requires a grounded owned CHARACTER."), false);
                    return started ? 1 : 0;
                })).then(LiteralArgumentBuilder.<CommandSourceStack>literal("stop").executes(c -> {
                    GhostControlClient.stopConnectedPredictionFixture();
                    c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Connected prediction fixture stopped."), false);
                    return 1;
                })))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("arm").executes(c -> {
                    int count = GhostControlClient.armConnectedWallCapture();
                    c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(count == 0
                            ? "Connected-wall capture requires an owned CHARACTER session."
                            : "Connected-wall capture armed for at most " + count + " client ticks."), false);
                    return count == 0 ? 0 : 1;
                }))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("jump").executes(c -> {
                    boolean waiting = GhostControlClient.armConnectedJumpCapture();
                    c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(waiting
                            ? "Connected-jump capture waiting up to 200 client ticks for one live supported-frame motor jump; then 16 ticks, at most 64 rows. No animation or movement changes."
                            : "Connected-jump capture requires an owned CHARACTER session."), false);
                    return waiting ? 1 : 0;
                }))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("stop").executes(c -> {
                    int remaining = GhostControlClient.connectedWallCaptureRemaining();
                    GhostControlClient.stopConnectedWallCapture();
                    c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Connected-wall capture stopped; remaining window=" + remaining), false);
                    return 1;
                })));
    }
}
