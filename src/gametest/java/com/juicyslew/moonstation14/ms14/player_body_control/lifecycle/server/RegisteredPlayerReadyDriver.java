package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

/** Shared test-only driver for the registered player's real lifecycle Begin/Ready owner and hand query. */
public final class RegisteredPlayerReadyDriver {
    private RegisteredPlayerReadyDriver() { }

    /** Drives an already admitted public lifecycle intent through the real production post-tick owner. */
    public static void submitAcceptedIntent(RegisteredServerPlayerFixture fixture,
            com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Intent intent) {
        LifecycleCharacterSessionControl.onPayload(intent, fixture.player());
        LifecycleCharacterSessionControl.playerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(fixture.player()));
    }

    public static void admitIntent(RegisteredServerPlayerFixture fixture, GhostControlPayloads.Intent intent) {
        LifecycleCharacterSessionControl.onPayload(intent, fixture.player());
    }

    public static void authoritativeTick(RegisteredServerPlayerFixture fixture) {
        LifecycleCharacterSessionControl.playerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(fixture.player()));
    }

    /** Re-enters the public post-tick owner without another payload, to verify a consumed fixture input is not replayed. */
    public static void tickWithoutNewIntent(RegisteredServerPlayerFixture fixture) {
        LifecycleCharacterSessionControl.playerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(fixture.player()));
    }
    public record Active(LivingEntity body, long epoch, BodyHandStateSnapshot initialHands) { }

    public static Active readyAndQuery(RegisteredServerPlayerFixture fixture) {
        GhostControlPayloads.Begin begin = null;
        var outboundKinds = new java.util.ArrayList<String>();
        Object outbound;
        while ((outbound = fixture.pollOutbound()) != null) {
            if (outbound instanceof ClientboundCustomPayloadPacket packet) {
                var payload = packet.payload();
                if (payload instanceof GhostControlPayloads.Begin captured) {
                    outboundKinds.add("Begin[" + captured.harnessKind() + ",epoch=" + captured.mindEpoch() + "]");
                    if (begin == null && captured.harnessKind() == MobHarnessKind.CHARACTER) {
                        begin = captured;
                        break;
                    }
                } else {
                    outboundKinds.add(payload.getClass().getSimpleName());
                }
            } else {
                outboundKinds.add(outbound.getClass().getSimpleName());
            }
        }
        if (begin == null) throw new IllegalStateException(noCharacterBeginDiagnostic(fixture, outboundKinds));
        LifecycleCharacterSessionControl.onPayload(new GhostControlPayloads.Ready(begin.mindEpoch()), fixture.player());
        var active = LifecycleCharacterSessionControl.activeCharacterBody(fixture.player())
                .orElseThrow(() -> new IllegalStateException("normal Ready did not establish active body"));
        BodyHandStateSnapshot snapshot = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(active.epoch(), 1));
        if (snapshot.reason() != BodyHandStateSnapshot.Reason.OK)
            throw new IllegalStateException("full hand query denied after Ready: " + snapshot.reason());
        BodyHandStateService.requireFreshQuery(fixture.player());
        return new Active(active.body(), active.epoch(), snapshot);
    }

    private static String noCharacterBeginDiagnostic(RegisteredServerPlayerFixture fixture, java.util.List<String> outboundKinds) {
        var player = fixture.player();
        var server = player.getServer();
        var startup = server == null ? null : LifecycleStartupRuntime.snapshot(server).orElse(null);
        var context = server == null ? null : LifecycleStartupRuntime.contextFor(server).orElse(null);
        boolean reserved = context != null && context.hasReservedClaim(player.getUUID());
        boolean createdHere = context != null && context.wasCreatedOnThisServer(player.getUUID());
        String profile;
        try {
            profile = context == null ? "no-context" : context.currentAccountProfile(player.getUUID())
                    .map(saved -> saved.state() + "/revision=" + saved.revision()).orElse("no-current-profile");
        } catch (Exception failure) {
            profile = "profile-read-failed:" + failure.getClass().getSimpleName();
        }
        boolean active = LifecycleCharacterSessionControl.activeCharacterBody(player).isPresent();
        // GameTest failures are serialized into a WritableBook page (1024 chars max).
        // Keep the actionable lifecycle state, but never append the unbounded outbound packet list.
        return "no CHARACTER Begin; account=" + player.getUUID()
                + " listed=" + (server != null && server.getPlayerList().getPlayer(player.getUUID()) == player)
                + " removed=" + player.isRemoved() + " connectionAccepting=" + (player.connection != null
                    && player.connection.isAcceptingMessages())
                + " startup=" + (startup == null ? "none" : startup.state()) + " reserved=" + reserved + " createdHere=" + createdHere
                + " profile=" + profile + " activeCharacter=" + active + " outboundKinds=" + outboundKinds.size();
    }

    /** Drives the same current-epoch GHOST Begin/Ready lifecycle transition used by the client. */
    public static boolean readyGhost(RegisteredServerPlayerFixture fixture, long epoch) {
        Object outbound;
        boolean beginSeen = false;
        while ((outbound = fixture.pollOutbound()) != null) {
            if (outbound instanceof ClientboundCustomPayloadPacket packet
                    && packet.payload() instanceof GhostControlPayloads.Begin begin
                    && begin.harnessKind() == MobHarnessKind.GHOST && begin.mindEpoch() == epoch) {
                beginSeen = true;
                break;
            }
        }
        if (!beginSeen) return false;
        LifecycleGhostSessionControl.onPayload(new GhostControlPayloads.Ready(epoch), fixture.player());
        return LifecycleGhostSessionControl.committedActorSnapshot(fixture.player())
                .filter(actor -> actor.epoch() == epoch).isPresent();
    }

    /** Completes the real role-adoption OfferReady phase against its emitted target id/epoch. */
    public static void acceptRoleOffer(RegisteredServerPlayerFixture fixture,
                                       GhostControlPayloads.Offer offer) {
        LifecycleGhostSessionControl.onPayload(new GhostControlPayloads.OfferReady(
                offer.currentEpoch(), offer.targetEntityId(), offer.targetKind()), fixture.player());
    }

    /** Aims at a real outline hit and returns its actual face for an ordinary nominated block request. */
    public static BlockHitResult aimAt(LivingEntity body, ServerLevel level, BlockPos target) {
        Vec3 eye = body.getEyePosition();
        var shape = level.getBlockState(target).getShape(level, target);
        var bounds = shape.bounds();
        if (shape.isEmpty()) throw new IllegalArgumentException("fixture target has no outline shape: " + target);
        BlockHitResult lastHit = null;
        Vec3 lastAim = null;
        for (double fx : new double[]{.2, .5, .8}) for (double fy : new double[]{.2, .5, .8})
            for (double fz : new double[]{.2, .5, .8}) {
                Vec3 aim = new Vec3(target.getX() + bounds.minX + (bounds.maxX - bounds.minX) * fx,
                        target.getY() + bounds.minY + (bounds.maxY - bounds.minY) * fy,
                        target.getZ() + bounds.minZ + (bounds.maxZ - bounds.minZ) * fz);
                Vec3 delta = aim.subtract(eye);
                double distance = delta.length();
                if (!(distance > 0) || distance > com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe.MAX_REACH)
                    continue;
                Vec3 direction = delta.scale(1.0 / distance);
                body.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
                body.setXRot((float) -Math.toDegrees(Math.asin(direction.y)));
                Vec3 end = eye.add(body.getLookAngle().scale(
                        com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe.MAX_REACH));
                var hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, body));
                if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
                    lastHit = blockHit;
                    lastAim = aim;
                    if (blockHit.getBlockPos().equals(target)) return blockHit;
                }
            }
        throw new IllegalStateException("aimed body ray missed fixture target=" + target + " eye=" + eye
                + " candidate=" + lastAim + " hit=" + (lastHit == null ? "MISS" : lastHit.getBlockPos()
                + "/" + lastHit.getDirection() + "@" + lastHit.getLocation()));
    }
}
