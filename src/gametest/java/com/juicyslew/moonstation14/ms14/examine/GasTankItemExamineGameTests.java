package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.item.custom.GasTankItem;
import com.juicyslew.moonstation14.ms14.atmos.tank.ModGasTanks;
import com.juicyslew.moonstation14.ms14.hands.live.BodyGiveService;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.live.LiveOwnedItemAccess;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags("item_examine")
public final class GasTankItemExamineGameTests {
    private GasTankItemExamineGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 200)
    public static void authenticatedHeldAndDroppedTankExamineUsesFreshOwnerAndCommonItemView(GameTestHelper helper) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "tankexamine");
        helper.runAtTickTime(2, () -> {
            try {
                var admitted = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var body = admitted.body();
                var level = helper.getLevel();
                var tank = GasTankItem.createStack(level, ModGasTanks.OXYGEN_FILLED_ID);
                var tankContents = GasTankItem.contents(tank, level).orElseThrow();
                var tankProfile = ModGasTanks.catalog(level).get(tankContents.prototypeId());
                String expectedPressure = GasTankExaminePresentation.examinedPressure(
                        tankContents.mixture().pressureKpa(tankProfile.volumeCubicMeters()));
                var emergency = GasTankItem.createStack(level, ModGasTanks.EMERGENCY_OXYGEN_FILLED_ID);
                var emergencyContents = GasTankItem.contents(emergency, level).orElseThrow();
                var emergencyProfile = ModGasTanks.catalog(level).get(emergencyContents.prototypeId());
                String expectedEmergencyPressure = GasTankExaminePresentation.examinedPressure(
                        emergencyContents.mixture().pressureKpa(emergencyProfile.volumeCubicMeters()));
                require(BodyGiveService.give(fixture.player(), tank), "tank is granted through normal controlled-body give");
                LiveHands hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                String hand = hands.activeHand(), token = hands.token(hand).orElseThrow();
                long revision = hands.revision();
                var target = ExamineTarget.ownedItem(LiveOwnedItemAccess.Location.HELD, hand, revision, token);
                var response = ExamineService.query(fixture.player(), new ExamineRequest(1, body.getUUID(), admitted.epoch(), target));
                require(response.accepted() && response.detailsAllowed(), "authenticated held-item request accepted with close details");
                assertTankRows(response, true, expectedPressure);
                var unchanged = LiveHands.equipmentSnapshot(body).orElseThrow();
                require(unchanged.persisted(), "examination retains resolved equipment owner state");

                var stale = ExamineService.query(fixture.player(), new ExamineRequest(2, body.getUUID(), admitted.epoch(),
                        ExamineTarget.ownedItem(LiveOwnedItemAccess.Location.HELD, hand, revision - 1, token)));
                require(!stale.accepted(), "stale hand revision is denied");
                var forged = ExamineService.query(fixture.player(), new ExamineRequest(3, body.getUUID(), admitted.epoch(),
                        ExamineTarget.ownedItem(LiveOwnedItemAccess.Location.HELD, hand, revision, "forged-token")));
                require(!forged.accepted(), "forged owner token is denied");
                var unknownHand = ExamineService.query(fixture.player(), new ExamineRequest(31, body.getUUID(), admitted.epoch(),
                        ExamineTarget.ownedItem(LiveOwnedItemAccess.Location.HELD, "not-a-hand", revision, token)));
                require(!unknownHand.accepted(), "unknown hand identifier is denied rather than throwing");
                require(body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) == hands,
                        "malformed held-slot queries do not change the body owner state");

                require(BodyHandItemTransfer.drop(fixture.player(), hand, token, revision) == BodyHandItemTransfer.Result.SUCCESS,
                        "fixture moves exact tank from hand through normal body drop path");
                var loose = level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,
                        body.getBoundingBox().inflate(4), item -> GasTankItem.contents(item.getItem(), level).isPresent())
                        .stream().findFirst().orElseThrow(() -> new GameTestAssertException("normal drop yields loose tank entity"));
                var floorResponse = ExamineService.query(fixture.player(), new ExamineRequest(4, body.getUUID(), admitted.epoch(),
                        ExamineTarget.entity(loose)));
                require(floorResponse.accepted(), "floor item uses ordinary world examination admission");
                assertTankRows(floorResponse, true, expectedPressure);
                require(keys(floorResponse).contains("examine.moonstation14.gas-tank.disconnected"),
                        "loose floor tank is not inferred connected from a body-wide scan");

                ItemStack bag = new ItemStack(com.juicyslew.moonstation14.item.ModItems.BAG.get());
                String childToken = "examine-child-token";
                var grid = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.of(java.util.List.of(
                        new com.juicyslew.moonstation14.ms14.storage.grid.GridContents.Entry(childToken,
                                GasTankItem.createStack(level, ModGasTanks.EMERGENCY_OXYGEN_FILLED_ID), 0, 0,
                                com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Rotation.DEG_0)));
                bag = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.applyCopyWithGrid(bag, grid)
                        .orElseThrow(() -> new GameTestAssertException("valid filled tank fits in bag grid"));
                require(BodyGiveService.give(fixture.player(), bag), "body-owned grid host admitted via normal give");
                LiveHands bagHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                String bagHand = bagHands.handIds().stream().filter(id -> bagHands.stackCopy(id)
                        .map(stack -> stack.is(com.juicyslew.moonstation14.item.ModItems.BAG)).orElse(false)).findFirst().orElseThrow();
                String bagToken = bagHands.token(bagHand).orElseThrow();
                long bagRevision = bagHands.revision();
                var hostView = com.juicyslew.moonstation14.ms14.hands.live.BodyEquippedStorageTransfer.readGridItem(
                        body, new LiveHands.GridHost(false, bagHand, bagToken), childToken, bagRevision);
                require(hostView.isPresent() && GasTankItem.contents(hostView.orElseThrow(), level).isPresent(),
                        "canonical body-owned held-grid reader resolves exact child token from host slot id");
                require(com.juicyslew.moonstation14.ms14.hands.live.BodyEquippedStorageTransfer.readGridItem(
                        body, new LiveHands.GridHost(false, bagHand, "wrong-host"), childToken, bagRevision).isEmpty(),
                        "canonical body-owned reader rejects mismatched host token");
                require(com.juicyslew.moonstation14.ms14.hands.live.BodyEquippedStorageTransfer.readGridItem(
                        body, new LiveHands.GridHost(false, bagHand, bagToken), childToken, bagRevision - 1).isEmpty(),
                        "canonical body-owned reader rejects stale revision");
                var storedTarget = ExamineTarget.storedItem(LiveOwnedItemAccess.Location.HELD, bagHand,
                        bagRevision, bagToken, childToken);
                var storedResponse = ExamineService.query(fixture.player(), new ExamineRequest(5, body.getUUID(), admitted.epoch(), storedTarget));
                require(storedResponse.accepted() && storedResponse.detailsAllowed(), "authorized body-owned grid child examines through fresh host resolution");
                assertTankRows(storedResponse, true, expectedEmergencyPressure);
                require(!ExamineService.query(fixture.player(), new ExamineRequest(6, body.getUUID(), admitted.epoch(),
                        ExamineTarget.storedItem(LiveOwnedItemAccess.Location.HELD, bagHand, bagRevision, "wrong-host", childToken))).accepted(),
                        "wrong grid-host token is denied");
                require(!ExamineService.query(fixture.player(), new ExamineRequest(7, body.getUUID(), admitted.epoch(),
                        ExamineTarget.storedItem(LiveOwnedItemAccess.Location.HELD, bagHand, bagRevision, bagToken, "wrong-child"))).accepted(),
                        "wrong child token is denied");
                require(!ExamineService.query(fixture.player(), new ExamineRequest(32, body.getUUID(), admitted.epoch(),
                        ExamineTarget.storedItem(LiveOwnedItemAccess.Location.HELD, "not-a-hand", bagRevision, bagToken, childToken))).accepted(),
                        "unknown storage host identifier is denied rather than throwing");
                require(body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).revision() == bagRevision,
                        "invalid grid-host query leaves current owner revision unchanged");

                require(BodyHandItemTransfer.drop(fixture.player(), bagHand, bagToken, bagRevision)
                                == BodyHandItemTransfer.Result.SUCCESS,
                        "storage host transitions to world through normal body drop");
                var worldBag = level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,
                        body.getBoundingBox().inflate(5), entity -> com.juicyslew.moonstation14.ms14.storage.grid.GridContents.read(entity.getItem()).isPresent())
                        .stream().findFirst().orElseThrow(() -> new GameTestAssertException("dropped bag is loaded"));
                body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, worldBag.position());
                long worldHandRevision = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).revision();
                final com.juicyslew.moonstation14.ms14.hands.network.BodyWorldGridSnapshot[] opened = {null};
                fixture.observeOutbound(packet -> {
                    if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                            && custom.payload() instanceof com.juicyslew.moonstation14.ms14.hands.network.BodyWorldGridSnapshot snapshot)
                        opened[0] = snapshot;
                });
                var listener = (net.minecraft.server.network.ServerGamePacketListenerImpl) fixture.connection().getPacketListener();
                listener.handleCustomPayload(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                        new com.juicyslew.moonstation14.ms14.hands.network.BodyWorldGridQuery(admitted.epoch(), 1,
                                worldBag.getUUID(), worldHandRevision)));
                require(opened[0] != null && opened[0].reason() == com.juicyslew.moonstation14.ms14.hands.network.BodyWorldGridSnapshot.Reason.OK,
                        "normal registered payload opens a world storage session");
                var worldChild = ExamineTarget.worldStoredItem(worldBag.getUUID(), opened[0].session(),
                        opened[0].handRevision(), opened[0].hostRevision(), childToken);
                var worldResponse = ExamineService.query(fixture.player(), new ExamineRequest(8, body.getUUID(), admitted.epoch(), worldChild));
                require(worldResponse.accepted(), "only current open host session authorizes world child examination");
                assertTankRows(worldResponse, true, expectedEmergencyPressure);
                var counterfeit = ExamineTarget.worldStoredItem(worldBag.getUUID(), java.util.UUID.randomUUID(),
                        opened[0].handRevision(), opened[0].hostRevision(), childToken);
                require(!ExamineService.query(fixture.player(), new ExamineRequest(9, body.getUUID(), admitted.epoch(), counterfeit)).accepted(),
                        "forged world-session nonce is denied");
                require(!ExamineService.query(fixture.player(), new ExamineRequest(10, body.getUUID(), admitted.epoch() + 1, worldChild)).accepted(),
                        "stale body epoch is denied for world child target");
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                        "MS14_ITEM_EXAMINE_PASS test=authenticatedHeldFloorOwnedGridAndOpenWorldStorage accepted=true heldGridHostResolved=true staleRevisionDenied=true forgedTokenDenied=true forgedSessionDenied=true epochDenied=true");
                helper.succeed();
            } catch (Throwable failure) {
                throw failure instanceof RuntimeException runtime ? runtime : new GameTestAssertException(failure.getMessage());
            } finally { fixture.close(); }
        });
    }

    private static void assertTankRows(ExamineResponse response, boolean details, String expectedPressure) {
        var keys = keys(response);
        require(keys.contains("examine.moonstation14.gas-tank.valve-closed"), "sealed tank reports implemented closed valve state");
        require(keys.contains("examine.moonstation14.gas-tank.disconnected"), "disconnected tank state is explicit");
        require(keys.contains("examine.moonstation14.gas-tank.pressure") == details,
                "pressure detail follows actual examination detail-range gate");
        if (details) {
            var row = response.entries().stream().map(ExamineEntry::text)
                    .filter(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                            && t.getKey().equals("examine.moonstation14.gas-tank.pressure")).findFirst().orElseThrow();
            Object actual = ((net.minecraft.network.chat.contents.TranslatableContents) row.getContents()).getArgs()[0];
            require(expectedPressure.equals(actual), "examine uses rounded production pressure formatter: " + actual);
        }
        require(!keys.contains("examine.moonstation14.gas-tank.connected"), "fixture tank is not connected");
    }
    private static java.util.Set<String> keys(ExamineResponse response) {
        var keys = new java.util.HashSet<String>();
        for (var entry : response.entries()) collect(entry.text(), keys);
        return keys;
    }
    private static void collect(net.minecraft.network.chat.Component component, java.util.Set<String> keys) {
        if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t) {
            keys.add(t.getKey());
            for (Object arg : t.getArgs()) if (arg instanceof net.minecraft.network.chat.Component nested) collect(nested, keys);
        }
        component.getSiblings().forEach(child -> collect(child, keys));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
