package com.juicyslew.moonstation14.ms14.ghost_roles.server;

import com.juicyslew.moonstation14.ms14.ghost_roles.network.GhostRolePayloads;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.HandlerThread;

import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleGhostSessionControl;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleRoleAvailability;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessEntity;
import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Play transport registration. The catalog/controller is installed by the server runtime owner. */
public final class GhostRoleNetworking {
    private static volatile BiConsumer<CustomPacketPayload, IPayloadContext> serverHandler;
    private static volatile BiConsumer<CustomPacketPayload, IPayloadContext> clientHandler;

    private GhostRoleNetworking() { }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToClient(GhostRolePayloads.Snapshot.TYPE, GhostRolePayloads.Snapshot.STREAM_CODEC,
                        GhostRoleNetworking::receiveRegisteredClientPayload)
                .playToClient(GhostRolePayloads.Result.TYPE, GhostRolePayloads.Result.STREAM_CODEC,
                        GhostRoleNetworking::receiveRegisteredClientPayload)
                .playToServer(GhostRolePayloads.SnapshotRequest.TYPE, GhostRolePayloads.SnapshotRequest.STREAM_CODEC,
                        (payload, context) -> dispatch(payload, context, serverHandler))
                .playToServer(GhostRolePayloads.Request.TYPE, GhostRolePayloads.Request.STREAM_CODEC,
                        (payload, context) -> dispatch(payload, context, serverHandler));
    }

    public static void installServerHandler(BiConsumer<CustomPacketPayload, IPayloadContext> handler) {
        serverHandler = handler;
    }

    /** Authenticated current-level catalog, teleport, and prepared role-request endpoint. */
    public static void installDefaultServerHandler() {
        installServerHandler((payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var actor = LifecycleGhostSessionControl.committedActorSnapshot(player).orElse(null);
            if (actor == null) return;
            var credential = new GhostRolePayloads.Context(actor.ghost().getUUID(), actor.epoch());
            if (payload instanceof GhostRolePayloads.SnapshotRequest request) {
                if (!credential.equals(request.context())) return;
                GhostRoleNetworking.sendSnapshot(player, credential);
            } else if (payload instanceof GhostRolePayloads.Request request) {
                boolean success = false;
                String message = "Role action unavailable.";
                GhostRolePayloads.TeleportLook resultLook = null;
                boolean authenticated = credential.equals(request.context());
                if (authenticated && request.action() == GhostRolePayloads.Action.TELEPORT
                        && actor.ghost() instanceof GhostMobHarnessEntity ghost
                        && ghost.level() == player.serverLevel() && ghost.isAlive() && !ghost.isRemoved()) {
                    var level = player.serverLevel();
                    var target = level.getEntity(request.target());
                    var catalogEntry = target instanceof net.minecraft.world.entity.Mob candidate
                            ? GhostRoleCatalog.entryForTarget(player.getServer(), candidate, player.hasPermissions(2)) : null;
                    boolean catalogued = catalogEntry != null && catalogEntry.teleport();
                    if (catalogued && target instanceof LivingEntity living && !living.isRemoved()
                            && living.isAlive() && MobStateSystem.isAlive(living)
                            && living.level() == level && LifecycleRoleAvailability.unclaimed(player.getServer(), living)) {
                        GhostRoleTeleportGeometry.Look look = GhostRoleTeleportGeometry.compute(
                                living.getX(), living.getY(), living.getZ(), living.getYRot(),
                                living.getEyeY(), ghost.getEyeHeight());
                        Vec3 destination = look.position();
                        ghost.teleportTo(destination.x, destination.y, destination.z);
                        ghost.setYRot(look.yaw());
                        ghost.setXRot(look.pitch());
                        ghost.yRotO = look.yaw();
                        ghost.xRotO = look.pitch();
                        ghost.yBodyRot = look.yaw();
                        ghost.yHeadRot = look.yaw();
                        ghost.setDeltaMovement(Vec3.ZERO);
                        success = true;
                        resultLook = new GhostRolePayloads.TeleportLook(look.yaw(), look.pitch());
                        message = "Teleported to role target.";
                    }
                } else if (authenticated && request.action() == GhostRolePayloads.Action.REQUEST_ROLE) {
                    message = "Role request denied; target is unavailable.";
                    var canonical = LifecycleGhostSessionControl.canonicalGhost(player).orElse(null);
                    var level = player.serverLevel();
                    var target = level.getEntity(request.target());
                    var catalogEntry = target instanceof net.minecraft.world.entity.Mob candidate
                            ? GhostRoleCatalog.entryForTarget(player.getServer(), candidate, player.hasPermissions(2)) : null;
                    boolean catalogued = catalogEntry != null && catalogEntry.takeover();
                    if (canonical != null && canonical.epoch() == actor.epoch()
                            && canonical.ghost() == actor.ghost() && catalogued && target instanceof net.minecraft.world.entity.Mob mob
                            && !mob.isRemoved() && mob.isAlive() && MobStateSystem.isAlive(mob)
                            && mob.level() == level && LifecycleRoleAvailability.unclaimed(player.getServer(), mob)) {
                        success = catalogEntry.development()
                                ? player.hasPermissions(2) && LifecycleGhostSessionControl.offerDevelopmentTarget(player, mob)
                                : LifecycleGhostSessionControl.offerRoleTarget(player, mob);
                        message = success ? "Role requested; waiting for server control handoff."
                                : "Role request denied; target could not be prepared.";
                    }
                }
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new GhostRolePayloads.Result(request.context(), request.correlation(), success, message, resultLook));
            }
        });
    }

    public static void sendSnapshot(ServerPlayer player, GhostRolePayloads.Context context) {
        var level = player.serverLevel();
        var entries = GhostRoleCatalog.snapshot(player.getServer(), player.hasPermissions(2)).stream()
                .filter(entry -> level.getEntity(entry.target()) != null).toList();
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new GhostRolePayloads.Snapshot(context, entries));
    }

    public static void installClientHandler(BiConsumer<CustomPacketPayload, IPayloadContext> handler) {
        clientHandler = handler;
    }

    /** Shared payload-registration receive path, also used by client transport integration tests. */
    public static void receiveRegisteredClientPayload(CustomPacketPayload payload, IPayloadContext context) {
        dispatch(payload, context, clientHandler);
    }

    private static void dispatch(CustomPacketPayload payload, IPayloadContext context,
                                 BiConsumer<CustomPacketPayload, IPayloadContext> handler) {
        if (handler != null) handler.accept(payload, context);
    }
}
