package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.*;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.fixture.ChemMasterFixtureService;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Authenticated player, admitted block-use route, normal ChemMaster control handler and output ownership. */
@PrefixGameTestTemplate(false)
@GameTestTags({"chemmaster-output"})
public final class ChemMasterOutputAuthenticatedGameTests {
    private ChemMasterOutputAuthenticatedGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 600)
    @GameTestTags({"chemmaster-output-auth"})
    public static void registeredPlayerInsertsAndPackagesThroughNormalOwners(GameTestHelper helper) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "chem-output-auth");
        var fixtureClosed = new AtomicBoolean();
        Runnable closeFixture = () -> { if (fixtureClosed.compareAndSet(false, true)) fixture.close(); };
        helper.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { closeFixture.run(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { closeFixture.run(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest,
                    net.minecraft.gametest.framework.GameTestInfo newTest, net.minecraft.gametest.framework.GameTestRunner runner) { closeFixture.run(); }
        });
        helper.runAtTickTime(2, () -> {
            try {
                if (fixtureClosed.get()) return;
                var owner = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                ServerLevel level = (ServerLevel) helper.getLevel();
                BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 2));
                require(ChemMasterFixtureService.build(level, origin).startsWith("Built open-air"), "build ordinary powered fixture supplies");
                BlockPos machinePos = origin.offset(3, 1, 8);
                PowerFixtureReadiness.await(helper, level, 400,
                        () -> level.getBlockEntity(machinePos) instanceof ChemMasterBlockEntity machine
                                && PowerRuntime.hasFullFreshChemMasterPower(level, machine, machine.placementIdentity()),
                     () -> "fixture ChemMaster waits for fresh normal power", () -> {
                    if (fixtureClosed.get()) return;
                    try {
                        var machine = (ChemMasterBlockEntity) level.getBlockEntity(machinePos);
                        var water = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water");
                        var sugar = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("sugar");
                        require(machine.buffer().addWholeMixture(new com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment(Map.of(water, 2f, sugar, 2f))),
                                "seed machine buffer mixture");
                        machine.stateChanged();
                        var body = owner.body();
                        body.setPos(machinePos.getX() + .5, machinePos.getY() + 1, machinePos.getZ() - .5);
                        var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                        var handId = hands.handIds().getFirst();
                        // Match the ordinary ChemMaster fixture's bare item stack: absence of an
                        // explicit grid attachment resolves to its valid empty profile at runtime.
                        var emptyCanister = new ItemStack(ModItems.PILL_CANISTER.get());
                        var itemPosition = body.position().add(0, 0, -0.8);
                        var canisterEntity = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                                itemPosition.add(0, .1, 0), emptyCanister);
                        canisterEntity.setNoGravity(true);
                        require(level.addFreshEntity(canisterEntity), "registered loose canister enters supported world-item owner");
                        aimAtItem(body, canisterEntity);
                        var selectedItem = com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.probeSelected(
                                level, body, canisterEntity.getUUID(), com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.MAX_REACH);
                        require(selectedItem != null, "normal body-eye item admission selects fixture canister; eye=" + body.getEyePosition()
                                + " look=" + body.getLookAngle() + " item=" + canisterEntity.position());
                        var handAuthority = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElse(null);
                        var representedAfterPickup = hands.grant(hands.revision(), emptyCanister.copy());
                        boolean looseAdmission = com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.loadedReachableItem(
                                level, body, canisterEntity.getUUID(), com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.MAX_REACH);
                        boolean postPickupRepresentable = representedAfterPickup.changed() && representedAfterPickup.remainder().isEmpty()
                                && BodyHandStateService.representable(representedAfterPickup.state(), body);
                        require(looseAdmission && handAuthority != null
                                        && com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.revalidate(fixture.player(), handAuthority)
                                        && BodyHandStateService.representable(hands, body) && postPickupRepresentable,
                         "pickup prerequisites: looseAdmission=" + looseAdmission + " authority=" + (handAuthority != null)
                                        + " revalidated=" + (handAuthority != null && com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.revalidate(fixture.player(), handAuthority))
                                        + " beforeRepresentable=" + BodyHandStateService.representable(hands, body)
                                         + " afterRepresentable=" + postPickupRepresentable + " entityType=" + canisterEntity.getClass().getName()
                                         + " gridHost=" + com.juicyslew.moonstation14.ms14.storage.grid.GridContents.isHost(emptyCanister)
                                         + " gridRead=" + com.juicyslew.moonstation14.ms14.storage.grid.GridContents.read(emptyCanister).isPresent()
                                         + " profile=" + com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.resolveServer(
                                                com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.prototype(emptyCanister))
                                         + " grantChanged=" + representedAfterPickup.changed()
                                         + " remainder=" + representedAfterPickup.remainder()
                                         + " slots=" + BodyHandStateService.slots(representedAfterPickup.state(), representedAfterPickup.state().handIds())
                                         + " equipment=" + BodyHandStateService.equipment(representedAfterPickup.state(), body,
                                                BodyHandStateService.slots(representedAfterPickup.state(), representedAfterPickup.state().handIds())));
                        var pickup = BodyHandItemTransfer.pickupSelected(fixture.player(), canisterEntity.getUUID(), handId,
                                hands.revision(), ignored -> {});
                        require(pickup == BodyHandItemTransfer.Result.SUCCESS,
                                "ordinary registered-player pickup obtains canister; result=" + pickup + " hand=" + handId
                                        + " entityRemoved=" + canisterEntity.isRemoved() + " body=" + body.position()
                                        + " item=" + canisterEntity.position() + " target=" + canisterEntity.getTarget()
                                        + " delay=" + canisterEntity.hasPickUpDelay() + " reach=" + body.distanceToSqr(canisterEntity.position())
                                        + " handrev=" + hands.revision() + " compatible=" + hands.compatible(body)
                                        + " actorCanAct=" + com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(body));
                        var held = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(owner.epoch(), 10));
                        var hand = held.hands().stream().filter(value -> value.id().equals(handId)).findFirst().orElseThrow();
                        var parentOutputToken = hand.token();
                        var hit = RegisteredPlayerReadyDriver.aimAt(body, level, machinePos);
                        var use = new BodyBlockUseRequest(owner.epoch(), 1, held.revision(), handId, hand.token(),
                                machinePos, hit.getDirection(), false, BodyBlockUseRequest.Intent.USE);
                        require(BodyHandActionNetworking.handleBlockUse(fixture.player(), use) == BodyBlockUseResult.Outcome.PLACED,
                                "normal admitted held USE inserts canister into removable output slot");
                        require(machine.outputSlot().snapshot().stack().is(ModItems.PILL_CANISTER), "machine owns exact canister output");

                        var fresh = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(owner.epoch(), 11));
                        var emptyHand = fresh.hands().stream().filter(value -> value.id().equals(fresh.activeHand())).findFirst().orElseThrow();
                        require(emptyHand.token() == null, "active hand is empty after output insertion");
                        var activationHit = RegisteredPlayerReadyDriver.aimAt(body, level, machinePos);
                        var activate = new BodyBlockUseRequest(owner.epoch(), 2, fresh.revision(), emptyHand.id(), null,
                                machinePos, activationHit.getDirection(), false, BodyBlockUseRequest.Intent.ACTIVATE_WORLD);
                        var activationOutcome = BodyHandActionNetworking.handleBlockUse(fixture.player(), activate);
                        require(activationOutcome == BodyBlockUseResult.Outcome.NO_CHANGE,
                                "active empty-hand world activation opens workspace; outcome="
                                        + activationOutcome + " query=" + fresh.reason() + " empty=" + emptyHand.id()
                                        + " rev=" + fresh.revision() + "/" + hands.revision() + " token=" + emptyHand.token()
                                        + " active=" + fresh.activeHand()
                                        + " eligible=" + com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority
                                            .resolve(fixture.player()).map(value -> com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority.eligibleForComplexInteraction(fixture.player(), value)).orElse(false)
                                        + " range=" + body.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(machinePos))
                                        + "/" + machine.prototype().uiRange());
                        var opened = takeUpdate(fixture).state();
                        require(opened.open() && opened.outputSlot().kind() == ChemMasterWindowState.OutputKind.PILL_CANISTER,
                                "full snapshot describes actual canister and output revision");

                        var type = request(opened, 1, ChemMasterControlRequest.Operation.SET_PILL_TYPE, null, -1, 7, null);
                        ChemMasterSessionService.handle(fixture.player(), type);
                        var selected = takeUpdate(fixture).state();
                        require(selected.pillType() == 7 && selected.drawSource() == ChemMasterControlRequest.DrawSource.BUFFER,
                                "global pill type changes and default draw source is BUFFER");
                        var create = request(selected, 2, ChemMasterControlRequest.Operation.CREATE_PILLS,
                                null, selected.bufferRevision(), -1,
                                new ChemMasterControlRequest.OutputRequest(2, 1, "Authenticated"));
                        ChemMasterSessionService.handle(fixture.player(), create);
                        var packaged = takeUpdate(fixture).state();
                        require(packaged.acknowledgedRequestId() == 2 && packaged.rejectionReason().isEmpty()
                                        && packaged.outputSlot().pills().size() == 2,
                                "normal authenticated control handler commits pills and correlated snapshot");
                        require(packaged.outputSlot().pills().stream().allMatch(pill -> pill.type() == 7
                                        && pill.label().filter("Authenticated"::equals).isPresent()),
                                "snapshot exposes authoritative type and raw labels");
                        require(packaged.bufferReagents().stream().mapToLong(ChemMasterWindowState.ReagentRow::amountCents).sum() == 200,
                                "BUFFER pill operation withdrew exact mixed dose based on authoritative snapshot");

                        // Insert a real reagent beaker by ordinary pickup + held USE, then change
                        // global source and package from INPUT through the authenticated control owner.
                        var afterBufferHands = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(owner.epoch(), 12));
                        var openHand = afterBufferHands.hands().stream().filter(value -> value.token() == null).findFirst().orElseThrow();
                        ItemStack beakerStack = new ItemStack(com.juicyslew.moonstation14.item.ModItems.BEAKER.get());
                        beakerStack.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(),
                                new com.juicyslew.moonstation14.ms14.reagent.ReagentComponent(Map.of(water, 2f, sugar, 2f)));
                        var beakerPosition = body.position().add(0, 0, -0.8);
                        var beakerEntity = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                                beakerPosition.add(0, .1, 0), beakerStack);
                        beakerEntity.setNoGravity(true);
                        require(level.addFreshEntity(beakerEntity), "registered loose reagent beaker enters the supported world-item owner");
                        aimAtItem(body, beakerEntity);
                        require(BodyHandItemTransfer.pickupSelected(fixture.player(), beakerEntity.getUUID(), openHand.id(),
                                afterBufferHands.revision(), ignored -> {}) == BodyHandItemTransfer.Result.SUCCESS,
                                "ordinary selected-item authority obtains the reagent beaker");
                        var beakerHeld = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(owner.epoch(), 13));
                        var heldBeaker = beakerHeld.hands().stream().filter(value -> value.id().equals(openHand.id())).findFirst().orElseThrow();
                        var beakerHit = RegisteredPlayerReadyDriver.aimAt(body, level, machinePos);
                        require(BodyHandActionNetworking.handleBlockUse(fixture.player(), new BodyBlockUseRequest(owner.epoch(), 3,
                                beakerHeld.revision(), heldBeaker.id(), heldBeaker.token(), machinePos, beakerHit.getDirection(),
                                false, BodyBlockUseRequest.Intent.USE)) == BodyBlockUseResult.Outcome.PLACED,
                                "normal admitted held USE inserts reagent beaker into INPUT slot");
                        var inputInserted = takeLatestUpdate(fixture);
                        require(inputInserted.inputVolumeCents() == 400, "snapshot reports exact mixed INPUT mixture");

                        var selectInput = request(inputInserted, 3, ChemMasterControlRequest.Operation.SET_DRAW_SOURCE,
                                ChemMasterControlRequest.DrawSource.INPUT, -1, -1, null);
                        ChemMasterSessionService.handle(fixture.player(), selectInput);
                        var inputSelected = takeAcknowledged(fixture, 3);
                        require(inputSelected.drawSource() == ChemMasterControlRequest.DrawSource.INPUT,
                                "authenticated source-selection operation is reflected in ACK snapshot");
                        var selectType = request(inputSelected, 4, ChemMasterControlRequest.Operation.SET_PILL_TYPE,
                                null, -1, 9, null);
                        ChemMasterSessionService.handle(fixture.player(), selectType);
                        var inputType = takeAcknowledged(fixture, 4);
                        var inputPills = request(inputType, 5, ChemMasterControlRequest.Operation.CREATE_PILLS,
                                null, inputType.bufferRevision(), -1,
                                new ChemMasterControlRequest.OutputRequest(1, 1, "Input dose"));
                        ChemMasterSessionService.handle(fixture.player(), inputPills);
                        var inputPackaged = takeAcknowledged(fixture, 5);
                        require(inputPackaged.rejectionReason().isEmpty() && inputPackaged.outputSlot().pills().size() == 3
                                        && inputPackaged.outputSlot().pills().stream().anyMatch(pill -> pill.type() == 9
                                            && pill.label().filter("Input dose"::equals).isPresent()
                                            && pill.reagents().stream().mapToLong(ChemMasterWindowState.ReagentRow::amountCents).sum() == 100),
                                "INPUT source creates a labelled 1-unit pill with selected global type");
                        require(inputPackaged.inputVolumeCents() == 300
                                        && inputPackaged.bufferReagents().stream().mapToLong(ChemMasterWindowState.ReagentRow::amountCents).sum() == 200,
                                "INPUT packaging conserves mixture and leaves BUFFER untouched");

                        // Eject through the authenticated shared slot transfer and prove the
                        // canister/child grid remains owned after leaving the machine.
                        var ejectCanister = request(inputPackaged, 6, ChemMasterControlRequest.Operation.EJECT_OUTPUT,
                                null, -1, -1, null);
                        ChemMasterSessionService.handle(fixture.player(), ejectCanister);
                        var canisterEjected = takeAcknowledged(fixture, 6);
                        require(canisterEjected.rejectionReason().isEmpty()
                                        && canisterEjected.outputSlot().kind() == ChemMasterWindowState.OutputKind.EMPTY,
                                "authenticated EJECT_OUTPUT clears the output slot");
                        var liveHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                        var canisterToken = parentOutputToken;
                        var parentToken = new com.juicyslew.moonstation14.ms14.hands.ItemToken(canisterToken);
                        var ledger = com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers.forLevel(level);
                        String canisterHandId = liveHands.handIds().stream()
                                .filter(id -> liveHands.token(id).filter(canisterToken::equals).isPresent()).findFirst().orElse(null);
                        boolean heldCanister = canisterHandId != null;
                        net.minecraft.world.item.ItemStack carriedCanister = heldCanister
                                ? liveHands.stackCopy(canisterHandId).orElseThrow() : null;
                        if (carriedCanister == null) {
                            var search = new net.minecraft.world.phys.AABB(machinePos).inflate(4);
                            carriedCanister = level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,
                                    search, entity -> entity.getItem().is(ModItems.PILL_CANISTER)).stream()
                                    .map(entity -> entity.getItem().copy())
                                    .filter(stack -> com.juicyslew.moonstation14.ms14.storage.grid.GridContents.read(stack)
                                            .map(contents -> contents.entries().size() == 3).orElse(false))
                                    .findFirst().orElse(null);
                        }
                        require(carriedCanister != null,
                                "EJECT_OUTPUT returned the filled canister through its hand/world owner; handToken="
                                        + canisterHandId + " ledgerParent=" + ledger.snapshot().locationOf(parentToken));
                        var carriedGrid = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.read(carriedCanister).orElseThrow();
                        require(carriedGrid.entries().size() == 3 && carriedGrid.entries().stream().allMatch(entry ->
                                com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers.forLevel(level).snapshot()
                                    .locationOf(new com.juicyslew.moonstation14.ms14.hands.ItemToken(entry.token()))
                                    .filter(location -> location.equals(new com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation.StorageCell(
                                             new com.juicyslew.moonstation14.ms14.hands.ItemToken(canisterToken), "main",
                                            com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation.Orientation.UNSPECIFIED,
                                            entry.x() + "," + entry.y()))).isPresent()),
                                "all three pills retain child StorageCell claims under the ejected canister token");

                        // BodyHandStateService rate-limits four owner snapshots per tick. Continue
                        // the bottle route next tick, using the normal active-hand selection owner.
                         helper.runAfterDelay(1, () -> {
                             try {
                                 if (fixtureClosed.get()) return;
                                var beforeSelect = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                                String bottleHandId = beforeSelect.handIds().stream().filter(id -> !id.equals(beforeSelect.activeHand())
                                        && beforeSelect.token(id).isEmpty()).findFirst().orElseGet(() -> beforeSelect.handIds().stream()
                                        .filter(id -> beforeSelect.token(id).isEmpty()).findFirst().orElseThrow());
                                var select = new BodyHandActionRequest(BodyHandActionRequest.Action.SELECT_HAND, bottleHandId,
                                        owner.epoch(), 1, beforeSelect.revision(), null, null);
                                require(BodyHandRequestService.apply(fixture.player(), select).accepted(),
                                        "ordinary authenticated SELECT_HAND activates the free bottle hand");
                                var live = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                                var bottlePosition = body.position().add(0, 0, -0.8);
                                var bottleEntity = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                                        bottlePosition.add(0, .1, 0), new ItemStack(ModItems.BOTTLE.get()));
                                bottleEntity.setNoGravity(true);
                                require(level.addFreshEntity(bottleEntity), "registered loose output bottle enters supported world-item owner");
                                aimAtItem(body, bottleEntity);
                                require(BodyHandItemTransfer.pickupSelected(fixture.player(), bottleEntity.getUUID(), bottleHandId,
                                        live.revision(), ignored -> {}) == BodyHandItemTransfer.Result.SUCCESS,
                                        "ordinary selected-item authority obtains output bottle");
                                var bottleHeldState = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(owner.epoch(), 16));
                                var heldBottle = bottleHeldState.hands().stream().filter(value -> value.id().equals(bottleHandId)).findFirst().orElseThrow();
                                var bottleToken = heldBottle.token();
                                var bottleHit = RegisteredPlayerReadyDriver.aimAt(body, level, machinePos);
                                require(BodyHandActionNetworking.handleBlockUse(fixture.player(), new BodyBlockUseRequest(owner.epoch(), 4,
                                        bottleHeldState.revision(), heldBottle.id(), heldBottle.token(), machinePos, bottleHit.getDirection(),
                                        false, BodyBlockUseRequest.Intent.USE)) == BodyBlockUseResult.Outcome.PLACED,
                                        "normal admitted held USE inserts bottle into output slot");
                                // QUERY ignores snapshot revisions by contract, but receives a correlated
                                // authoritative response instead of trusting a drained unsolicited update.
                                var bottleQuery = request(inputPackaged, 7, ChemMasterControlRequest.Operation.QUERY,
                                        null, -1, -1, null);
                                ChemMasterSessionService.handle(fixture.player(), bottleQuery);
                                var bottleInserted = takeAcknowledged(fixture, 7);
                                require(bottleInserted.acknowledgedRequestId() == 7 && bottleInserted.rejectionReason().isEmpty()
                                                 && bottleInserted.outputSlot().kind() == ChemMasterWindowState.OutputKind.BOTTLE
                                                 && bottleInserted.outputSlot().revision() > inputPackaged.outputSlot().revision(),
                                         "correlated QUERY snapshot describes inserted bottle; kind=" + bottleInserted.outputSlot().kind()
                                                 + " outputRevision=" + bottleInserted.outputSlot().revision() + " priorRevision="
                                                 + inputPackaged.outputSlot().revision() + " stateRevision=" + bottleInserted.stateRevision()
                                                 + " ack=" + bottleInserted.acknowledgedRequestId() + " rejection=" + bottleInserted.rejectionReason());
                                var fillBottle = request(bottleInserted, 8, ChemMasterControlRequest.Operation.OUTPUT_TO_BOTTLE,
                                        null, bottleInserted.bufferRevision(), -1,
                                        new ChemMasterControlRequest.OutputRequest(0, 1, "Bottle from input"));
                                ChemMasterSessionService.handle(fixture.player(), fillBottle);
                                var bottleFilled = takeAcknowledged(fixture, 8);
                                require(bottleFilled.rejectionReason().isEmpty()
                                                && bottleFilled.outputSlot().kind() == ChemMasterWindowState.OutputKind.BOTTLE
                                                && bottleFilled.outputSlot().volumeCents() == 100
                                                && bottleFilled.outputSlot().label().filter("Bottle from input"::equals).isPresent(),
                                        "authenticated INPUT source fills bottle with exact labeled 1-unit mixture");
                                require(bottleFilled.inputVolumeCents() == 200
                                                && bottleFilled.bufferReagents().stream().mapToLong(ChemMasterWindowState.ReagentRow::amountCents).sum() == 200,
                                        "bottle operation conserves INPUT mixture and leaves BUFFER unchanged");
                                var ejectBottle = request(bottleFilled, 9, ChemMasterControlRequest.Operation.EJECT_OUTPUT,
                                        null, -1, -1, null);
                                ChemMasterSessionService.handle(fixture.player(), ejectBottle);
                                var bottleEjected = takeAcknowledged(fixture, 9);
                                require(bottleEjected.rejectionReason().isEmpty()
                                                && bottleEjected.outputSlot().kind() == ChemMasterWindowState.OutputKind.EMPTY,
                                        "authenticated output ejection also succeeds after bottle filling");
                                var afterBottleEjectHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                                var ejectedBottleHand = afterBottleEjectHands.handIds().stream()
                                        .filter(id -> afterBottleEjectHands.token(id).filter(bottleToken::equals).isPresent()).findFirst().orElseThrow();
                                var ejectedBottle = afterBottleEjectHands.stackCopy(ejectedBottleHand).orElseThrow();
                                var bottleContents = ejectedBottle.get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
                                require(bottleContents != null && bottleContents.centContents().values().stream().mapToLong(Long::longValue).sum() == 100,
                                        "ejected bottle retains its exact reagent contents");
                                closeFixture.run(); helper.succeed();
                            } catch (RuntimeException | Error failure) { closeFixture.run(); throw failure; }
                        });
                    } catch (RuntimeException | Error failure) { closeFixture.run(); throw failure; }
                });
            } catch (RuntimeException | Error failure) { closeFixture.run(); throw failure; }
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 200)
    @GameTestTags({"chemmaster-output-auth", "chemmaster-fixture"})
    public static void registeredPlayerPicksUpBareFixtureCanister(GameTestHelper helper) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "chem-output-fixture-pickup");
        helper.runAtTickTime(2, () -> {
            try {
                var owner = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var level = (ServerLevel) helper.getLevel();
                BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 2));
                require(ChemMasterFixtureService.build(level, origin).startsWith("Built open-air"),
                        "build standard ChemMaster fixture with its ordinary bare canister");
                var canisterPos = origin.offset(7, 1, 5);
                var canister = level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,
                        new net.minecraft.world.phys.AABB(canisterPos).inflate(.5),
                        entity -> entity.getItem().is(ModItems.PILL_CANISTER)).stream().findFirst().orElseThrow();
                require(!canister.getItem().has(com.juicyslew.moonstation14.component.ModDataComponents.GRID_CONTENTS.get()),
                        "fixture canister is bare and relies on resolved virtual-empty grid");
                var body = owner.body();
                body.setPos(origin.getX() + 6.5, origin.getY() + 1, origin.getZ() + 5.5);
                var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                var handId = hands.activeHand();
                aimAtItem(body, canister);
                var selected = com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.probeSelected(
                        level, body, canister.getUUID(), com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.MAX_REACH);
                require(selected != null, "ordinary body-eye target probe reaches fixture canister; body=" + body.position()
                        + " eye=" + body.getEyePosition() + " target=" + canister.position()
                        + " range=" + body.distanceToSqr(canister.position()));
                require(BodyHandItemTransfer.pickupSelected(fixture.player(), canister.getUUID(), handId,
                                hands.revision(), ignored -> {}) == BodyHandItemTransfer.Result.SUCCESS,
                        "standard fixture bare canister is acquired through authenticated pickupSelected");
                var held = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                require(held.stackCopy(handId).filter(stack -> stack.is(ModItems.PILL_CANISTER)
                                && !stack.has(com.juicyslew.moonstation14.component.ModDataComponents.GRID_CONTENTS.get())
                                && com.juicyslew.moonstation14.ms14.storage.grid.GridContents.read(stack)
                                        .map(grid -> grid.entries().isEmpty()).orElse(false)).isPresent(),
                        "normal pickup preserves bare representation while resolving its empty grid");
                fixture.close(); helper.succeed();
            } catch (RuntimeException | Error failure) { throw failure; }
        });
    }

    private static ChemMasterControlRequest request(ChemMasterWindowState state, long id,
            ChemMasterControlRequest.Operation operation, ChemMasterControlRequest.DrawSource source,
            long bufferRevision, int pillType, ChemMasterControlRequest.OutputRequest output) {
        return new ChemMasterControlRequest(state.sessionId(), state.machineId(), state.dimension(),
                new BlockPos(state.x(), state.y(), state.z()), id, state.stateRevision(), state.slotRevision(), operation,
                state.mode(), state.sort(), java.util.Optional.empty(), 0,
                output == null && operation != ChemMasterControlRequest.Operation.EJECT_OUTPUT ? 0 : state.outputSlot().revision(),
                output == null ? 0 : state.bufferRevision(),
                source == null ? java.util.Optional.empty() : java.util.Optional.of(source), pillType,
                java.util.Optional.ofNullable(output));
    }
    private static void aimAtItem(net.minecraft.world.entity.LivingEntity body, net.minecraft.world.entity.item.ItemEntity item) {
        var delta=item.position().add(0,.2,0).subtract(body.getEyePosition()).normalize();
        body.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z))); body.setXRot((float)-Math.toDegrees(Math.asin(delta.y)));
    }
    private static ChemMasterWindowUpdate takeUpdate(RegisteredServerPlayerFixture fixture) {
        Object packet; while((packet=fixture.pollOutbound())!=null){
            if(packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof ChemMasterWindowUpdate update) return update;
            if(packet instanceof net.minecraft.network.protocol.game.ClientboundBundlePacket bundle)
                for(var nested:bundle.subPackets()) if(nested instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof ChemMasterWindowUpdate update)return update;
        }
        throw new GameTestAssertException("missing authenticated ChemMaster state update");
    }
    private static ChemMasterWindowState takeAcknowledged(RegisteredServerPlayerFixture fixture, long requestId) {
        Object packet;
        while ((packet = fixture.pollOutbound()) != null) {
            var found = findUpdate(packet);
            if (found != null && found.state().acknowledgedRequestId() == requestId) return found.state();
        }
        throw new GameTestAssertException("missing correlated ChemMaster ACK " + requestId);
    }
    private static ChemMasterWindowState takeLatestUpdate(RegisteredServerPlayerFixture fixture) {
        ChemMasterWindowUpdate latest = null;
        Object packet;
        while ((packet = fixture.pollOutbound()) != null) {
            var found = findUpdate(packet);
            if (found != null) latest = found;
        }
        if (latest == null) throw new GameTestAssertException("missing unsolicited ChemMaster snapshot");
        return latest.state();
    }
    private static ChemMasterWindowUpdate findUpdate(Object packet) {
        if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof ChemMasterWindowUpdate update) return update;
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundBundlePacket bundle)
            for (var nested : bundle.subPackets()) { var update = findUpdate(nested); if (update != null) return update; }
        return null;
    }
    private static void require(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}
}
