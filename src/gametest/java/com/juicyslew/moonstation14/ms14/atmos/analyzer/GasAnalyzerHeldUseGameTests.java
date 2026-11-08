package com.juicyslew.moonstation14.ms14.atmos.analyzer;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHeldItemActions;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"atmos-analyzer", "interaction"})
public final class GasAnalyzerHeldUseGameTests {
    private GasAnalyzerHeldUseGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 80)
    @GameTestTags({"atmos-analyzer-held-use", "network"})
    public static void ordinaryHeldUseOpensBoundReadOnlySessionAndPutawayClosesIt(GameTestHelper helper) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "atmos-analyzer-held-use-v1");
        helper.runAtTickTime(2, () -> {
            try {
                GasAnalyzerClientState.clear();
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var body = active.body();
                var authority = HandActorAuthority.resolve(fixture.player()).orElseThrow();
                var source = LooseItemEntity.create((net.minecraft.server.level.ServerLevel) body.level(), body.position().add(1, 0, 0),
                        new ItemStack(ModItems.ATMOSPHERE_ANALYZER.get()));
                source.setNoGravity(true);
                require(body.level().addFreshEntity(source), "analyzer source is loaded and published");
                require(BodyHandItemTransfer.pickupTrustedCreated(authority, source, source.getItem(), () -> true)
                        == BodyHandItemTransfer.Result.SUCCESS, "registered controlled-body pickup");
                var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                String hand = hands.activeHand(), token = hands.token(hand).orElseThrow();
                var result = BodyHandRequestService.apply(fixture.player(), new BodyHandActionRequest(
                        BodyHandActionRequest.Action.USE_IN_HAND, hand, active.epoch(), 1, hands.revision(), null, token));
                require(result.accepted() && result.reason() == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK,
                        "normal authoritative held USE_IN_HAND opens analyzer");
                var snapshot = GasAnalyzerSessionService.latestSnapshot(fixture.player());
                require(snapshot != null && !snapshot.closed() && snapshot.body().equals(body.getUUID())
                                && snapshot.epoch() == active.epoch() && snapshot.localCell() != null,
                        "immediate session snapshot is bound to body and includes local-environment cell");
                GasAnalyzerClientState.accept(snapshot);
                require(GasAnalyzerClientState.current() != null
                                && GasAnalyzerClientState.current().session().equals(snapshot.session()),
                        "production client data facade accepts the correlated initial snapshot");
                GasAnalyzerSessionService.control(fixture.player(), new GasAnalyzerControl(snapshot.session(), snapshot.generation(),
                        snapshot.epoch(), 1, 0, false));
                var zeroRange = GasAnalyzerSessionService.latestSnapshot(fixture.player());
                var zeroTarget = GasAnalyzerCellSelection.select((net.minecraft.server.level.ServerLevel) body.level(),
                        body.getEyePosition(), body.getLookAngle(), 0);
                require(zeroRange != null && zeroRange.controlSequence() == 1 && zeroRange.requestedDistance() == 0
                                && GasAnalyzerSampleAcceptancePolicy.responseMatchesTarget(zeroRange, 0, 1, zeroTarget),
                        "live session distance-zero control reads and correlates the server-derived eye cell");
                var remoteTarget=GasAnalyzerCellSelection.select((net.minecraft.server.level.ServerLevel)body.level(),
                        body.getEyePosition(),body.getLookAngle(),snapshot.requestedDistance());
                require(GasAnalyzerSampleAcceptancePolicy.responseMatchesTarget(snapshot,snapshot.requestedDistance(),0,remoteTarget),
                        "production UI model only accepts the server sample for the same server-derived body-eye cell");
                GasAnalyzerSessionService.control(fixture.player(),new GasAnalyzerControl(snapshot.session(),snapshot.generation(),
                        snapshot.epoch(),2,2,false));
                var controlled=GasAnalyzerSessionService.latestSnapshot(fixture.player());
                var controlledTarget=GasAnalyzerCellSelection.select((net.minecraft.server.level.ServerLevel)body.level(),
                        body.getEyePosition(),body.getLookAngle(),2);
                require(controlled!=null&&controlled.controlSequence()==2&&controlled.requestedDistance()==2
                                &&GasAnalyzerSampleAcceptancePolicy.responseMatchesTarget(controlled,2,2,controlledTarget),
                        "distance control triggers actual server read and its correlated data facade target agrees with the production UI model");
                var reopen = BodyHeldItemActions.activate(fixture.player(), new BodyHandActionRequest(
                        BodyHandActionRequest.Action.USE_IN_HAND, hand, active.epoch(), 2, hands.revision(), null, token));
                var reopened = GasAnalyzerSessionService.latestSnapshot(fixture.player());
                require(reopen == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK
                                && reopened != null && reopened.session().equals(controlled.session())
                                && reopened.generation() == controlled.generation()
                                && reopened.requestedDistance() == controlled.requestedDistance(),
                         "in-hand activation reopens same authoritative session without resetting range");
                String otherHand = hands.handIds().stream().filter(id -> !id.equals(hand)).findFirst().orElseThrow();
                require(BodyHandRequestService.apply(fixture.player(), new BodyHandActionRequest(
                        BodyHandActionRequest.Action.SELECT_HAND, otherHand, active.epoch(), 3, hands.revision(), null, null)).accepted(),
                        "authenticated hand selection makes held analyzer inactive");
                hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                require(hands.token(hand).filter(token::equals).isPresent(), "analyzer source remains held in its original inactive slot");
                var inactiveBaseline = GasAnalyzerSessionService.latestSnapshot(fixture.player());
                GasAnalyzerSessionService.control(fixture.player(), new GasAnalyzerControl(snapshot.session(), snapshot.generation(),
                        snapshot.epoch(), 3, 1, false));
                require(GasAnalyzerSessionService.latestSnapshot(fixture.player()) == inactiveBaseline
                                && !inactiveBaseline.closed(),
                        "valid newer control while source is held but inactive neither samples nor closes the session");
                GasAnalyzerSessionService.serverTick(null);
                require(GasAnalyzerSessionService.latestSnapshot(fixture.player()) == inactiveBaseline
                                && !inactiveBaseline.closed(), "inactive held source survives server tick without a spurious close");
                require(BodyHandRequestService.apply(fixture.player(), new BodyHandActionRequest(
                        BodyHandActionRequest.Action.SELECT_HAND, hand, active.epoch(), 4, hands.revision(), null, null)).accepted(),
                        "authenticated hand selection reactivates analyzer source");
                hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                require(hands.activeHand().equals(hand) && hands.token(hand).filter(token::equals).isPresent(),
                        "source token remains owned by the reactivated hand");
                GasAnalyzerSessionService.control(fixture.player(), new GasAnalyzerControl(snapshot.session(), snapshot.generation(),
                        snapshot.epoch(), 4, 2, false));
                var activeAgain = GasAnalyzerSessionService.latestSnapshot(fixture.player());
                require(activeAgain != inactiveBaseline && activeAgain.controlSequence() == 4
                                && activeAgain.requestedDistance() == 2 && !activeAgain.closed(),
                        "strictly newer control samples again after source reactivation");

                long retargetGameTime = body.level().getGameTime();
                int oldYaw = body.getYRot() < 0 ? -90 : 0;
                body.setYRot(oldYaw == -90 ? 0 : -90);
                body.setYHeadRot(body.getYRot());
                var expectedRetarget = GasAnalyzerCellSelection.select((net.minecraft.server.level.ServerLevel) body.level(),
                        body.getEyePosition(), body.getLookAngle(), activeAgain.requestedDistance());
                require(expectedRetarget.cell() != null && !expectedRetarget.cell().equals(activeAgain.targetCell()),
                        "actual changed body look independently predicts a different target cell");
                int profileInterval = ModGasAnalyzers.resolve((net.minecraft.server.level.ServerLevel) body.level()).updateIntervalTicks();
                require(retargetGameTime < activeAgain.serverTick() + profileInterval,
                        "retarget check is before the profile sampling deadline");
                GasAnalyzerSessionService.serverTick(null);
                var retargeted = GasAnalyzerSessionService.latestSnapshot(fixture.player());
                require(retargeted.sequence() == activeAgain.sequence() + 1
                                && retargeted.targetCell().equals(expectedRetarget.cell()),
                        "server tick emits an immediate correlated new sample for changed aim before deadline");
                GasAnalyzerSessionService.serverTick(null);
                require(GasAnalyzerSessionService.latestSnapshot(fixture.player()) == retargeted,
                        "unchanged pose at same game time does not spam another sample");
                GasAnalyzerClientState.accept(controlled);
                require(GasAnalyzerClientState.current().targetCell()==null||GasAnalyzerClientState.current().targetCell().equals(controlledTarget.cell()),
                        "facade retains the latest server-derived target cell, never a client-forged coordinate");
                require(BodyHandItemTransfer.drop(fixture.player(), hand, token, hands.revision()) == BodyHandItemTransfer.Result.SUCCESS,
                        "put analyzer away through real body-hand transfer");
                GasAnalyzerSessionService.serverTick(null);
                var closed = GasAnalyzerSessionService.latestSnapshot(fixture.player());
                require(closed != null && closed.closed() && closed.generation() == snapshot.generation(),
                        "source loss closes the exact session generation");
                GasAnalyzerClientState.accept(closed);
                require(GasAnalyzerClientState.current().closed(), "correlated close reaches the same client state generation");
                helper.succeed();
            } catch (Throwable failure) { helper.fail("authenticated gas analyzer held-use session: " + failure); }
            finally { fixture.close(); }
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    @GameTestTags({"atmos-analyzer-selection"})
    public static void serverCellTraversalStopsAtOpaqueAndPassesCanonicalGlass(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var origin = helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
        var start = new net.minecraft.world.phys.Vec3(origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5);
        var glass = origin.east();
        level.setBlock(glass, net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState(), 3);
        var east = new net.minecraft.world.phys.Vec3(1, 0, 0);
        var eyeCell=GasAnalyzerCellSelection.select(level,start,east,0);
        require(eyeCell.status()==GasAnalyzerCellSelection.Status.SELECTED&&eyeCell.cell().equals(origin),
                "range zero samples the loaded eye-origin cell");
        var throughGlass = GasAnalyzerCellSelection.select(level, start, east, 3);
        require(throughGlass.status() == GasAnalyzerCellSelection.Status.SELECTED
                        && throughGlass.cell().equals(origin.east(3)),
                "shared ExamineVision glass remains inspectable to the selected finite cell");
        level.setBlock(glass, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(origin, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        var zeroInsideOpaque=GasAnalyzerCellSelection.select(level,start,east,0);
        require(zeroInsideOpaque.status()==GasAnalyzerCellSelection.Status.BLOCKED,
                "range zero at an opaque origin is blocked rather than leaking a sample");
        level.setBlock(origin, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        var behindWall = GasAnalyzerCellSelection.select(level, start, east, 3);
        require(behindWall.status() == GasAnalyzerCellSelection.Status.SELECTED
                        && behindWall.cell().equals(origin), "opaque collision clamps sampling to the last air cell before it");
        var stale = GasAnalyzerSampleAcceptancePolicy.responseMatchesTarget(
                new GasAnalyzerSnapshot(java.util.UUID.randomUUID(), 1, 4, 1, 10, 1, java.util.UUID.randomUUID(),
                        "source", level.dimension().location(), origin, throughGlass.cell(), 3,
                        GasAnalyzerSnapshot.Status.READY, null, null, false), 3, 1, behindWall);
        require(!stale, "previous transparent-cell response is rejected after authoritative aim hits opaque wall");
        helper.succeed();
    }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    @GameTestTags({"atmos-analyzer-held-use", "network", "atmos-analyzer-selection"})
    public static void registeredAnalyzerReadsFiniteChamberThroughGlassAndRejectsStaleTarget(GameTestHelper helper) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "atmos-analyzer-glass-chamber-v1");
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var anchor = helper.absolutePos(new net.minecraft.core.BlockPos(4, 2, 4));
        var lease = com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.acquire(helper,
                new net.minecraft.world.phys.AABB(anchor).inflate(6));
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(helper, lease, 40,
                () -> true, "analyzer sealed-glass chamber", () -> helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var body = active.body();
                var authority = HandActorAuthority.resolve(fixture.player()).orElseThrow();
                var source = LooseItemEntity.create(level, body.position().add(1, 0, 0),
                        new ItemStack(ModItems.ATMOSPHERE_ANALYZER.get()));
                source.setNoGravity(true);
                require(level.addFreshEntity(source), "analyzer source published");
                require(BodyHandItemTransfer.pickupTrustedCreated(authority, source, source.getItem(), () -> true)
                        == BodyHandItemTransfer.Result.SUCCESS, "registered controlled-body pickup");
                var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                String hand = hands.activeHand(), token = hands.token(hand).orElseThrow();

                // A closed 5x5x5 glass shell, with a 3x3x3 passable finite interior.
                for (int dx = 0; dx < 5; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) {
                    boolean shell = dx == 0 || dx == 4 || dy == -2 || dy == 2 || dz == -2 || dz == 2;
                    if (shell) level.setBlock(anchor.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState(), 3);
                }
                var interior = new java.util.ArrayList<net.minecraft.core.BlockPos>();
                for (int dx = 1; dx <= 3; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++)
                    interior.add(anchor.offset(dx, dy, dz));
                var authored = new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(
                        java.util.Map.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA, 10.0), 340.0);
                require(com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE
                                .initializeEnclosedFiniteMixtureForTrustedSetup(level, interior, authored),
                        "trusted one-shot initializer proves and seeds closed loaded chamber");
                var expectedReading = com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE
                        .readAtmosphere(level, anchor.offset(2, 0, 0)).orElseThrow();
                require(expectedReading.status() == com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading.Status.FINITE,
                        "interior endpoint is an actual finite atmosphere cell");

                // Aim the controlled body east from outside; the ray crosses the glass west wall.
                // Put the actual eye at a known half-cell outside the west glass wall.
                body.setPos(anchor.getX() - .75, anchor.getY() + .5 - body.getEyeHeight(), anchor.getZ() + .5);
                body.setYRot(-90); body.setYHeadRot(-90); body.setXRot(0);
                var eye = body.getEyePosition();
                var look = body.getLookAngle();
                var firstCell = anchor.offset(2, 0, 0);
                var secondCell = anchor.offset(3, 0, 0);
                var expectedFirstSelection = GasAnalyzerCellSelection.select(level, eye, look, 3);
                var expectedSecondSelection = GasAnalyzerCellSelection.select(level, eye, look, 4);
                require(expectedFirstSelection.status() == GasAnalyzerCellSelection.Status.SELECTED
                                && expectedFirstSelection.cell().equals(firstCell), selectionFacts("range 3", eye, look, 3, expectedFirstSelection, firstCell));
                require(expectedSecondSelection.status() == GasAnalyzerCellSelection.Status.SELECTED
                                && expectedSecondSelection.cell().equals(secondCell), selectionFacts("range 4", eye, look, 4, expectedSecondSelection, secondCell));
                var opened = BodyHandRequestService.apply(fixture.player(), new BodyHandActionRequest(
                        BodyHandActionRequest.Action.USE_IN_HAND, hand, active.epoch(), 1, hands.revision(), null, token));
                require(opened.accepted(), "actual registered held-use opens while aimed through glass");
                var initial = GasAnalyzerSessionService.latestSnapshot(fixture.player());
                require(initial != null, "initial authoritative analyzer response");
                GasAnalyzerSessionService.control(fixture.player(), new GasAnalyzerControl(initial.session(), initial.generation(),
                        initial.epoch(), 1, 3, false));
                var first = GasAnalyzerSessionService.latestSnapshot(fixture.player());
                require(first.targetCell().equals(firstCell) && first.status() == GasAnalyzerSnapshot.Status.READY
                                && first.target() != null && first.local() != null
                                && first.local().gases().stream().noneMatch(g -> g.gas() == com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA),
                        "server reads distinct chamber target rather than body-local air; "
                                + selectionFacts("range 3", eye, look, 3, expectedFirstSelection, firstCell)
                                + "; latest=" + first.targetCell() + "; local=" + first.local() + "; target=" + first.target());
                var expected = GasAnalyzerSample.from(expectedReading, ModGasAnalyzers.resolve(level));
                var actualPlasma = first.target().gases().stream()
                        .filter(g -> g.gas() == com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA).findFirst().orElseThrow();
                require(Math.abs(actualPlasma.moles() - expected.gases().getFirst().moles()) <= 1e-5
                                && Math.abs(first.target().temperatureKelvin() - expected.temperatureKelvin()) <= 1e-5
                                && Math.abs(first.target().pressureKpa() - expected.pressureKpa()) <= 1e-5
                                && Math.abs(first.target().volumeLitres() - expected.volumeLitres()) <= 1e-5,
                        "snapshot carries expected finite mixture species, moles, temperature, pressure, and volume");
                require(!first.targetCell().equals(anchor.east()), "glass wall cell is not reported as the gas sample");

                // A second range selects the next interior cell; former-cell data cannot be correlated to it.
                GasAnalyzerSessionService.control(fixture.player(), new GasAnalyzerControl(first.session(), first.generation(),
                        first.epoch(), 2, 4, false));
                var second = GasAnalyzerSessionService.latestSnapshot(fixture.player());
                var predictedSecond = expectedSecondSelection;
                require(second.controlSequence() == 2 && second.requestedDistance() == 4
                                && second.targetCell().equals(secondCell) && !second.targetCell().equals(first.targetCell()),
                        "new range/control selects distinct finite chamber cell; "
                                + selectionFacts("range 4", eye, look, 4, predictedSecond, secondCell)
                                + "; latest=" + second.targetCell() + "; local=" + second.local() + "; target=" + second.target());
                require(!GasAnalyzerSampleAcceptancePolicy.responseMatchesTarget(first, 4, 2, predictedSecond)
                                && GasAnalyzerSampleAcceptancePolicy.responseMatchesTarget(second, 4, 2, predictedSecond),
                        "old response rejected and only newly correlated target accepted");
                level.setBlock(anchor, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                var opaqueSelection = GasAnalyzerCellSelection.select(level, eye, look, 4);
                require(opaqueSelection.status() == GasAnalyzerCellSelection.Status.SELECTED && opaqueSelection.cell().equals(anchor.west()),
                        selectionFacts("opaque range 4", eye, look, 4, opaqueSelection, anchor.west()));
                GasAnalyzerSessionService.control(fixture.player(), new GasAnalyzerControl(second.session(), second.generation(),
                        second.epoch(), 3, 4, false));
                var blocked = GasAnalyzerSessionService.latestSnapshot(fixture.player());
                require(blocked.status() == GasAnalyzerSnapshot.Status.READY && blocked.target() != null
                                && blocked.targetCell().equals(anchor.west()), "opaque wall clamps actual session read to last air cell, never gas beyond wall; "
                                + selectionFacts("opaque range 4", eye, look, 4, opaqueSelection, anchor.west())
                                + "; latest=" + blocked.targetCell() + "; local=" + blocked.local() + "; target=" + blocked.target());
                helper.succeed();
            } catch (Throwable failure) { helper.fail("registered finite chamber analyzer sample: " + failure); }
            finally { fixture.close(); lease.close(); }
        }), anchor);
    }
    private static String selectionFacts(String label, net.minecraft.world.phys.Vec3 eye, net.minecraft.world.phys.Vec3 look,
                                         int range, GasAnalyzerCellSelection.Result selected, net.minecraft.core.BlockPos expected) {
        return label + " eye=" + eye + " look=" + look + " range=" + range + " selected=" + selected.cell()
                + " status=" + selected.status() + " expected=" + expected;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
