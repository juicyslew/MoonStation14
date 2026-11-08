package com.juicyslew.moonstation14.ms14.disposal.unit;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionNetworking;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Exercises portable-body admission through the existing held-use request and real disposal transport. */
@PrefixGameTestTemplate(false)
@GameTestTags({"disposal", "living-carry", "hands"})
public final class DisposalPortableBodyHandUseGameTests {
    private DisposalPortableBodyHandUseGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 180)
    @GameTestTags({"disposal", "living-carry", "interaction", "portable-disposal"})
    public static void heldMouseTransfersAndExitsThroughRealDisposalRoute(GameTestHelper helper) {
        RegisteredServerPlayerFixture fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "portable-disposal");
        helper.runAtTickTime(2, () -> {
            try {
                var ready = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                ServerLevel level = (ServerLevel) helper.getLevel();
                BlockPos unitPos = helper.absolutePos(new BlockPos(4, 1, 6));
                BlockPos floor = unitPos.below();
                level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(unitPos, com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_UNIT.get().defaultBlockState(), 3);
                var unit = (DisposalUnitBlockEntity) level.getBlockEntity(unitPos);
                require(unit != null && unit.isValid(), "live resolved disposal unit installed");
                var body = ready.body();
                body.setPos(unitPos.getX() + .5, unitPos.getY() + 1, unitPos.getZ() - 1.4);
                var mouse = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
                require(mouse != null, "resolved mouse prototype creates original live entity");
                mouse.setNoAi(true);
                mouse.setPos(body.getX(), body.getY(), body.getZ() - .4);
                require(level.addFreshEntity(mouse), "original mouse joins loaded level");
                com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(mouse, level);
                mouse.setData(com.juicyslew.moonstation14.component.ModDataAttachments.STOMACH.get(),
                        new com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment());
                var stomach = mouse.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.STOMACH.get())
                        .toComponent();
                String hand = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())
                        .activeHand();
                String token = "portable-disposal-key";
                var key = com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrying
                        .createKey(body, mouse, UUID.randomUUID());
                com.juicyslew.moonstation14.ms14.hands.live.PortableBodyGameTestAccess
                        .holdPortableKey(body, hand, token, key);
                var liveHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                var portableSnapshot = com.juicyslew.moonstation14.ms14.hands.live.PortableBodyReleaseTransaction.capture(mouse);
                var originalOwner = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level)
                        .ownerOf(mouse).orElseThrow();
                var originalFlags = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level)
                        .export(mouse, originalOwner).orElseThrow();
                require(!unit.insertPortableBody(mouse, portableSnapshot, originalFlags, () -> false)
                                && unit.bodyIds().isEmpty()
                                && body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())
                                .stackCopy(hand).filter(com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys::isKey).isPresent()
                                && com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level)
                                .ownerOf(mouse).filter(owner -> owner instanceof com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrierOwner
                                        carried && carried.carrier() == body && carried.isValid()).isPresent(),
                        "stale admission gate leaves exact key, original root, and owner unchanged");
                var gateCalls = new java.util.concurrent.atomic.AtomicInteger();
                require(!unit.insertPortableBody(mouse, portableSnapshot, originalFlags,
                                () -> gateCalls.incrementAndGet() < 4)
                                && gateCalls.get() == 4 && unit.bodyIds().isEmpty()
                                && body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())
                                .stackCopy(hand).filter(com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys::isKey).isPresent()
                                && com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level)
                                .ownerOf(mouse).filter(owner -> owner instanceof com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrierOwner
                                        carried && carried.carrier() == body && carried.isValid()).isPresent()
                                && com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level)
                                .ownerOf(mouse).flatMap(owner -> com.juicyslew.moonstation14.ms14.containment
                                        .BodyContainmentService.forLevel(level).export(mouse, owner))
                                        .filter(originalFlags::equals).isPresent(),
                        "post-removal gate rejection compensates the exact root and original containment flags");
                liveHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                var refreshedHands = com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(fixture.player(),
                        new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(ready.epoch(), 2));
                require(refreshedHands.reason() == com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot.Reason.OK
                                && refreshedHands.revision() == liveHands.revision(),
                        "ordinary request path refreshes issued server hand snapshot after fixture gateway mutation");
                var staleActorRequest = new BodyBlockUseRequest(ready.epoch() + 1, 1, liveHands.revision(), hand, token,
                        unitPos, Direction.NORTH, false, BodyBlockUseRequest.Intent.USE);
                require(com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseGameTestAccess
                                .dispatch(fixture.player(), staleActorRequest) == BodyBlockUseResult.Outcome.DENIED
                                && unit.bodyIds().isEmpty()
                                && body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())
                                .stackCopy(hand).filter(com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys::isKey).isPresent()
                                && com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level)
                                .ownerOf(mouse).filter(owner -> owner instanceof com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrierOwner
                                        carried && carried.carrier() == body && carried.isValid()).isPresent(),
                        "stale actor epoch denial preserves exact key and original custody");
                var hit = RegisteredPlayerReadyDriver.aimAt(body, level, unitPos);
                require(hit.getBlockPos().equals(unitPos), "existing bounded block-use ray selects disposal unit: " + hit.getBlockPos());
                var admissionRequest = new BodyBlockUseRequest(ready.epoch(), 2, liveHands.revision(), hand, token,
                        hit.getBlockPos(), hit.getDirection(), false, BodyBlockUseRequest.Intent.USE);
                require(com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe
                                .probe(level, body, unitPos, hit.getDirection()) != null,
                        "request's bounded collider probe admits same face/body ray; origin=" + body.getEyePosition()
                                + ", look=" + body.getLookAngle() + ", hit=" + hit.getLocation());
                require(com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService
                                .probeBlockUse(fixture.player(), admissionRequest),
                        "ordinary authenticated block-use admission accepts refreshed held-key snapshot");
                var request = new BodyBlockUseRequest(ready.epoch(), 3, liveHands.revision(), hand, token,
                        hit.getBlockPos(), hit.getDirection(), false, BodyBlockUseRequest.Intent.USE);
                var dispatchResult = com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseGameTestAccess
                        .dispatch(fixture.player(), request);
                require(dispatchResult == BodyBlockUseResult.Outcome.PLACED,
                        "ordinary authorized held-use route admits portable body into disposal unit: " + dispatchResult
                                + ", adapter=" + com.juicyslew.moonstation14.ms14.hands.live.PortableBodyGameTestAccess
                                        .lastDisposalAdmissionDiagnostic()
                                + ", hit=" + hit + ", hand=" + liveHands.token(hand) + ", revision=" + liveHands.revision()
                                + ", snapshot=" + portableSnapshot + ", rootToken=" + portableSnapshot.rootToken()
                                + ", locationToken=" + portableSnapshot.locationToken() + ", rootId=" + portableSnapshot.rootId()
                                + ", carrier=" + body.getUUID() + ", unitValid=" + unit.isValid()
                                + ", owner=" + com.juicyslew.moonstation14.ms14.containment.BodyContainmentService
                                        .forLevel(level).ownerOf(mouse));
                UUID originalId = mouse.getUUID();
                require(unit.bodyIds().contains(originalId) && level.getEntity(originalId) == mouse
                                && com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level)
                                .ownerOf(mouse).orElse(null) == unit
                                && com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level)
                                .export(mouse, unit).filter(originalFlags::equals).isPresent()
                                && mouse.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.STOMACH.get())
                                .toComponent().equals(stomach)
                                && body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())
                                .token(hand).isEmpty(),
                        "unit owns same UUID/body physiology; held key is consumed rather than payload");
                var humanViewpoint = unit.positionFor(body).add(0, body.getEyeHeight(), 0);
                var mouseViewpoint = unit.positionFor(mouse).add(0, mouse.getEyeHeight(), 0);
                require(humanViewpoint.equals(mouseViewpoint),
                        "live unit owner places human and mouse native eyes at the same owner-authored camera viewpoint");
                com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level).tick();
                require(mouse.position().equals(unit.positionFor(mouse)),
                        "live containment tick pins the original mouse to the unit owner's resolved eye-aligned body position");

                var tubeWorld = com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeWorldData.forMutation(level);
                level.setBlock(floor, com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_TUBE.get().defaultBlockState(), 3);
                require(tubeWorld.add(level, new com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord(
                        UUID.randomUUID(), floor, "disposal_trunk", Direction.DOWN, true, "", "", 0),
                        pos -> level.getBlockState(pos).is(com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_TUBE.get())),
                        "actual open outlet trunk registered at the unit inlet");
                unit.setGameTestPowerOverride(true);
                unit.engage();
                helper.runAfterDelay(100, () -> {
                    try {
                        require(level.getEntity(originalId) == mouse && mouse.isAddedToLevel() && !mouse.isRemoved()
                                        && !unit.bodyIds().contains(originalId)
                                        && com.juicyslew.moonstation14.ms14.containment.BodyContainmentService
                                        .forLevel(level).ownerOf(mouse).isEmpty()
                                        && mouse.isInvisible() == originalFlags.invisible()
                                        && mouse.noPhysics == originalFlags.noPhysics()
                                        && mouse.isNoGravity() == originalFlags.noGravity()
                                        && mouse.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.STOMACH.get())
                                        .toComponent().equals(stomach),
                                "flush, tube travel, and outlet retain original UUID and physiology");
                        helper.succeed();
                    } catch (RuntimeException | Error failure) { throw failure; }
                });
            } catch (RuntimeException | Error failure) { throw failure; }
        });
    }

    /** Player-owned mouse remains lifecycle-controlled while its exact body traverses disposal transit. */
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 300)
    @GameTestTags({"disposal", "living-carry", "interaction", "player-lifecycle", "portable-disposal"})
    public static void committedMouseRetainsOwnerAndControlThroughDisposalTransit(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        var carrierFixture = RegisteredServerPlayerFixture.register(level, "disposalcarrier");
        var ownerFixture = RegisteredServerPlayerFixture.register(level, "disposalowner");
        var roleMouse = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(roleMouse != null, "canonical lifecycle mouse entity created");
        roleMouse.setPos(ownerFixture.player().position().add(2, 0, 0));
        require(level.addFreshEntity(roleMouse), "role mouse joins loaded level");
        try {
            com.juicyslew.moonstation14.gametest.support.CommittedMouseRoleSetup.start(helper, ownerFixture, roleMouse,
                    committed -> {
                        try {
                            // Both embedded logins emit Begin only after their normal deferred join tasks run.
                            var carrierReady = RegisteredPlayerReadyDriver.readyAndQuery(carrierFixture);
                            var binding = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character
                                    .PlayerCharacterHarnessBinder.binding(committed);
                            var before = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server
                                    .LifecycleCharacterSessionControl.activeCharacterBody(ownerFixture.player()).orElseThrow();
                            require(binding != null && before.body() == committed && ownerFixture.player().getCamera() == committed,
                                    "canonical role activation starts with exact player-owned mouse and binding");

                            BlockPos unitPos = helper.absolutePos(new BlockPos(4, 1, 6));
                            BlockPos floor = unitPos.below();
                            level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
                            level.setBlock(unitPos, com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_UNIT.get()
                                    .defaultBlockState(), 3);
                            var unit = (DisposalUnitBlockEntity) level.getBlockEntity(unitPos);
                            require(unit != null && unit.isValid(), "live unit for committed-role route");
                            var carrier = carrierReady.body();
                            carrier.setPos(unitPos.getX() + .5, unitPos.getY() + 1, unitPos.getZ() - 1.4);
                            String hand = carrier.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments
                                    .LIVE_HANDS.get()).activeHand();
                            String token = "committed-role-disposal-key";
                            var key = com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrying
                                    .createKey(carrier, committed, UUID.randomUUID());
                            com.juicyslew.moonstation14.ms14.hands.live.PortableBodyGameTestAccess
                                    .holdPortableKey(carrier, hand, token, key);
                            var ownerAuthority = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server
                                    .LifecycleCharacterSessionControl.activeCharacterBody(ownerFixture.player()).orElseThrow();
                            require(ownerAuthority.body() == committed && ownerFixture.player().getCamera() == committed
                                            && binding.equals(com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character
                                                    .PlayerCharacterHarnessBinder.binding(committed)),
                                    "portable carrier ownership does not replace lifecycle body authority");

                            var hit = RegisteredPlayerReadyDriver.aimAt(carrier, level, unitPos);
                            var hands = carrier.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments
                                    .LIVE_HANDS.get());
                            var request = new BodyBlockUseRequest(carrierReady.epoch(), 1, hands.revision(), hand, token,
                                    hit.getBlockPos(), hit.getDirection(), false, BodyBlockUseRequest.Intent.USE);
                            require(com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseGameTestAccess
                                    .dispatch(carrierFixture.player(), request) == BodyBlockUseResult.Outcome.PLACED,
                                    "separate authenticated carrier transfers committed original mouse into unit");
                            UUID id = committed.getUUID();
                            require(level.getEntity(id) == committed && unit.bodyIds().contains(id)
                                            && carrier.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments
                                                    .LIVE_HANDS.get()).stackCopy(hand).isEmpty()
                                            && com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server
                                            .LifecycleCharacterSessionControl.activeCharacterBody(ownerFixture.player())
                                            .filter(active -> active.body() == committed).isPresent()
                                            && ownerFixture.player().getCamera() == committed
                                            && binding.equals(com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character
                                                    .PlayerCharacterHarnessBinder.binding(committed)),
                                    "contained original retains UUID, binding, active owner, and camera");

                             var tubeWorld = com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeWorldData.forMutation(level);
                             BlockPos straight = floor.east(), bend = straight.east(), exit = bend.south();
                             var tubeBlock = com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_TUBE.get();
                             for (BlockPos pos : java.util.List.of(floor, straight, bend, exit)) {
                                 level.setBlock(pos, tubeBlock.defaultBlockState(), 3);
                                 if (!pos.equals(floor)) level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
                             }
                             var routeRecords = java.util.List.of(
                                     new com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord(
                                             UUID.randomUUID(), floor, "disposal_trunk", Direction.EAST, true, "", "", 0),
                                     new com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord(
                                             UUID.randomUUID(), straight, "disposal_pipe", Direction.EAST, true, "", "", 0),
                                     new com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord(
                                             UUID.randomUUID(), bend, "disposal_bend", Direction.WEST, true, "", "", 0),
                                     new com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord(
                                             UUID.randomUUID(), exit, "disposal_pipe", Direction.SOUTH, true, "", "", 0));
                             for (var record : routeRecords) require(tubeWorld.add(level, record,
                                     pos -> level.getBlockState(pos).is(tubeBlock)),
                                     "real multi-segment outlet route registered at " + record.pos());
                            unit.setGameTestPowerOverride(true);
                            unit.engage();
                             // Manual flush deadlines are advanced on the following server tick.
                             helper.runAfterDelay(45, () -> {
                                try {
                                    var containment = com.juicyslew.moonstation14.ms14.containment
                                            .BodyContainmentService.forLevel(level);
                                    var transitOwner = containment.ownerOf(committed).orElse(null);
                                    var transitHolder = level.getEntitiesOfClass(
                                                    com.juicyslew.moonstation14.ms14.disposal.transport.DisposalTransitHolder.class,
                                                    new net.minecraft.world.phys.AABB(unitPos).inflate(64),
                                                    holder -> holder.bodyIds().contains(id)).stream().findFirst().orElse(null);
                                    require(transitOwner != null
                                            && transitOwner.stage() == com.juicyslew.moonstation14.ms14.containment
                                                    .ContainedBodyOwner.Stage.TRANSIT
                                            && transitHolder != null
                                            && transitOwner.view().ownerIdentity().equals(transitOwner.identity())
                                            && com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server
                                                    .LifecycleCharacterSessionControl.activeCharacterBody(ownerFixture.player())
                                                    .filter(active -> active.body() == committed).isPresent()
                                            && ownerFixture.player().getCamera() == committed
                                             && binding.equals(com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character
                                                     .PlayerCharacterHarnessBinder.binding(committed)),
                                             "transit preserves exact owner binding and committed camera session: stage="
                                                     + (transitOwner == null ? "none" : transitOwner.stage())
                                                     + ", holder=" + transitHolder + ", bodies="
                                                     + (transitHolder == null ? "none" : transitHolder.bodyIds())
                                                     + ", active=" + com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server
                                                     .LifecycleCharacterSessionControl.activeCharacterBody(ownerFixture.player())
                                                     + ", camera=" + ownerFixture.player().getCamera()
                                                     + ", binding=" + com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character
                                                     .PlayerCharacterHarnessBinder.binding(committed)
                                                     + ", expectedBinding=" + binding);
                                    helper.runAfterDelay(100, () -> {
                                        try {
                                            require(level.getEntity(id) == committed && !unit.bodyIds().contains(id)
                                                            && containment.ownerOf(committed).isEmpty()
                                                            && com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server
                                                            .LifecycleCharacterSessionControl.activeCharacterBody(ownerFixture.player())
                                                            .filter(active -> active.body() == committed).isPresent()
                                                            && ownerFixture.player().getCamera() == committed
                                                            && binding.equals(com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character
                                                                    .PlayerCharacterHarnessBinder.binding(committed)),
                                                    "same committed UUID exits transit without lifecycle session loss");
                                            helper.succeed();
                                        } finally {
                                            carrierFixture.close(); ownerFixture.close(); committed.discard();
                                        }
                                    });
                                } catch (RuntimeException | Error failure) {
                                    carrierFixture.close(); ownerFixture.close(); committed.discard(); throw failure;
                                }
                            });
                        } catch (RuntimeException | Error failure) {
                            carrierFixture.close(); ownerFixture.close(); committed.discard(); throw failure;
                        }
                    });
        } catch (RuntimeException | Error failure) {
            carrierFixture.close(); ownerFixture.close(); roleMouse.discard(); throw failure;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
