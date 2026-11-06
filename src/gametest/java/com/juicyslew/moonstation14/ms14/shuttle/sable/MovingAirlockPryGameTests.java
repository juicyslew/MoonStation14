package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity;
import com.juicyslew.moonstation14.ms14.doors.AirlockState;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.FixtureHeldTool;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedBlockGetter;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.tools.ToolQualities;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperationState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Registered crowbar PRY completion while native Sable movement carries its authorized HUMAN. */
@GameTestTags({"shuttle-airlock-access", "shuttle", "shuttle-pry-power-diagnostic"})
@PrefixGameTestTemplate(false)
public final class MovingAirlockPryGameTests {
    private MovingAirlockPryGameTests() { }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 850,
            batch = "shuttle_moving_airlock_pry")
    public static void crowbarPryCompletesDuringNativeCarry(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(760, 300, 640), "moving-pry-owner",
                3.5, 1, 3.5, 24, (fixture, body) -> {
                    var level = fixture.level();
                    BlockPos doorPos = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.AIRLOCK);
                    if (!(level.getBlockEntity(doorPos) instanceof AirlockBlockEntity door) || door.prototype() == null)
                        throw new GameTestAssertException("authored lower airlock owner/prototype missing");
                    BlockPos apcPos = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.APC);
                    if (!(level.getBlockEntity(apcPos) instanceof PowerDeviceBlockEntity apc)
                            || !apc.toggleBreakerAuthorized(true, 0) || apc.breakerClosed())
                        throw new GameTestAssertException("normal APC breaker mutation failed to disable cabin supply");
                    BoundedGameTestWait.await(helper, 50, () -> "airlock supply did not settle known at zero: "
                                    + PowerRuntime.airlockPower(level, door),
                            () -> PowerRuntime.airlockPower(level, door).known()
                                    && PowerRuntime.airlockPower(level, door).deliveredWatts() == 0,
                            () -> beginPry(helper, fixture, body, door, doorPos, false, false));
                });
    }

    @GameTestTags({"shuttle-owner-regression", "shuttle-pry-power-context"})
    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 850,
            batch = "shuttle_moving_airlock_pry_walk")
    public static void crowbarPryCancelsOnOwnWalkOnSameFrame(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(800, 300, 640), "moving-pry-walk-owner",
                3.5, 1, 3.5, 24, (fixture, body) -> {
                    var level = fixture.level();
                    BlockPos doorPos = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.AIRLOCK);
                    if (!(level.getBlockEntity(doorPos) instanceof AirlockBlockEntity door) || door.prototype() == null)
                        throw new GameTestAssertException("authored lower airlock owner/prototype missing");
                    if (!door.applyWire(door.identity(), door.state().revision(),
                            AirlockState.Wire.POWER_ONE, AirlockState.WireAction.CUT)
                            || !door.applyWire(door.identity(), door.state().revision(),
                            AirlockState.Wire.POWER_TWO, AirlockState.WireAction.CUT))
                        throw new GameTestAssertException("both-power-wires-cut fixture premise was rejected");
                    var state = door.state();
                    var supply = PowerRuntime.airlockPower(level, door);
                    if (!state.powerWireOneCut() || !state.powerWireTwoCut() || !state.receiverPowerDisabled()
                            || !supply.known() || supply.deliveredWatts() != 0
                            || !PowerRuntime.airlockPowerDiagnostic(level, door).contains("receiver=disabled"))
                        throw new GameTestAssertException("both-power-wires-cut did not establish known-zero receiver premise: "
                                + supply + ", " + PowerRuntime.airlockPowerDiagnostic(level, door));
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                            "[moving-airlock-pry-power] fixture-premise=both-power-wires-cut root={} state={} power={}",
                            door.identity(), state, supply);
                    beginPry(helper, fixture, body, door, doorPos, true, false);
                });
    }

    @GameTestTags({"shuttle-airborne-query", "shuttle-airlock-access", "shuttle"})
    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 850,
            batch = "shuttle_moving_airlock_pry_freefall")
    public static void freefallPryCompletesWithCurrentPoseRayBounds(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(880, 300, 640), "moving-pry-freefall-owner",
                3.5, 1, 3.5, (fixture, body) -> {
                    var level = fixture.level();
                    BlockPos doorPos = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.AIRLOCK);
                    if (!(level.getBlockEntity(doorPos) instanceof AirlockBlockEntity door) || door.prototype() == null)
                        throw new GameTestAssertException("authored lower airlock owner/prototype missing");
                    BlockPos apcPos = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.APC);
                    if (!(level.getBlockEntity(apcPos) instanceof PowerDeviceBlockEntity apc)
                            || !apc.toggleBreakerAuthorized(true, 0) || apc.breakerClosed())
                        throw new GameTestAssertException("normal APC breaker mutation failed to disable cabin supply");
                    BoundedGameTestWait.await(helper, 50, () -> "airlock supply did not settle known at zero: "
                                    + PowerRuntime.airlockPower(level, door),
                            () -> PowerRuntime.airlockPower(level, door).known()
                                    && PowerRuntime.airlockPower(level, door).deliveredWatts() == 0,
                            () -> beginPry(helper, fixture, body, door, doorPos, false, true));
                });
    }

    private static void beginPry(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                                 PlayerCharacterHarnessEntity body, AirlockBlockEntity door, BlockPos doorPos,
                                 boolean ownWalk, boolean freeFall) {
        var level = fixture.level();
        var authority = fixture.owner().authority();
        UUID bodyId = body.getUUID(), mindId = authority.mindId().value(), frameId = fixture.plot().engineId();
        long epoch = authority.epoch();
        var hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || hands.activeHand().isBlank())
            throw new GameTestAssertException("lifecycle body lacks issued active hand");
        var issued = FixtureHeldTool.issue(fixture.owner().actor(), hands.activeHand(), new ItemStack(ModItems.CROWBAR.get()));
        double speed = ToolQualities.prySpeed(issued.stack());
        if (!Double.isFinite(speed) || speed <= 0 || !door.state().allowsPry(false))
            throw new GameTestAssertException("resolved crowbar/airlock state is not pry eligible: speed=" + speed
                    + ", state=" + door.state());
        int duration = (int) Math.max(1, Math.ceil(5.0 / speed * 20));
        fixture.owner().trackViewToward(body, () -> {
            var point = LoadedFrameRayQuery.currentTargetPoint(level, doorPos);
            return point == null ? null : point.worldPoint();
        });
        long aimSequence = fixture.owner().intentSequence().get();
        BoundedGameTestWait.await(helper, 30, () -> "lifecycle aim/probe did not settle on airlock",
                () -> fixture.owner().drainAppliedSequence() >= aimSequence && hasFace(level, body, doorPos),
                () -> dispatchPry(helper, fixture, body, door, doorPos, issued, duration, bodyId, mindId, epoch, frameId, ownWalk, freeFall));
    }

    private static void dispatchPry(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                                    PlayerCharacterHarnessEntity body, AirlockBlockEntity door, BlockPos doorPos,
                                    FixtureHeldTool.Issued issued, int duration, UUID bodyId, UUID mindId,
                                    long epoch, UUID frameId, boolean ownWalk, boolean freeFall) {
        var level = fixture.level();
        Direction face = face(level, body, doorPos);
        var hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || hands.revision() != issued.revision() || !hands.activeHand().equals(issued.hand())
                || hands.token(issued.hand()).filter(issued.token()::equals).isEmpty()
                || !hands.stackCopy(issued.hand()).filter(stack -> stack.is(ModItems.CROWBAR.get())).isPresent())
            throw new GameTestAssertException("owner-published crowbar hand/token/revision changed");
        var target = BodyBlockUseTargetProbe.probe(level, body, doorPos, face);
        if (target == null || !frameId.equals(target.frameId()))
            throw new GameTestAssertException("actual target probe lost native cabin frame");
        var actorPoint = LoadedFrameRayQuery.currentActorPoint(level, body);
        if (actorPoint == null || !frameId.equals(actorPoint.frameId()))
            throw new GameTestAssertException("actor has no exact native Sable tracking frame");
        var storageStart = actorPoint.storagePoint();
        var frameStart = LoadedFrameRayQuery.currentTargetPoint(level, doorPos);
        Vec3 bodyBefore = body.position();
        long doorRevisionAtStart = door.state().revision();
        var request = new BodyBlockUseRequest(epoch, 1, issued.revision(), issued.hand(), issued.token(),
                doorPos, face, false, BodyBlockUseRequest.Intent.USE);
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                "[moving-airlock-pry-power] stage=admission tick={} root={} body={} frame={} revision={} power={} powerDiagnostic={} topologyReady={} topologyGeneration={}",
                level.getServer().getTickCount(), door.identity(), bodyId, frameId, doorRevisionAtStart,
                PowerRuntime.airlockPower(level, door), PowerRuntime.airlockPowerDiagnostic(level, door),
                com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyReady(level),
                com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(level));
        fixture.owner().dispatch(request);
        String result = fixture.owner().drainBlockUseOutbounds(request.sequence());
        TimedOperation active = find(body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get()), door.identity());
        if (active == null || active.durationTicks() != duration || !active.breakOnMove()
                || !active.kind().equals("airlock-pry") || !door.state().phase().equals(com.juicyslew.moonstation14.ms14.doors.AirlockState.DoorPhase.CLOSED))
            throw new GameTestAssertException("registered BODY USE did not start resolved pry operation: "
                    + result + ", operation=" + active + ", duration=" + duration);
        if (ownWalk) {
            walkUntilCancelled(helper, fixture, body, door, doorPos, active, issued, duration,
                    bodyId, mindId, epoch, frameId, storageStart, frameStart, doorRevisionAtStart);
            return;
        }
        if (freeFall) {
            completePryDuringNativeFreeFall(helper, fixture, body, door, doorPos, active, issued, duration,
                    bodyId, mindId, epoch, frameId, storageStart, frameStart, doorRevisionAtStart);
            return;
        }
        dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(fixture.plot().subLevel())
                .addLinearAndAngularVelocity(new org.joml.Vector3d(2, 0, 2), new org.joml.Vector3d());
        BoundedGameTestWait.await(helper, duration + 18, () -> "registered crowbar pry failed to complete: "
                        + find(body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get()), door.identity()),
                () -> { TimedOperation now = operation(body, active.id());
                    return now != null && now.phase() != TimedOperation.Phase.ACTIVE; }, () -> {
                    TimedOperation done = operation(body, active.id());
                    var point = LoadedFrameRayQuery.currentTargetPoint(level, doorPos);
                    var actorAfter = LoadedFrameRayQuery.currentActorPoint(level, body);
                    var currentHands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    if (done == null || done.phase() != TimedOperation.Phase.COMPLETED || done.durationTicks() != duration
                            || door.state().phase() != com.juicyslew.moonstation14.ms14.doors.AirlockState.DoorPhase.OPENING
                            || level.getBlockEntity(doorPos) != door || !door.identity().toString().equals(done.sourceKey())
                            || point == null || actorAfter == null || !frameId.equals(point.frameId()) || !frameId.equals(actorAfter.frameId())
                            || actorAfter.storagePoint().distanceTo(storageStart) > .3
                            || point.worldPoint().subtract(frameStart.worldPoint()).horizontalDistance() <= .3
                            || body.position().subtract(bodyBefore).horizontalDistance() <= .3
                            || !body.getUUID().equals(bodyId) || !fixture.owner().authority().mindId().value().equals(mindId)
                            || fixture.owner().authority().epoch() != epoch || currentHands == null
                            || currentHands.revision() != issued.revision()
                            || currentHands.token(issued.hand()).filter(issued.token()::equals).isEmpty()
                            || !currentHands.stackCopy(issued.hand()).filter(stack -> stack.is(ModItems.CROWBAR.get())).isPresent()
                            || !HandActorAuthority.revalidate(fixture.owner().actor(), fixture.owner().authority()))
                        throw new GameTestAssertException("pry did not complete during rigid native carry: operation=" + done
                                + ", phase=" + door.state().phase() + ", actor=" + actorAfter + ", actorStart=" + storageStart
                                + ", localDelta=" + (actorAfter == null ? null : actorAfter.storagePoint().distanceTo(storageStart))
                                + ", target=" + point + ", power=" + PowerRuntime.airlockPower(level, door)
                                + ", doorRevision=" + door.state().revision() + "/" + doorRevisionAtStart
                                + ", los=" + com.juicyslew.moonstation14.ms14.doors.AirlockToolLineOfSight.canReachRoot(body, level, doorPos, door, door.identity())
                                + ", ray=" + diagnosticRay(level, body, doorPos)
                                + ", eye=" + body.getEyePosition() + ", posture=" + body.getExistingDataOrNull(ModDataAttachments.POSTURE.get())
                                + ", health=" + body.getHealth()
                                + ", lower=" + level.getBlockState(doorPos) + ", upper=" + level.getBlockState(doorPos.above())
                                + ", lowerChunk=" + level.getChunkSource().getChunkNow(doorPos.getX() >> 4, doorPos.getZ() >> 4).getFullStatus()
                                + ", upperChunk=" + level.getChunkSource().getChunkNow(doorPos.getX() >> 4, doorPos.getZ() >> 4).getFullStatus());
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("[moving airlock pry proof] operation={} durationTicks={} terminalTick={} frameWorldDelta={} actorLocalDelta={} frameId={} door={} phase={} power={}",
                            done.id(), done.durationTicks(), done.terminalTick(), point.worldPoint().distanceTo(frameStart.worldPoint()),
                            actorAfter.storagePoint().distanceTo(storageStart), frameId, door.identity(), door.state().phase(),
                            PowerRuntime.airlockPower(level, door));
                    helper.succeed();
                });
    }

    private static void walkUntilCancelled(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                                          PlayerCharacterHarnessEntity body, AirlockBlockEntity door, BlockPos doorPos,
                                          TimedOperation active, FixtureHeldTool.Issued issued, int duration,
                                          UUID bodyId, UUID mindId, long epoch, UUID frameId, Vec3 storageStart,
                                          LoadedFrameRayQuery.TargetPoint frameStart, long doorRevisionAtStart) {
        var level = fixture.level();
        Vec3 bodyStart = body.position();
        long observationStart = level.getServer().getTickCount();
        final WalkSample[] terminalSample = {null};
        fixture.owner().setWish(-1000, 0);
        BoundedGameTestWait.observeServerTicks(helper, 50, event -> {
                    TimedOperation current = operation(body, active.id());
                    if (current != null && current.phase() != TimedOperation.Phase.ACTIVE && terminalSample[0] == null) {
                        terminalSample[0] = new WalkSample(level.getServer().getTickCount(), current,
                                LoadedFrameRayQuery.currentActorPoint(level, body),
                                 LoadedFrameRayQuery.currentTargetPoint(level, doorPos), body.position(),
                                 PowerRuntime.airlockPower(level, door),
                                 PowerRuntime.airlockPowerDiagnostic(level, door),
                                 com.juicyslew.moonstation14.ms14.doors.AirlockToolLineOfSight
                                        .canReachRoot(body, level, doorPos, door, door.identity()),
                                door.state().revision());
                        fixture.owner().setWish(0, 0);
                    }
                }, () -> {
                    fixture.owner().setWish(0, 0);
                    WalkSample sample = terminalSample[0];
                    if (sample == null) throw new GameTestAssertException("own-walk had no terminal sample within 50 ticks: "
                            + operation(body, active.id()) + ", LOS="
                            + com.juicyslew.moonstation14.ms14.doors.AirlockToolLineOfSight
                                    .canReachRoot(body, level, doorPos, door, door.identity())
                            + ", power=" + PowerRuntime.airlockPower(level, door)
                            + ", powerDiagnostic=" + PowerRuntime.airlockPowerDiagnostic(level, door));
                    TimedOperation cancelled = sample.operation();
                    var actorPoint = sample.actorPoint();
                    var target = sample.targetPoint();
                    var hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    var power = sample.power();
                    double localDelta = actorPoint == null ? Double.NaN : actorPoint.storagePoint().distanceTo(storageStart);
                    double moveBreakDistance = com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem.DEFAULT_MOVE_BREAK_DISTANCE;
                    double worldDelta = sample.bodyWorldPoint().distanceTo(bodyStart);
                    double frameDelta = target == null ? Double.NaN : target.worldPoint().distanceTo(frameStart.worldPoint());
                    if (cancelled.phase() != TimedOperation.Phase.CANCELLED
                            || cancelled.durationTicks() != duration || cancelled.terminalTick() - cancelled.startTick() >= duration
                            || actorPoint == null || !frameId.equals(actorPoint.frameId()) || localDelta <= moveBreakDistance
                            || target == null || !frameId.equals(target.frameId()) || worldDelta <= .3
                            || !sample.lineOfSight() || !power.known() || power.deliveredWatts() != 0
                            || sample.doorRevision() != doorRevisionAtStart
                            || door.state().phase() != com.juicyslew.moonstation14.ms14.doors.AirlockState.DoorPhase.CLOSED
                            || level.getBlockEntity(doorPos) != door || !door.identity().toString().equals(cancelled.sourceKey())
                            || !body.getUUID().equals(bodyId) || !fixture.owner().authority().mindId().value().equals(mindId)
                            || fixture.owner().authority().epoch() != epoch || hands == null
                            || hands.revision() != issued.revision()
                            || hands.token(issued.hand()).filter(issued.token()::equals).isEmpty()
                            || !hands.stackCopy(issued.hand()).filter(stack -> stack.is(ModItems.CROWBAR.get())).isPresent()
                            || !HandActorAuthority.revalidate(fixture.owner().actor(), fixture.owner().authority()))
                        throw new GameTestAssertException("registered pry terminal sample failed own-walk proof: operation="
                                + cancelled + ", terminalTick=" + cancelled.terminalTick() + ", sampleTick=" + sample.sampleTick()
                                + ", localDelta=" + localDelta + ", moveBreakDistance=" + moveBreakDistance
                                + ", worldDelta=" + worldDelta + ", frameDelta=" + frameDelta
                                + ", frame=" + actorPoint + ", target=" + target + ", LOS=" + sample.lineOfSight()
                                + ", power=" + power + ", powerDiagnostic=" + sample.powerDiagnostic()
                                + ", revision=" + sample.doorRevision() + "/" + doorRevisionAtStart
                                + ", phase=" + door.state().phase() + ", bodyIdentity=" + body.getUUID() + "/" + bodyId
                                + ", observationStartTick=" + observationStart);
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("[moving airlock pry own-walk proof] operation={} durationTicks={} terminalTick={} sampleTick={} localDelta={} worldDelta={} frameWorldDelta={} frameId={} LOS={} power={} powerDiagnostic={} phase={}",
                            cancelled.id(), duration, cancelled.terminalTick(), sample.sampleTick(), localDelta,
                            worldDelta, frameDelta, frameId, sample.lineOfSight(), power, sample.powerDiagnostic(), door.state().phase());
                    BoundedGameTestWait.succeedAfterObservation(helper);
                });
    }

    private record WalkSample(long sampleTick, TimedOperation operation,
                              LoadedFrameRayQuery.ActorPoint actorPoint,
                              LoadedFrameRayQuery.TargetPoint targetPoint, Vec3 bodyWorldPoint,
                              PowerRuntime.VentMeterObservation power, String powerDiagnostic,
                              boolean lineOfSight, long doorRevision) { }

    private static void completePryDuringNativeFreeFall(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                                             PlayerCharacterHarnessEntity body, AirlockBlockEntity door, BlockPos doorPos,
                                             TimedOperation active, FixtureHeldTool.Issued issued, int duration,
                                             UUID bodyId, UUID mindId, long epoch, UUID frameId, Vec3 storageStart,
                                             LoadedFrameRayQuery.TargetPoint frameStart, long doorRevisionAtStart) {
        var level = fixture.level();
        Vec3 bodyStart = body.position();
        var firstMiss = new java.util.concurrent.atomic.AtomicReference<String>();
        // One bounded diagnostic snapshot is recorded if the former free-fall rejection recurs.
        BoundedGameTestWait.safeOnEachTick(helper, () -> {
            if (firstMiss.get() != null || operation(body, active.id()) == null
                    || operation(body, active.id()).phase() != TimedOperation.Phase.ACTIVE) return;
            if (!com.juicyslew.moonstation14.ms14.doors.AirlockToolLineOfSight
                    .canReachRoot(body, level, doorPos, door, door.identity()))
                firstMiss.compareAndSet(null, queryDiagnostic(level, body, doorPos, fixture.plot().subLevel()));
        });
        // This is the intentional free-fall repro: no corridor, gravity changes, or repeated position writes.
        SableFixtureScene.clearStoneSupportPad(level, fixture.scene());
        dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(fixture.plot().subLevel())
                .addLinearAndAngularVelocity(new org.joml.Vector3d(2, 0, 2), new org.joml.Vector3d());
        BoundedGameTestWait.await(helper, duration + 18, () -> "free-fall pry did not complete: " + operation(body, active.id()),
                () -> { TimedOperation current = operation(body, active.id());
                    return current != null && current.phase() != TimedOperation.Phase.ACTIVE; }, () -> {
                    TimedOperation terminal = operation(body, active.id());
                    var actor = LoadedFrameRayQuery.currentActorPoint(level, body);
                    var target = LoadedFrameRayQuery.currentTargetPoint(level, doorPos);
                    var power = PowerRuntime.airlockPower(level, door);
                    double localDelta = actor == null ? Double.NaN : actor.storagePoint().distanceTo(storageStart);
                    boolean los = com.juicyslew.moonstation14.ms14.doors.AirlockToolLineOfSight
                            .canReachRoot(body, level, doorPos, door, door.identity());
                    var hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    if (terminal == null || terminal.phase() != TimedOperation.Phase.COMPLETED
                            || terminal.durationTicks() != duration || actor == null || !frameId.equals(actor.frameId())
                            || localDelta > .3 || target == null || !frameId.equals(target.frameId())
                            || target.worldPoint().distanceTo(frameStart.worldPoint()) <= .3
                            || power.known() == false || power.deliveredWatts() != 0
                            || door.state().revision() <= doorRevisionAtStart
                            || door.state().phase() != com.juicyslew.moonstation14.ms14.doors.AirlockState.DoorPhase.OPENING
                            || body.getHealth() <= 0 || !body.isAlive() || body.isNoGravity() || body.noPhysics
                            || firstMiss.get() != null || !los
                            || hands == null || hands.revision() != issued.revision()
                            || hands.token(issued.hand()).filter(issued.token()::equals).isEmpty()
                            || !hands.stackCopy(issued.hand()).filter(stack -> stack.is(ModItems.CROWBAR.get())).isPresent()
                            || !body.getUUID().equals(bodyId) || !fixture.owner().authority().mindId().value().equals(mindId)
                            || fixture.owner().authority().epoch() != epoch
                            || !HandActorAuthority.revalidate(fixture.owner().actor(), fixture.owner().authority()))
                        throw new GameTestAssertException("free-fall pry failed its current-pose ray bounds proof: op="
                                + terminal + ", actor=" + actor + ", target=" + target + ", localDelta=" + localDelta
                                + ", LOS=" + los + ", firstMiss=" + firstMiss.get() + ", power=" + power
                                + ", doorRevision=" + door.state().revision() + "/" + doorRevisionAtStart
                                + ", phase=" + door.state().phase() + ", eye=" + body.getEyePosition()
                                + ", health=" + body.getHealth() + ", posture=" + body.getExistingDataOrNull(ModDataAttachments.POSTURE.get()));
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("[airborne pry query proof] completedAfterTicks={} frameWorldDelta={} localDelta={} feet={} eye={} eyeHeight={} health={} onGround={} frameId={} LOS={} power={} doorRevision={} formerMissObserved={} query={}",
                            terminal.terminalTick() - terminal.startTick(), target.worldPoint().distanceTo(frameStart.worldPoint()),
                            localDelta, body.position(), body.getEyePosition(), body.getEyeHeight(), body.getHealth(), body.onGround(),
                            frameId, los, power, door.state().revision(), firstMiss.get(),
                            queryDiagnostic(level, body, doorPos, fixture.plot().subLevel()));
                    helper.succeed();
                });
    }

    private static String queryDiagnostic(net.minecraft.server.level.ServerLevel level,
                                          PlayerCharacterHarnessEntity body, BlockPos root,
                                          dev.ryanhcode.sable.sublevel.ServerSubLevel expected) {
        var target = LoadedFrameRayQuery.currentTargetPoint(level, root);
        var upper = LoadedFrameRayQuery.currentTargetPoint(level, root.above());
        if (target == null || upper == null) return "missing targets root=" + target + ", upper=" + upper;
        Vec3 from = body.getEyePosition(), to = target.worldPoint();
        var actual = LoadedFrameRayQuery.clip(level, from, to, 16, .125, 1,
                (getter, a, b) -> new ClipContext(a, b, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, body));
        var pose = level instanceof LevelPoseProviderExtension provider ? provider.sable$getPose(expected) : expected.logicalPose();
        Vec3 storageFrom = pose.transformPositionInverse(from), storageTo = pose.transformPositionInverse(to);
        AABB storageBounds = new BoundingBox3d(expected.getPlot().getBoundingBox()).toMojang().inflate(1.125);
        String storageClip;
        try {
            var storage = LoadedBlockGetter.forFrame(level, expected.getPlot(), storageBounds, 16)
                    .clip(new ClipContext(storageFrom, storageTo, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, body));
            storageClip = storage.getType() + "/" + storage.getBlockPos() + "/" + storage.getDirection()
                    + "/" + storage.getLocation() + "/" + storage.getBlockPos().equals(root);
        } catch (LoadedBlockGetter.UnknownChunk unknown) {
            storageClip = "UNKNOWN_CHUNK";
        }
        var broadphaseBox = new BoundingBox3d(from, to).expand(.125);
        var intersecting = new java.util.ArrayList<String>();
        for (var sub : Sable.HELPER.getAllIntersecting(level, broadphaseBox))
            intersecting.add(sub.getUniqueId() + ":" + sub.boundingBox().toMojang());
        var world = actual.worldHit();
        return "actual=" + actual.state() + ", world=" + (world == null ? null : world.getType() + "/" + world.getBlockPos()
                + "/" + level.getBlockState(world.getBlockPos())) + ", frame=" + actual.frameHit()
                + ", expectedCandidate=" + intersecting.stream().anyMatch(value -> value.startsWith(expected.getUniqueId().toString()))
                + ", candidates=" + intersecting + ", expectedId=" + expected.getUniqueId()
                + ", logicalPose=" + expected.logicalPose() + ", currentPose=" + pose
                + ", nativeBounds=" + expected.boundingBox().toMojang() + ", plotBounds=" + expected.getPlot().getBoundingBox()
                + ", from=" + from + ", to=" + to + ", localFrom=" + storageFrom + ", localTo=" + storageTo
                + ", storageClip=" + storageClip + ", rootState=" + level.getBlockState(root)
                + ", rootShape=" + level.getBlockState(root).getShape(level, root, net.minecraft.world.phys.shapes.CollisionContext.empty()).bounds()
                + ", upperState=" + level.getBlockState(root.above())
                + ", upperShape=" + level.getBlockState(root.above()).getShape(level, root.above(), net.minecraft.world.phys.shapes.CollisionContext.empty()).bounds()
                + ", rootFrame=" + target.frameId() + ", upperFrame=" + upper.frameId()
                + ", eyeRootDistanceSq=" + from.distanceToSqr(to);
    }

    private static TimedOperation find(TimedOperationState state, UUID id) {
        return state == null ? null : state.operations().stream().filter(value -> value.phase() == TimedOperation.Phase.ACTIVE
                && value.kind().equals("airlock-pry") && value.sourceKey().equals(id.toString())).findFirst().orElse(null);
    }
    private static TimedOperation operation(PlayerCharacterHarnessEntity body, long id) {
        var state = body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get());
        return state == null ? null : state.operations().stream().filter(value -> value.id() == id).findFirst().orElse(null);
    }
    private static Direction face(net.minecraft.server.level.ServerLevel level,
                                 net.minecraft.world.entity.LivingEntity body, BlockPos pos) {
        for (Direction face : Direction.values()) if (BodyBlockUseTargetProbe.probe(level, body, pos, face) != null) return face;
        throw new GameTestAssertException("native body ray failed to hit airlock target " + LoadedFrameRayQuery.currentTargetPoint(level, pos));
    }
    private static boolean hasFace(net.minecraft.server.level.ServerLevel level,
                                   net.minecraft.world.entity.LivingEntity body, BlockPos pos) {
        for (Direction face : Direction.values()) if (BodyBlockUseTargetProbe.probe(level, body, pos, face) != null) return true;
        return false;
    }
    private static Object diagnosticRay(net.minecraft.server.level.ServerLevel level,
                                       net.minecraft.world.entity.LivingEntity body, BlockPos pos) {
        var root = LoadedFrameRayQuery.currentTargetPoint(level, pos);
        var upper = LoadedFrameRayQuery.currentTargetPoint(level, pos.above());
        if (root == null || upper == null) return "missing target points root=" + root + ", upper=" + upper;
        Vec3 eye = body.getEyePosition(), target = root.worldPoint();
        var clip = LoadedFrameRayQuery.clip(level, eye, target, 16, .125, 1,
                (getter, from, to) -> new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, body));
        var world = clip.worldHit();
        var frame = clip.frameHit();
        return "state=" + clip.state() + ", world=" + (world == null ? null : world.getType() + "/" + world.getBlockPos()
                + "/" + level.getBlockState(world.getBlockPos())) + ", frame=" + frame + ", root=" + root + ", upper=" + upper
                + ", eyeRootDistSq=" + eye.distanceToSqr(target);
    }
}
