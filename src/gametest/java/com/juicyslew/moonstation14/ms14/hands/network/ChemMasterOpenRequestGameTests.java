package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterBlockEntity;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterControlRequest;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterSessionService;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterWindowUpdate;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.fixture.ChemMasterFixtureService;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Registered-player normal block-use admission and ChemMaster workspace request evidence. */
@PrefixGameTestTemplate(false)
@GameTestTags({"feature-readiness"})
public final class ChemMasterOpenRequestGameTests {
    private ChemMasterOpenRequestGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 600)
    public static void ordinaryAdmittedUseOpensAndRefreshesChemMasterWorkspace(GameTestHelper helper) {
        RegisteredServerPlayerFixture fixture = RegisteredServerPlayerFixture.register(
                helper.getLevel(), "readiness-chem-open");
        helper.runAtTickTime(2, () -> {
            try {
                var owner = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                ServerLevel level = (ServerLevel) helper.getLevel();
                BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 2));
                require(ChemMasterFixtureService.build(level, origin).startsWith("Built open-air"),
                        "reuse existing real powered ChemMaster fixture builder");
                BlockPos machinePos = origin.offset(3, 1, 8);
                PowerFixtureReadiness.await(helper, level, 400,
                        () -> level.getBlockEntity(machinePos) instanceof ChemMasterBlockEntity machine
                                && PowerRuntime.hasFullFreshChemMasterPower(level, machine, machine.placementIdentity()),
                        () -> "ChemMaster is waiting for its existing fixture's resolved fresh power", () -> {
                            try {
                                var body = owner.body();
                                // Stand on the fixture APC's top, south of the machine. This is
                                // inside the existing loaded approach and keeps the service wall
                                // and intervening APC out of the actual body-to-machine ray.
                                 // Stay on the fixture's clear south approach but within the
                                 // resolved ChemMaster's two-block body interaction range.
                                 body.setPos(machinePos.getX() + 0.5, machinePos.getY() + 1, machinePos.getZ() - 1.25);
                                 var bodyHit = RegisteredPlayerReadyDriver.aimAt(body, level, machinePos);
                                 require(bodyHit.getBlockPos().equals(machinePos)
                                                 && level.getBlockEntity(bodyHit.getBlockPos()) instanceof ChemMasterBlockEntity,
                                         "ordinary admitted target is the actual ChemMaster root: " + bodyHit.getBlockPos());
                                 var targetProbe = BodyBlockUseTargetProbe.probe(level, body, bodyHit.getBlockPos(), bodyHit.getDirection());
                                 require(targetProbe != null, "normal block-use target probe admits actual eye ray hit="
                                         + bodyHit.getBlockPos() + "/" + bodyHit.getDirection() + "@" + bodyHit.getLocation()
                                         + " eye=" + body.getEyePosition() + " look=" + body.getLookAngle());
                                 var complexAuthority = com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority
                                         .resolve(fixture.player()).orElseThrow(() -> new GameTestAssertException(
                                                 "full body action authority unavailable before ordinary block request"));
                                 var machine = (ChemMasterBlockEntity) level.getBlockEntity(machinePos);
                                 require(com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority
                                                 .eligibleForComplexInteraction(fixture.player(), complexAuthority)
                                                 && machine.isLoadedValid() && body.distanceToSqr(
                                                 net.minecraft.world.phys.Vec3.atCenterOf(machinePos))
                                                 <= machine.prototype().uiRange() * machine.prototype().uiRange(),
                                         "workspace entry preconditions: eligible="
                                                 + com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority
                                                 .eligibleForComplexInteraction(fixture.player(), complexAuthority)
                                                 + " complexEnabled=" + complexAuthority.identity().complexInteractionEnabled()
                                                 + " stunned=" + com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.isStunned(body)
                                                 + " machineValid=" + machine.isLoadedValid() + " range="
                                                 + body.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(machinePos))
                                                 + "/" + machine.prototype().uiRange());
                                  var hands = body.getExistingDataOrNull(
                                         com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                                  require(hands != null && hands.compatible(body), "normal lifecycle hands initialized");
                                  var handIds = hands.handIds();
                                  require(handIds.size() == 2, "fixture resolves the two ordinary hands");
                                  var firstItem = spawnPickupItem(level, body, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
                                  aimAtItem(body, firstItem);
                                  require(com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.pickupSelected(
                                          fixture.player(), firstItem.getUUID(), handIds.get(0), hands.revision(), ignored -> {})
                                          == com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS,
                                          "first incompatible ordinary item enters the first hand through owner pickup transaction");
                                  BodyHandStateSnapshot heldOnce = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(owner.epoch(), 3));
                                  require(heldOnce.reason() == BodyHandStateSnapshot.Reason.OK, "fresh complete proof after first pickup");
                                  var liveHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                                  var secondItem = spawnPickupItem(level, body, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER));
                                  aimAtItem(body, secondItem);
                                  require(com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.pickupSelected(
                                          fixture.player(), secondItem.getUUID(), handIds.get(1), liveHands.revision(), ignored -> {})
                                          == com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS,
                                          "second incompatible item enters the other hand through owner pickup transaction");
                                  BodyHandStateSnapshot fresh = BodyHandStateService.query(fixture.player(),
                                          new BodyHandStateQuery(owner.epoch(), 4));
                                  require(fresh.reason() == BodyHandStateSnapshot.Reason.OK
                                                  && fresh.hands().stream().allMatch(hand -> hand.token() != null),
                                          "fresh full proof confirms both hands occupied before explicit activation");
                                  bodyHit = RegisteredPlayerReadyDriver.aimAt(body, level, machinePos);
                                  BodyBlockUseRequest request = new BodyBlockUseRequest(owner.epoch(), 1,
                                          fresh.revision(), fresh.activeHand(), fresh.hands().stream()
                                                  .filter(hand -> hand.id().equals(fresh.activeHand())).findFirst().orElseThrow().token(),
                                          bodyHit.getBlockPos(), bodyHit.getDirection(), false,
                                          BodyBlockUseRequest.Intent.ACTIVATE_WORLD);
                                  BodyBlockUseResult.Outcome outcome = BodyHandActionNetworking.handleBlockUse(fixture.player(), request);
                                 require(outcome == BodyBlockUseResult.Outcome.NO_CHANGE,
                                          "full-hands explicit activation owner outcome=" + outcome + " target=" + bodyHit.getBlockPos()
                                                 + "/" + bodyHit.getDirection() + " revision=" + hands.revision()
                                                 + " active=" + fresh.activeHand() + " actorMenu="
                                                 + (fixture.player().containerMenu == fixture.player().inventoryMenu) + " power="
                                                 + PowerRuntime.hasFullFreshChemMasterPower(level,
                                                 (ChemMasterBlockEntity) level.getBlockEntity(machinePos),
                                                 ((ChemMasterBlockEntity) level.getBlockEntity(machinePos)).placementIdentity()));
                                  ChemMasterWindowUpdate opened = takeWindowUpdate(fixture);
                                require(opened != null && opened.state().open()
                                                && opened.state().bodyId().equals(body.getUUID())
                                                && opened.state().bodyEpoch() == owner.epoch(),
                                         "outbound authoritative workspace state is bound to current controlled body and epoch");

                                 var activeHeld = fresh.hands().stream().filter(hand -> hand.id().equals(fresh.activeHand())).findFirst().orElseThrow();
                                 var slotBeforeUse = ((ChemMasterBlockEntity) level.getBlockEntity(machinePos)).inputSlot().snapshot();
                                 BodyBlockUseResult.Outcome ordinaryHeld = BodyHandActionNetworking.handleBlockUse(fixture.player(),
                                         new BodyBlockUseRequest(owner.epoch(), 2, fresh.revision(), fresh.activeHand(), activeHeld.token(),
                                                 bodyHit.getBlockPos(), bodyHit.getDirection(), false, BodyBlockUseRequest.Intent.USE));
                                 require(ordinaryHeld == BodyBlockUseResult.Outcome.DENIED
                                                 && ((ChemMasterBlockEntity) level.getBlockEntity(machinePos)).inputSlot().snapshot().equals(slotBeforeUse),
                                         "ordinary incompatible held USE neither opens nor mutates ChemMaster input");
                                 require(takeOptionalWindowUpdate(fixture) == null,
                                         "ordinary incompatible held USE does not create a second workspace update");

                                ChemMasterSessionService.handle(fixture.player(), new ChemMasterControlRequest(
                                        opened.state().sessionId(), opened.state().machineId(), opened.state().dimension(),
                                        new BlockPos(opened.state().x(), opened.state().y(), opened.state().z()),
                                        1, opened.state().stateRevision(), opened.state().slotRevision(),
                                        ChemMasterControlRequest.Operation.QUERY, opened.state().mode(), opened.state().sort(),
                                        java.util.Optional.empty(), 0));
                                ChemMasterWindowUpdate refreshed = takeWindowUpdate(fixture);
                                 require(refreshed != null && refreshed.state().open()
                                                 && refreshed.state().sessionId().equals(opened.state().sessionId())
                                                 && refreshed.state().frame() > opened.state().frame(),
                                         "normal session query emits a retained refreshed workspace state");
                                 require(com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.applyStun(body, 20)
                                                 && com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.isStunned(body),
                                         "resolved live human accepts real finite stun status");
                                 helper.runAfterDelay(2, () -> {
                                     try {
                                         require(com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.isStunned(body)
                                                         && !com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(body),
                                                 "stun remains action-blocking after ordinary server ticks");
                                         var stunnedQuery = request(refreshed.state(), 2,
                                                 ChemMasterControlRequest.Operation.QUERY, refreshed.state().mode());
                                         ChemMasterSessionService.handle(fixture.player(), stunnedQuery);
                                         ChemMasterWindowUpdate retained = takeWindowUpdate(fixture);
                                         require(retained.state().open()
                                                         && retained.state().sessionId().equals(opened.state().sessionId())
                                                         && retained.state().acknowledgedRequestId() == 2
                                                         && retained.state().closeReason().isEmpty(),
                                                 "server ticks and QUERY retain the same session while stunned");

                                         var unchangedMode = retained.state().mode();
                                         var requestedMode = unchangedMode == ChemMasterControlRequest.Mode.TRANSFER
                                                 ? ChemMasterControlRequest.Mode.DISCARD : ChemMasterControlRequest.Mode.TRANSFER;
                                         ChemMasterSessionService.handle(fixture.player(), request(retained.state(), 3,
                                                 ChemMasterControlRequest.Operation.SET_MODE, requestedMode));
                                         ChemMasterWindowUpdate denied = takeWindowUpdate(fixture);
                                         require(denied.state().open()
                                                         && denied.state().sessionId().equals(opened.state().sessionId())
                                                         && denied.state().acknowledgedRequestId() == 3
                                                         && denied.state().rejectionReason().filter("action blocked"::equals).isPresent()
                                                         && denied.state().mode() == unchangedMode
                                                         && denied.state().stateRevision() == retained.state().stateRevision()
                                                         && denied.state().slotRevision() == retained.state().slotRevision(),
                                                 "stunned SET_MODE is correlated/rejected without closing or mutating: "
                                                         + denied.state());

                                         helper.runAfterDelay(20, () -> {
                                             try {
                                                 require(!com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.isStunned(body)
                                                                 && com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(body),
                                                         "ordinary status ticking expires the finite stun");
                                                 ChemMasterSessionService.handle(fixture.player(), request(denied.state(), 4,
                                                         ChemMasterControlRequest.Operation.QUERY, denied.state().mode()));
                                                 ChemMasterWindowUpdate recovered = takeWindowUpdate(fixture);
                                                 require(recovered.state().open()
                                                                 && recovered.state().sessionId().equals(opened.state().sessionId())
                                                                 && recovered.state().acknowledgedRequestId() == 4,
                                                         "same session still answers QUERY after stun expiry");
                                                 ChemMasterControlRequest.Mode recoveredMode = recovered.state().mode()
                                                         == ChemMasterControlRequest.Mode.TRANSFER
                                                         ? ChemMasterControlRequest.Mode.DISCARD : ChemMasterControlRequest.Mode.TRANSFER;
                                                 ChemMasterSessionService.handle(fixture.player(), request(recovered.state(), 5,
                                                         ChemMasterControlRequest.Operation.SET_MODE, recoveredMode));
                                                 ChemMasterWindowUpdate accepted = takeWindowUpdate(fixture);
                                                 require(accepted.state().open()
                                                                 && accepted.state().sessionId().equals(opened.state().sessionId())
                                                                 && accepted.state().acknowledgedRequestId() == 5
                                                                 && accepted.state().rejectionReason().isEmpty()
                                                                 && accepted.state().mode() == recoveredMode
                                                                 && accepted.state().stateRevision() > recovered.state().stateRevision(),
                                                         "same-session mutation succeeds after real stun expiry");

                                                 body.setPos(machinePos.getX() + 10.5, machinePos.getY() + 1,
                                                         machinePos.getZ() + 0.5);
                                                 helper.runAfterDelay(1, () -> {
                                                     try {
                                                         ChemMasterWindowUpdate closed = takeWindowUpdate(fixture);
                                                         require(!closed.state().open()
                                                                         && closed.state().sessionId().equals(opened.state().sessionId())
                                                                         && closed.state().closeReason().isPresent(),
                                                                 "ordinary server tick still closes session when body leaves resolved UI range");
                                                         helper.succeed();
                                                     } finally { fixture.close(); }
                                                 });
                                             } catch (RuntimeException | Error failure) {
                                                 fixture.close();
                                                 throw failure;
                                             }
                                         });
                                     } catch (RuntimeException | Error failure) {
                                         fixture.close();
                                         throw failure;
                                     }
                                 });
                             } catch (RuntimeException | Error failure) {
                                 fixture.close();
                                 throw failure;
                             }
                         });
            } catch (RuntimeException | Error failure) {
                fixture.close();
                throw failure;
            }
        });
    }

    private static ChemMasterWindowUpdate takeWindowUpdate(RegisteredServerPlayerFixture fixture) {
        Object outbound;
        java.util.ArrayList<String> packetTypes = new java.util.ArrayList<>();
        while ((outbound = fixture.pollOutbound()) != null) {
            ChemMasterWindowUpdate update = findWindowUpdate(outbound, packetTypes);
            if (update != null) return update;
        }
        throw new GameTestAssertException("no ChemMasterWindowUpdate outbound; packet types=" + packetTypes);
    }

    private static ChemMasterWindowUpdate takeOptionalWindowUpdate(RegisteredServerPlayerFixture fixture) {
        Object outbound;
        java.util.ArrayList<String> packetTypes = new java.util.ArrayList<>();
        while ((outbound = fixture.pollOutbound()) != null) {
            ChemMasterWindowUpdate update = findWindowUpdate(outbound, packetTypes);
            if (update != null) return update;
        }
        return null;
    }

    private static net.minecraft.world.entity.item.ItemEntity spawnPickupItem(ServerLevel level,
            net.minecraft.world.entity.LivingEntity body, net.minecraft.world.item.ItemStack stack) {
        var position = body.position().add(0, 0, -0.8);
        var item = new net.minecraft.world.entity.item.ItemEntity(level, position.x, position.y + .1, position.z, stack);
        item.setNoGravity(true);
        item.setPickUpDelay(0);
        level.addFreshEntity(item);
        return item;
    }

    private static void aimAtItem(net.minecraft.world.entity.LivingEntity body,
            net.minecraft.world.entity.item.ItemEntity item) {
        var delta = item.position().add(0, .2, 0).subtract(body.getEyePosition()).normalize();
        body.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        body.setXRot((float) -Math.toDegrees(Math.asin(delta.y)));
    }

    private static ChemMasterControlRequest request(com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterWindowState state,
            long id, ChemMasterControlRequest.Operation operation, ChemMasterControlRequest.Mode mode) {
        return new ChemMasterControlRequest(state.sessionId(), state.machineId(), state.dimension(),
                new BlockPos(state.x(), state.y(), state.z()), id, state.stateRevision(), state.slotRevision(), operation,
                mode, state.sort(), java.util.Optional.empty(), 0);
    }

    private static ChemMasterWindowUpdate findWindowUpdate(Object packet, java.util.List<String> packetTypes) {
        packetTypes.add(packet.getClass().getSimpleName());
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundBundlePacket bundle) {
            for (var nested : bundle.subPackets()) {
                ChemMasterWindowUpdate update = findWindowUpdate(nested, packetTypes);
                if (update != null) return update;
            }
        }
        if (packet instanceof ClientboundCustomPayloadPacket custom) {
            packetTypes.add(custom.payload().getClass().getSimpleName());
            return custom.payload() instanceof ChemMasterWindowUpdate update ? update : null;
        }
        return null;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
