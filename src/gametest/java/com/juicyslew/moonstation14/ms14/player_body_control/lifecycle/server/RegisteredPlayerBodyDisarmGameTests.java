package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.combat.*;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the authenticated request path against another registered player's actual character body. */
@GameTestTags({"melee-disarm", "registered-melee"})
@PrefixGameTestTemplate(false)
public final class RegisteredPlayerBodyDisarmGameTests {
    private RegisteredPlayerBodyDisarmGameTests() { }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", timeoutTicks=100)
    public static void registeredPlayerCanDisarmOtherRegisteredPlayerBody(GameTestHelper h) {
        var actorFixture = RegisteredServerPlayerFixture.register(h.getLevel(), "player-disarm-actor");
        var peerFixture = RegisteredServerPlayerFixture.register(h.getLevel(), "player-disarm-peer");
        var bodies = new net.minecraft.world.entity.LivingEntity[2];
        Runnable cleanup = () -> {
            for (var body : bodies) if (body != null && !body.isRemoved()) body.discard();
            actorFixture.close(); peerFixture.close();
        };
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void clean() { cleanup.run(); }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { clean(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { clean(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo o, net.minecraft.gametest.framework.GameTestInfo n, net.minecraft.gametest.framework.GameTestRunner r) { clean(); }
        });
        try {
            h.runAtTickTime(2, () -> {
                try {
                    var actor = RegisteredPlayerReadyDriver.readyAndQuery(actorFixture);
                    var peer = RegisteredPlayerReadyDriver.readyAndQuery(peerFixture);
                    bodies[0] = actor.body(); bodies[1] = peer.body();
                    var floor = h.absolutePos(new net.minecraft.core.BlockPos(3, 2, 3));
                    actor.body().teleportTo(floor.getX()+.5, floor.getY(), floor.getZ()+.5);
                    peer.body().teleportTo(floor.getX()+.5, floor.getY(), floor.getZ()+1.4);
                    for (var body : bodies) { body.setYRot(0); body.setXRot(0); body.setDeltaMovement(Vec3.ZERO); body.tick(); }
                    var actorHands = actor.body().getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                    var peerHands = peer.body().getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                    if (actorHands == null || !actorHands.compatible(actor.body()) || peerHands == null || !peerHands.compatible(peer.body()))
                        throw new GameTestAssertException("registered bodies lack compatible LiveHands; actor="+actorHands+" peer="+peerHands);
                    if (com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.resolveActor(actor.body()).isEmpty())
                        throw new GameTestAssertException("registered attacker character body was not resolved as an actor");
                    if (!com.juicyslew.moonstation14.ms14.combat.DisarmPolicy.canTarget(true, false))
                        throw new GameTestAssertException("LiveHands-only target eligibility gate denied peer body");
                    if (com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.isCarrier(peer.body()))
                        throw new GameTestAssertException("target UUID resolves to a carrier rather than peer body");
                    var selection = MeleeWeaponResolver.resolve(actor.body()).orElseThrow(() -> new GameTestAssertException("actor has no melee binding"));
                    var mode = com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.resolveActor(actor.body())
                            .flatMap(character -> character.component(com.juicyslew.moonstation14.ms14.character.components.CombatModeComponent.class))
                            .orElseThrow(() -> new GameTestAssertException("attacker body has no resolved character prototype"));
                    if (!mode.canDisarm().orElse(false)) throw new GameTestAssertException("attacker normal human prototype does not permit disarm");
                    CombatModeRequestService.handle(actorFixture.player(), new CombatModeRequest(actor.epoch(), 1, true));
                    var request = new MeleeAttackRequest(actor.epoch(), 1, selection.kind(), selection.handRevision(), selection.activeHand(), selection.itemToken().orElse(""), MeleeAttackRequest.Mode.DISARM, peer.body().getUUID(), java.util.List.of());
                    var reply = MeleeAttackRequestService.handle(actorFixture.player(), request);
                    if ((reply.status() != MeleeAttackReply.Status.DISARM_FAILED && reply.status() != MeleeAttackReply.Status.DISARM_HANDLED) || !reply.cooldownConsumed()) {
                        var eye=actor.body().getEyePosition(); var look=actor.body().getLookAngle();
                        throw new GameTestAssertException("actual peer-body disarm rejected: status="+reply.status()+" consumed="+reply.cooldownConsumed()+" actorOwner="+actorFixture.player().getUUID()+" actorBody="+actor.body().getUUID()+" peerOwner="+peerFixture.player().getUUID()+" peerBody="+peer.body().getUUID()+" targetBox="+peer.body().getBoundingBox()+" eye="+eye+" look="+look+" distance="+actor.body().distanceTo(peer.body())+" actorHandRevision="+actorHands.revision()+" peerHandRevision="+peerHands.revision());
                    }
                    h.succeed();
                } catch (RuntimeException | Error e) { cleanup.run(); throw e; }
            });
        } catch (RuntimeException | Error e) { cleanup.run(); throw e; }
    }
}
