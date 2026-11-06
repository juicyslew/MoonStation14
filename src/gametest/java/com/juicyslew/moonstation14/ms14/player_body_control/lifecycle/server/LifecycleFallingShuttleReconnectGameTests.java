package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.FixtureHeldTool;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult;
import com.juicyslew.moonstation14.ms14.hands.network.LifecycleHandActorFixture;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlock;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity;
import com.juicyslew.moonstation14.ms14.doors.AirlockState;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import com.juicyslew.moonstation14.ms14.shuttle.sable.MovingCabinAuthorityFixture;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import com.juicyslew.moonstation14.ms14.doors.NativeAirlockContactAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

/** A disconnected living body continues its actual fall with its native cabin before normal rejoin. */
@GameTestTags({"shuttle-falling-reconnect"})
@PrefixGameTestTemplate(false)
public final class LifecycleFallingShuttleReconnectGameTests {
    private LifecycleFallingShuttleReconnectGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 900)
    public static void reconnectAttachesToPhysicallyFallingBody(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(1400, 300, 1100),
                "falling-shuttle-reconnect", 2.5, 1, 2.5, (fixture, body) -> {
            var owner = fixture.owner();
            var actor = owner.actor();
            var server = helper.getLevel().getServer();
            var account = actor.getUUID();
            var mind = owner.authority().mindId();
            var hands = owner.authority().handIds();
            var binding = body.playerCharacterBinding();
            long intentSequence = owner.intentSequence().get();
            var liveHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            if (liveHands == null || !liveHands.compatible(body)) { helper.fail("initial Ready lacks live hands"); return; }
            var issued = FixtureHeldTool.issue(actor, liveHands.activeHand(), new ItemStack(Items.GLASS_BOTTLE));
            var frame = fixture.plot().engineId();
            var handle = RigidBodyHandle.of(fixture.plot().subLevel());
            fixture.setIntentPumpEnabled(false);
            handle.addLinearAndAngularVelocity(new Vector3d(0, 20, 0), new Vector3d());
            double[] previousY = {fixture.plot().subLevel().logicalPose().position().y};
            BoundedGameTestWait.await(helper, 160, () -> "native frame did not begin falling after ascent",
                    () -> {
                        double y = fixture.plot().subLevel().logicalPose().position().y;
                        boolean falling = handle.getLinearVelocity(new Vector3d()).y < -.5 && y < previousY[0];
                        previousY[0] = y;
                        return falling;
                    }, () -> {
                var context = LifecycleStartupRuntime.contextFor(server).orElseThrow();
                actor.connection.disconnect(Component.literal("falling shuttle reconnect fixture"));
            BoundedGameTestWait.await(helper, 80, () -> "disconnect did not persist OFFLINE profile",
                        () -> server.getPlayerList().getPlayer(account) != actor
                                && context.lifecycle().profile(account).map(p -> !p.active()
                                && p.state() == com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.LifecycleState.OFFLINE).orElse(false),
                        () -> observeOffline(helper, fixture, body, owner, frame, mind, hands, issued, context,
                                binding, intentSequence, 240, body.position().y, fixture.plot().subLevel().logicalPose().position().y));
            });
        });
    }

    private static void observeOffline(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
            PlayerCharacterHarnessEntity body, LifecycleHandActorFixture.Ready owner, java.util.UUID frame,
            com.juicyslew.moonstation14.ms14.player_body_control.MindId mind, java.util.List<String> hands,
            FixtureHeldTool.Issued issued, LifecycleServerContext context,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBinding binding,
        long intentSequence, int remaining, double startY, double fallingStartFrameY) {
        final int[] freshFloorContacts = {0};
        final boolean[] observedDownwardMotion = {false};
        final Object[] previousCollisionInfo = {((EntityMovementExtension) (Object) body).sable$getCollisionInfo()};
        final String[] firstFailure = {"none"};
        BoundedGameTestWait.observeServerTicks(helper, remaining, event -> {
            long tick = helper.getLevel().getServer().getTickCount();
            var movement = (EntityMovementExtension) (Object) body;
            double verticalVelocity = RigidBodyHandle.of(fixture.plot().subLevel())
                    .getLinearVelocity(new Vector3d()).y;
            if (verticalVelocity < -.5) observedDownwardMotion[0] = true;
            Object rawInfo = movement.sable$getCollisionInfo();
            var info = rawInfo instanceof SubLevelEntityCollision.CollisionInfo collision ? collision : null;
            var tracking = movement.sable$getTrackingSubLevel();
            boolean freshInfo = rawInfo != previousCollisionInfo[0];
            if (info != null && freshInfo && info.verticalCollisionBelow
                    && body.onGround() && tracking == fixture.plot().subLevel()
                    && info.trackingSubLevel == fixture.plot().subLevel()) freshFloorContacts[0]++;
            previousCollisionInfo[0] = rawInfo;
            var point = LoadedFrameRayQuery.currentActorPoint(body.level(), body);
            var live = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            if (!((Object) body instanceof com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob marker)
                    || !marker.moonstation14$physicalStepConsumed(helper.getLevel(), tick)
                    || point == null || !frame.equals(point.frameId()) || point.storagePoint().y < fixture.cabin().origin().getY() + .8
                    || point.storagePoint().y > fixture.cabin().origin().getY() + 3
                    || body.isRemoved() || helper.getLevel().getEntity(body.getUUID()) != body || !body.isAlive()
                    || !binding.equals(body.playerCharacterBinding())
                    || !owner.authority().mindId().equals(mind) || !owner.authority().handIds().equals(hands)
                    || owner.intentSequence().get() != intentSequence
                    || context.lifecycle().profile(owner.actor().getUUID()).map(p -> p.active()
                            || p.state() != com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.LifecycleState.OFFLINE).orElse(true)
                    || live == null
                    || live.token(issued.hand()).filter(issued.token()::equals).isEmpty()
                    || live.stackCopy(issued.hand()).filter(stack -> ItemStack.matches(stack, issued.stack())).isEmpty()) {
                firstFailure[0] = "offline body physical/ownership check failed: serverTick=" + tick
                        + ", levelTime=" + helper.getLevel().getGameTime() + ", eligible="
                        + com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep
                        .isPassivePlayerCharacterEligible(body) + ", markerCurrent="
                        + markerState(body, helper, tick) + ", markerPrevious=" + markerState(body, helper, tick - 1)
                        + ", bodyPos=" + body.position() + ", framePoint=" + point
                        + ", framePose=" + fixture.plot().subLevel().logicalPose()
                        + ", binding=" + body.playerCharacterBinding() + ", tracking=" + tracking
                        + ", collisionInfo=" + info + ", collisionInfoFresh=" + freshInfo
                        + ", floorContacts=" + freshFloorContacts[0] + ", mindClaim=" + owner.authority().mindId()
                        + ", handsClaim=" + owner.authority().handIds() + ", sequence=" + owner.intentSequence().get();
                helper.fail(firstFailure[0]); return;
            }
        }, () -> {
            var frameHandle = RigidBodyHandle.of(fixture.plot().subLevel());
            LandingWitness landing = landingWitness(fixture, body);
            if (freshFloorContacts[0] < 2
                    || !observedDownwardMotion[0]
                    || Math.abs(frameHandle.getLinearVelocity(new Vector3d()).y) > .15
                    || !fixture.level().getBlockState(landing.terrainBlock()).is(Blocks.STONE)
                    || Math.abs(landing.floorBottom().y - (landing.terrainBlock().getY() + 1)) > .15
                    || Math.abs(landing.bodyFoot().y - landing.floorTop().y) > .4) {
                helper.fail("offline fall did not witness frame impact on authored terrain and settle before Ready; contacts="
                        + freshFloorContacts[0] + ", downwardObserved=" + observedDownwardMotion[0]
                        + ", latest=" + ((EntityMovementExtension) (Object) body).sable$getCollisionInfo()
                        + ", tracking=" + ((EntityMovementExtension) (Object) body).sable$getTrackingSubLevel()
                        + ", frameVelocity=" + frameHandle.getLinearVelocity(new Vector3d())
                        + ", framePose=" + fixture.plot().subLevel().logicalPose()
                        + ", projectedFloorBottom=" + landing.floorBottom() + ", projectedFloorTop=" + landing.floorTop()
                        + ", bodyFoot=" + landing.bodyFoot() + ", terrainContact=" + landing.terrainBlock()
                        + "/" + fixture.level().getBlockState(landing.terrainBlock())
                        + ", firstFailure=" + firstFailure[0]); return;
            }
            if (Math.abs(body.position().y - startY) < .1
                    || fixture.plot().subLevel().logicalPose().position().y >= fallingStartFrameY) {
                helper.fail("offline body/frame did not continue its fall"); return;
            }
            var before = LoadedFrameRayQuery.currentActorPoint(body.level(), body);
            LifecycleHandActorFixture.reconnect(helper, owner, joined -> {
                BoundedGameTestWait.observeServerTicks(helper, 1, event -> { }, () -> {
                var ready = LoadedFrameRayQuery.currentActorPoint(body.level(), body);
                var handsAfter = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                try {
                    SavedLifecycleProfile active = context.currentAccountProfile(owner.actor().getUUID()).orElseThrow();
                    if (joined.authority().body() != body || !joined.authority().mindId().equals(mind)
                            || !joined.authority().handIds().equals(hands) || handsAfter == null
                            || handsAfter.token(issued.hand()).filter(issued.token()::equals).isEmpty()
                            || handsAfter.stackCopy(issued.hand()).filter(stack -> ItemStack.matches(stack, issued.stack())).isEmpty()
                            || before == null || ready == null || !frame.equals(ready.frameId())
                            || Math.abs(before.storagePoint().y - ready.storagePoint().y) > .25
                            || Math.abs(body.position().y - ready.worldPoint().y) > .25
                            || active.state() != SavedLifecycleProfile.State.ACTIVE || !active.bodyId().equals(body.getUUID())) {
                        helper.fail("Ready did not reattach to physically continued body/cabin pose: before=" + before
                                + ", after=" + ready + ", world=" + body.position() + ", savedOffline="
                                + context.currentAccountProfile(owner.actor().getUUID()).map(SavedLifecycleProfile::location).orElse(null)
                                + ", frame=" + frame + ", bodySame=" + (joined.authority().body() == body)
                                + ", mindSame=" + joined.authority().mindId().equals(mind) + ", handsSame="
                                + joined.authority().handIds().equals(hands) + ", itemOwned="
                                + (handsAfter != null && handsAfter.token(issued.hand()).filter(issued.token()::equals).isPresent())); return;
                    }
                    exerciseAirlockAfterReady(helper, fixture, body, joined, issued);
                } catch (java.io.IOException failure) { helper.fail("cannot inspect rejoined lifecycle profile: " + failure); }
                });
            });
        });
    }

    /** Projects the actual authored floor block beneath the body through the settled native frame pose. */
    private static LandingWitness landingWitness(MovingCabinAuthorityFixture.Ready fixture,
            PlayerCharacterHarnessEntity body) {
        BlockPos cabinOrigin = fixture.cabin().origin();
        var pose = fixture.plot().subLevel().logicalPose();
        var floorBottom = pose.transformPosition(new Vector3d(cabinOrigin.getX() + 2.5,
                cabinOrigin.getY(), cabinOrigin.getZ() + 2.5));
        var floorTop = pose.transformPosition(new Vector3d(cabinOrigin.getX() + 2.5,
                cabinOrigin.getY() + 1, cabinOrigin.getZ() + 2.5));
        BlockPos terrain = BlockPos.containing(floorBottom.x, floorBottom.y - .01, floorBottom.z);
        var actorPoint = LoadedFrameRayQuery.currentActorPoint(body.level(), body);
        Vec3 foot = actorPoint == null ? body.position() : actorPoint.worldPoint();
        return new LandingWitness(floorBottom, floorTop, terrain, foot);
    }

    private record LandingWitness(Vector3d floorBottom, Vector3d floorTop, BlockPos terrainBlock, Vec3 bodyFoot) { }

    private static void exerciseAirlockAfterReady(GameTestHelper helper,
            MovingCabinAuthorityFixture.Ready fixture, PlayerCharacterHarnessEntity body,
            LifecycleHandActorFixture.Ready joined, FixtureHeldTool.Issued issued) {
        BlockPos hatch = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.AIRLOCK);
        if (!(fixture.level().getBlockState(hatch).getBlock() instanceof AirlockBlock)
                || !(fixture.level().getBlockEntity(hatch) instanceof AirlockBlockEntity door)) {
            helper.fail("real cabin hatch/root missing after Ready: " + hatch); return;
        }
        var storage = LoadedFrameRayQuery.currentActorPoint(body.level(), body);
        var target = LoadedFrameRayQuery.currentTargetPoint(fixture.level(), hatch);
        if (storage == null || target == null || !fixture.plot().engineId().equals(storage.frameId())
                || !fixture.plot().engineId().equals(target.frameId())) {
            helper.fail("post-Ready eye/door projection is not on the same live cabin frame: actor="
                    + storage + ", target=" + target); return;
        }
        var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(body)
                || hands.token(issued.hand()).filter(issued.token()::equals).isEmpty()
                || !hands.activeHand().equals(issued.hand())) {
            helper.fail("same issued active hand unavailable for post-Ready hatch request"); return;
        }
        long viewSequence = joined.viewTo(body, target.worldPoint());
        Vec3 aim = target.worldPoint().subtract(body.getEyePosition()).normalize();
        BoundedGameTestWait.await(helper, 12, () -> "Ready actor view did not aim at projected hatch: " + target,
                () -> joined.drainAppliedSequence() >= viewSequence && body.getLookAngle().dot(aim) > .995,
                () -> dispatchHatchE(helper, fixture, body, joined, door, hatch));
    }

    private static void dispatchHatchE(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
            PlayerCharacterHarnessEntity body, LifecycleHandActorFixture.Ready joined,
            AirlockBlockEntity door, BlockPos hatch) {
        var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(body)) {
            helper.fail("post-Ready BodyBlockUse hand state is missing/incompatible"); return;
        }
        var target = LoadedFrameRayQuery.currentTargetPoint(fixture.level(), hatch);
        Direction face = reachableFace(fixture.level(), body, hatch);
        var projectedEye = target == null ? java.util.List.<LoadedFrameRayQuery.FrameRayContext>of()
                : LoadedFrameRayQuery.frameRayContexts(fixture.level(), body.getEyePosition(), target.worldPoint(), 0);
        if (face == null || !BodyBlockUseTargetProbe.withinReach(fixture.level(), body, hatch)
                || BodyBlockUseTargetProbe.probe(fixture.level(), body, hatch, face) == null
                || projectedEye.stream().noneMatch(context -> fixture.plot().engineId().equals(context.frameId()))) {
            helper.fail("post-Ready hatch eye/reach ray failed: eye=" + body.getEyePosition()
                    + ", hatch=" + target + ", face=" + face + ", reach="
                    + BodyBlockUseTargetProbe.withinReach(fixture.level(), body, hatch) + ", projectedEye="
                    + projectedEye.stream().map(context -> context.frameId() + "/" + context.localFrom()).toList()); return;
        }
        requirePaidReady(door, "before E");
        var request = new BodyBlockUseRequest(joined.epoch(), 1, hands.revision(), hands.activeHand(),
                hands.token(hands.activeHand()).orElse(null), hatch, face, false,
                BodyBlockUseRequest.Intent.ACTIVATE_WORLD);
        String preDispatch = preDispatchSnapshot(fixture, joined, body, hands, request, face);
        joined.dispatch(request);
        java.util.List<String> payloadEvidence = new java.util.ArrayList<>();
        BodyBlockUseResult[] response = {null};
        BoundedGameTestWait.await(helper, 10,
                () -> "registered E request has no matching response epoch=" + request.epoch()
                        + " sequence=" + request.sequence() + " payloads=" + payloadEvidence
                        + " preDispatch=" + preDispatch
                        + ", " + doorBrief(fixture, door, body, hatch),
                () -> (response[0] = joined.pollBlockUseResult(request.epoch(), request.sequence(), payloadEvidence)) != null,
                () -> {
                    if (response[0].outcome() != BodyBlockUseResult.Outcome.NO_CHANGE
                            || door.state().phase() != AirlockState.DoorPhase.OPENING) {
                        helper.fail("normal authenticated ACTIVATE_WORLD result/phase mismatch epoch="
                                + response[0].epoch() + " sequence=" + response[0].sequence()
                                + " outcome=" + response[0].outcome() + " observed=" + payloadEvidence
                                + " preDispatch=" + preDispatch
                                + ", " + doorBrief(fixture, door, body, hatch)); return;
                    }
                    BoundedGameTestWait.await(helper, 140, () -> "hatch did not complete E-open/auto-close cycle: "
                                    + doorBrief(fixture, door, body, hatch),
                            () -> door.state().phase() == AirlockState.DoorPhase.CLOSED,
                            () -> bumpThroughHatch(helper, fixture, body, joined, door, hatch));
                });
    }

    private static void bumpThroughHatch(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
            PlayerCharacterHarnessEntity body, LifecycleHandActorFixture.Ready joined,
            AirlockBlockEntity door, BlockPos hatch) {
        var point = LoadedFrameRayQuery.currentActorPoint(body.level(), body);
        if (point == null || !fixture.plot().engineId().equals(point.frameId())
                || !BodyBlockUseTargetProbe.withinReach(fixture.level(), body, hatch)) {
            helper.fail("before bump projected eye/reach is invalid: " + point); return;
        }
        requirePaidReady(door, "before bump");
        if (door.state().phase() != AirlockState.DoorPhase.CLOSED || door.state().boltsDown()) {
            helper.fail("bump precondition hatch must be closed with bolts retracted: "
                    + doorBrief(fixture, door, body, hatch)); return;
        }
        // Use the joined owner's registered view/wish intent; never move the body directly from
        // the post-tick fixture callback (which would bypass the owned physical-step budget).
        final boolean[] openingWitnessed = {false};
        final boolean[] neutralApplied = {false};
        final boolean[] successScheduled = {false};
        final long[] neutralSequence = {-1};
        final com.juicyslew.moonstation14.ms14.doors.NativeAirlockContact[] hatchContactWitness = {null};
        final Object[] previousCollisionInfo = {((EntityMovementExtension) (Object) body).sable$getCollisionInfo()};
        final long[] lastSequence = {joined.intentSequence().get()};
        var mind = joined.authority().mindId();
        var handIds = joined.authority().handIds();
        var binding = body.playerCharacterBinding();
        joined.setWish(0, com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessMotor.INPUT_QUANTIZATION);
        long aimSequence = joined.viewTo(body, LoadedFrameRayQuery.currentTargetPoint(fixture.level(), hatch).worldPoint());
        lastSequence[0] = aimSequence;
        BoundedGameTestWait.observeServerTicks(helper, 40, event -> {
            if (successScheduled[0]) return;
            long tick = helper.getLevel().getServer().getTickCount();
            var live = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var control = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(joined.actor()).orElse(null);
            if (control == null || control.body() != body || control.epoch() != joined.epoch()
                    || !control.mindId().equals(mind) || !control.handIds().equals(handIds)
                    || !binding.equals(body.playerCharacterBinding()) || live == null || !live.compatible(body)
                    || !((Object) body instanceof com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob marker)
                    || !marker.moonstation14$physicalStepConsumed(helper.getLevel(), tick)
                    || joined.drainAppliedSequence() < lastSequence[0]) {
                helper.fail("joined normal input did not consume one owned physical step with exact Ready authority: tick="
                        + tick + ", ack=" + joined.drainAppliedSequence() + ", sequence=" + lastSequence[0]
                        + ", authority=" + control + ", " + doorBrief(fixture, door, body, hatch)); return;
            }
            if (door.state().phase() == AirlockState.DoorPhase.OPENING) {
                if (openingWitnessed[0]) {
                    // Let later OPENING/OPEN/CLOSED phases be irrelevant; only the witnessed
                    // neutral intent's acknowledged owned step may complete this regression.
                    neutralApplied[0] = true;
                    successScheduled[0] = true;
                    BoundedGameTestWait.succeedAfterObservation(helper);
                    return;
                }
                Object raw = ((EntityMovementExtension) (Object) body).sable$getCollisionInfo();
                if (raw instanceof SubLevelEntityCollision.CollisionInfo
                        && raw != previousCollisionInfo[0] && raw instanceof NativeAirlockContactAccess capture) {
                    hatchContactWitness[0] = capture.moonstation14$airlockContacts().stream()
                            .filter(contact -> contact.frame().getUniqueId().equals(fixture.plot().engineId())
                                    && (contact.storagePos().equals(hatch) || contact.storagePos().equals(hatch.above()))
                                    && contact.state().getBlock() instanceof AirlockBlock)
                            .findFirst().orElse(null);
                }
                if (hatchContactWitness[0] == null) {
                    helper.fail("hatch opened without fresh SDK-captured contact for the actual cabin hatch: info="
                            + raw + ", " + doorBrief(fixture, door, body, hatch)); return;
                }
                openingWitnessed[0] = true;
                // Exactly one input this observer tick: replace forward wish with neutral while
                // preserving the current hatch-directed view and the joined epoch.
                joined.setWish(0, 0);
                neutralSequence[0] = joined.neutralIntent(body);
                lastSequence[0] = neutralSequence[0];
                return;
            }
            if (openingWitnessed[0]) {
                // The opening witness already occurred. Do not fail on legitimate later phases,
                // and do not send more movement intents while waiting for neutral application.
                if (joined.drainAppliedSequence() >= neutralSequence[0]) {
                    neutralApplied[0] = true;
                    successScheduled[0] = true;
                    BoundedGameTestWait.succeedAfterObservation(helper);
                }
                return;
            }
            if (door.state().phase() != AirlockState.DoorPhase.CLOSED) {
                helper.fail("unexpected hatch phase before normal-input bump contact: "
                        + doorBrief(fixture, door, body, hatch)); return;
            }
            var current = LoadedFrameRayQuery.currentActorPoint(body.level(), body);
            var projectedDoor = LoadedFrameRayQuery.currentTargetPoint(fixture.level(), hatch);
            var eyeRay = projectedDoor == null ? java.util.List.<LoadedFrameRayQuery.FrameRayContext>of()
                    : LoadedFrameRayQuery.frameRayContexts(fixture.level(), body.getEyePosition(), projectedDoor.worldPoint(), 0);
            if (current == null || !fixture.plot().engineId().equals(current.frameId())
                    || eyeRay.stream().noneMatch(context -> fixture.plot().engineId().equals(context.frameId()))
                    || !BodyBlockUseTargetProbe.withinReach(fixture.level(), body, hatch)) {
                helper.fail("before normal-input bump eye/reach/frame projection failed: actor=" + current
                        + ", rayFrames=" + eyeRay.stream().map(LoadedFrameRayQuery.FrameRayContext::frameId).toList()
                        + ", " + doorBrief(fixture, door, body, hatch)); return;
            }
            requirePaidReady(door, "before joined input");
            if (body.getLookAngle().dot(projectedDoor.worldPoint().subtract(body.getEyePosition()).normalize()) < .99) {
                helper.fail("joined view intent no longer aims at projected hatch"); return;
            }
            previousCollisionInfo[0] = ((EntityMovementExtension) (Object) body).sable$getCollisionInfo();
            lastSequence[0] = joined.neutralIntent(body);
        }, () -> {
            if (!openingWitnessed[0] || !neutralApplied[0]) {
                helper.fail("ordinary joined movement input never opened cabin hatch within bounded approach: "
                        + doorBrief(fixture, door, body, hatch)); return;
            }
        });
    }

    private static Direction reachableFace(net.minecraft.server.level.ServerLevel level,
            PlayerCharacterHarnessEntity body, BlockPos target) {
        for (Direction face : Direction.values())
            if (BodyBlockUseTargetProbe.probe(level, body, target, face) != null) return face;
        return null;
    }

    private static String preDispatchSnapshot(MovingCabinAuthorityFixture.Ready fixture,
            LifecycleHandActorFixture.Ready joined, PlayerCharacterHarnessEntity body,
            com.juicyslew.moonstation14.ms14.hands.live.LiveHands hands,
            BodyBlockUseRequest request, Direction face) {
        var authority = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(joined.actor()).orElse(null);
        var probe = BodyBlockUseTargetProbe.probe(fixture.level(), body, request.target(), face);
        return "authority=" + (authority != null) + ",authEpoch=" + (authority == null ? -1 : authority.epoch())
                + ",requestEpoch=" + request.epoch() + ",handsCompatible=" + hands.compatible(body)
                + ",active=" + hands.activeHand() + ",requestedHand=" + request.handId()
                + ",revision=" + hands.revision() + "/" + request.expectedRevision()
                + ",tokenMatch=" + (request.expectedToken() != null
                        && hands.token(request.handId()).filter(request.expectedToken()::equals).isPresent())
                + ",canAct=" + com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(body)
                + ",probeFrame=" + (probe == null ? null : probe.frameId())
                + ",eye=" + body.getEyePosition() + ",look=" + body.getLookAngle();
    }

    private static void requirePaidReady(AirlockBlockEntity door, String before) {
        var level = (net.minecraft.server.level.ServerLevel) door.getLevel();
        var meter = PowerRuntime.airlockPower(level, door);
        double demand = door.prototype().apcPowerReceiver().requestedWatts();
        if (!meter.known() || meter.deliveredWatts() != demand || !door.state().powered()
                || door.state().receiverPowerDisabled() || door.state().boltsDown()
                || door.state().phase() != AirlockState.DoorPhase.CLOSED)
            throw new IllegalStateException(before + " requires fresh paid delivery, powered closed hatch and retracted bolts: "
                    + "meterKnown=" + meter.known() + ", delivered=" + meter.deliveredWatts()
                    + ", demand=" + demand + ", phase=" + door.state().phase()
                    + ", powered=" + door.state().powered() + ", receiverDisabled="
                    + door.state().receiverPowerDisabled() + ", boltsDown=" + door.state().boltsDown());
    }

    private static String doorBrief(MovingCabinAuthorityFixture.Ready fixture,
            AirlockBlockEntity door, PlayerCharacterHarnessEntity body, BlockPos hatch) {
        var meter = PowerRuntime.airlockPower(fixture.level(), door);
        var actor = LoadedFrameRayQuery.currentActorPoint(body.level(), body);
        var target = LoadedFrameRayQuery.currentTargetPoint(fixture.level(), hatch);
        return "hatch=" + hatch + " phase=" + door.state().phase() + " powered=" + door.state().powered()
                + " boltsDown=" + door.state().boltsDown() + " meterKnown=" + meter.known()
                + " delivered=" + meter.deliveredWatts() + " demand=" + door.prototype().apcPowerReceiver().requestedWatts()
                + " actorFrame=" + (actor == null ? null : actor.frameId()) + " targetFrame="
                + (target == null ? null : target.frameId()) + " reach="
                + BodyBlockUseTargetProbe.withinReach(fixture.level(), body, hatch);
    }

    private static boolean markerState(PlayerCharacterHarnessEntity body, GameTestHelper helper, long tick) {
        return ((Object) body instanceof com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob marker)
                && marker.moonstation14$physicalStepConsumed(helper.getLevel(), tick);
    }
}
