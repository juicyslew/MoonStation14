package com.juicyslew.moonstation14.ms14.standing;

import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

public final class PostureNetworking {
    private PostureNetworking() { }
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN).playToServer(
                com.juicyslew.moonstation14.ms14.loose_objects.pulling.NativeBodyPullRequest.TYPE,
                com.juicyslew.moonstation14.ms14.loose_objects.pulling.NativeBodyPullRequest.STREAM_CODEC,
                (request, context) -> {
                    if (!(context.player() instanceof ServerPlayer player) || player.connection == null
                            || player.getServer() == null || player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return;
                    com.juicyslew.moonstation14.ms14.loose_objects.pulling.BodyPullService.toggleNative(player, request.target());
                });
        event.registrar("1").executesOn(HandlerThread.MAIN).playToServer(PostureToggleRequest.TYPE,
                PostureToggleRequest.STREAM_CODEC, (request, context) -> {
                    if (!(context.player() instanceof ServerPlayer player) || player.connection == null
                            || player.getServer() == null || player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return;
                    BodyActionAuthority.resolve(player).ifPresent(snapshot -> {
                        if (BodyActionAuthority.revalidate(player, snapshot)) PostureSystem.voluntaryToggle(snapshot.body());
                    });
                });
        event.registrar("1").executesOn(HandlerThread.MAIN).playToServer(PostureRollRequest.TYPE,
                PostureRollRequest.STREAM_CODEC, (request, context) -> {
                    if (!(context.player() instanceof ServerPlayer player) || player.connection == null
                            || player.getServer() == null || player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return;
                    var snapshot = BodyActionAuthority.resolve(player).orElse(null);
                    net.minecraft.world.entity.LivingEntity actor = snapshot != null && BodyActionAuthority.revalidate(player, snapshot)
                            ? snapshot.body() : null;
                    if (actor == null && !com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.isCarrier(player)
                            && !player.isSpectator()
                            && com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.resolveActor(player).isPresent()) actor = player;
                    if (actor == null) return;
                    if (request.target() == null) PostureSystem.voluntaryRoll(actor);
                    else {
                        if (!com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(actor)) return;
                        var target = player.serverLevel().getEntity(request.target());
                        if (!(target instanceof net.minecraft.world.entity.LivingEntity living) || living == actor
                                || living.level() != actor.level() || !aimsAtDownBody(actor, living)) return;
                        PostureSystem.turnOtherBody(actor, living);
                    }
                });
        event.registrar("1").executesOn(HandlerThread.MAIN).playToServer(PulledBodyMoveRequest.TYPE,
                PulledBodyMoveRequest.STREAM_CODEC, (request, context) -> {
                    if (!(context.player() instanceof ServerPlayer player) || player.connection == null
                            || player.getServer() == null || player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return;
                    BodyActionAuthority.resolve(player).ifPresent(snapshot -> {
                        if (BodyActionAuthority.revalidate(player, snapshot))
                            PostureSystem.onOwnCrawlInput(snapshot.body(), request.hasInput());
                        if (request.hasInput() && BodyActionAuthority.revalidate(player, snapshot))
                            com.juicyslew.moonstation14.ms14.loose_objects.pulling.BodyPullService
                                    .onMovementInput(snapshot.body(), true);
                    });
                });
    }

    private static boolean aimsAtDownBody(net.minecraft.world.entity.LivingEntity actor,
                                          net.minecraft.world.entity.LivingEntity target) {
        if (!PostureSystem.isDownPosture(target)) return false;
        var start = actor.getEyePosition();
        var end = start.add(actor.getLookAngle().scale(4.5));
        var point = BodyInteractionRay.hit(BodyInteractionBounds.of(target), start, end, 4.5, Double.POSITIVE_INFINITY);
        if (point.isEmpty()) return false;
        var block = actor.level().clip(new net.minecraft.world.level.ClipContext(start, point.get(),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, actor));
        return block.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }
}

