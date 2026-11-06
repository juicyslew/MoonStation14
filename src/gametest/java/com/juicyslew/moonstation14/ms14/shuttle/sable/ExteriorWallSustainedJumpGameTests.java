package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.BoundHumanFixture;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.network.LifecycleHandActorFixture;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecyclePairedWorldStepObserver;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

/** Paired, sustained-input jump comparison at a normal production cabin's exterior wall. */
@GameTestTags({"shuttle-owner-regression", "shuttle-exterior-jump-reproduction"})
@PrefixGameTestTemplate(false)
public final class ExteriorWallSustainedJumpGameTests {
    private static final int FIXTURE_TIMEOUT_TICKS = 850;
    private static final int MAX_ARC_TICKS = 60;
    private static final int POST_LANDING_TICKS = 10;
    private static final double MAX_VERTICAL_DIVERGENCE = .10;

    private ExteriorWallSustainedJumpGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty",
            batch = "shuttle_exterior_jump_reproduction", timeoutTicks = FIXTURE_TIMEOUT_TICKS)
    @GameTestTags({"shuttle-exterior-jump-straight", "shuttle-exterior-jump-diagonal"})
    public static void heldInwardJumpTracksFreeBaselineThroughStraightAndDiagonalArcs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sceneOrigin = new BlockPos(3200, 300, 3200);
        SableFixtureScene.Region scene = SableFixtureScene.acquire(helper, sceneOrigin, 64, 12, 64);
        SableFixtureScene.awaitEmpty(helper, scene, 100, () -> {
            SableFixtureScene.authorStoneSupportPad(level, scene);
            var authored = SableFixturePlot.allocateAndAuthor(level,
                    new Vector3d(sceneOrigin.getX() + 32.5, sceneOrigin.getY(), sceneOrigin.getZ() + 32.5), plot -> {
                        BlockPos storage = plot.storageOrigin();
                        plot.ensureStorageFootprint(storage, ShuttleCabinFixture.WIDTH,
                                ShuttleCabinFixture.HEIGHT, ShuttleCabinFixture.DEPTH);
                    return ShuttleCabinFixture.build(level, storage, plot.engineId());
                    });
            SableFixtureScene.trackPlot(helper, authored.plot());
            SableFixtureScene.awaitStationaryFrame(helper, authored.plot(), 80, () ->
                    startOwnerAndPairedBodies(helper, level, sceneOrigin, authored.plot(), authored.result().origin()));
        });
    }

    private static void startOwnerAndPairedBodies(GameTestHelper helper, ServerLevel level, BlockPos sceneOrigin,
            SableFixturePlot plot, BlockPos storage) {
        var framePose = plot.subLevel().logicalPose();
        var wallLocal = new Vector3d(storage.getX() - .4, storage.getY(), storage.getZ() + 6.5);
        var freeLocal = new Vector3d(storage.getX() - .4, storage.getY(), storage.getZ() + 24.5);
        var wallWorld = framePose.transformPosition(wallLocal);
        var freeWorld = framePose.transformPosition(freeLocal);
        BlockPos wallSupport = BlockPos.containing(wallWorld.x, sceneOrigin.getY() - 1, wallWorld.z);
        BlockPos freeSupport = BlockPos.containing(freeWorld.x, sceneOrigin.getY() - 1, freeWorld.z);
        double wallTop = supportTop(level, wallSupport);
        double freeTop = supportTop(level, freeSupport);
        Vec3 wallFeet = new Vec3(wallWorld.x, wallTop + .08, wallWorld.z);
        Vec3 freeFeet = new Vec3(freeWorld.x, freeTop + .08, freeWorld.z);
        if (!(level.getBlockState(storage.offset(0, 1, 6)).is(com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get())))
            throw new GameTestAssertException("production cabin WEST side wall cell is missing");

        LifecycleHandActorFixture.start(helper, "exterior-wall-jump-owner", owner -> {
            PlayerCharacterHarnessEntity wallBody = (PlayerCharacterHarnessEntity) owner.authority().body();
            // One fixture placement before the movement test; thereafter only registered input moves the owner.
            wallBody.moveTo(wallFeet.x, wallFeet.y, wallFeet.z, 0, 0);
            var freeBody = BoundHumanFixture.create(level, freeFeet, "exterior-wall-free-jump-baseline");
            var wallMove = new AtomicReference<com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.MoveResult>();
            var freeMove = new AtomicReference<com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.MoveResult>();
            AtomicLong lastMoveTick = new AtomicLong(Long.MIN_VALUE);
            var pairedSnapshot = new AtomicReference<LifecyclePairedWorldStepObserver.PairSnapshot>();
            // At most one paired world-step observation is recorded per fixture server tick.
            // The hard window therefore spans the full native GameTest deadline without eviction.
            var captureWindow = new LifecyclePairedWorldStepObserver.CaptureWindow(FIXTURE_TIMEOUT_TICKS);
            var scope = LifecyclePairedWorldStepObserver.install(owner.actor(), freeBody,
                    wallMove, freeMove, lastMoveTick, pairedSnapshot, captureWindow);
            helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
                private void close() {
                    try { scope.close(); }
                    catch (Exception exception) { MoonStation14.LOGGER.error("could not restore wall-jump observer", exception); }
                }
                @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) { }
                @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo test,
                        net.minecraft.gametest.framework.GameTestRunner runner) { close(); }
                @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo test,
                        net.minecraft.gametest.framework.GameTestRunner runner) { close(); }
                @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest,
                        net.minecraft.gametest.framework.GameTestInfo newTest,
                        net.minecraft.gametest.framework.GameTestRunner runner) { close(); }
            });
            if (((EntityMovementExtension) (Object) freeBody).sable$getTrackingSubLevel() == plot.subLevel())
                fail(helper, wallBody, freeBody, plot, storage, List.of(), "free baseline starts tracked by shuttle");
            long neutralSequence=dispatch(owner, 0, 0, 0, frameXAxisYaw(plot));
            awaitSupport(helper, owner, wallBody, freeBody, plot, storage, wallTop, wallMove, freeMove,
                    lastMoveTick, pairedSnapshot, 40, neutralSequence,
                    () -> new ArcProbe(helper, owner, wallBody, freeBody, plot, storage,
                            wallTop,captureWindow).begin(0));
        });
    }

    private static double supportTop(ServerLevel level, BlockPos support) {
        var state = level.getBlockState(support);
        var shape = state.getCollisionShape(level, support);
        if (shape.isEmpty()) throw new GameTestAssertException("natural exterior support shape missing at " + support);
        return support.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
    }

    private static void awaitSupport(GameTestHelper helper, LifecycleHandActorFixture.Ready owner,
            PlayerCharacterHarnessEntity wall, PlayerCharacterHarnessEntity free, SableFixturePlot plot,
            BlockPos storage, double worldTerrainTop,
            AtomicReference<com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.MoveResult> wallMove,
            AtomicReference<com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.MoveResult> freeMove,
            AtomicLong lastMoveTick, AtomicReference<LifecyclePairedWorldStepObserver.PairSnapshot> pairedSnapshot,
            int remaining, long pendingSequence, Runnable ready) {
        helper.runAfterDelay(1, () -> {
            long tick = helper.getLevel().getServer().getTickCount();
            long acknowledged=owner.drainAppliedSequence();
            if (acknowledged < pendingSequence) {
                List<String> pendingSample=List.of("supportWait tick="+tick+" pendingSequence="+pendingSequence
                        +" acknowledgedSequence="+acknowledged+" lastMoveTick="+lastMoveTick.get()
                        +" wallGrounded="+wall.onGround()+" freeGrounded="+free.onGround()
                        +" pairedSnapshot="+pairedSnapshot.get()+" wallMove="+wallMove.get()
                        +" freeMove="+freeMove.get()+" native="+OwnedNativeMoveDiagnostic.describe(wall,wallMove.get()));
                if (remaining <= 0) fail(helper, wall, free, plot, storage, pendingSample,
                        "neutral support intent acknowledgement timeout pending="+pendingSequence+" ack="+acknowledged);
                else awaitSupport(helper, owner, wall, free, plot, storage, worldTerrainTop, wallMove, freeMove,
                        lastMoveTick, pairedSnapshot, remaining - 1, pendingSequence, ready);
                return;
            }
            if (wall.onGround() && free.onGround() && wallMove.get() != null && freeMove.get() != null) {
                if (((EntityMovementExtension) (Object) free).sable$getTrackingSubLevel() == plot.subLevel())
                    fail(helper, wall, free, plot, storage, List.of(), "free baseline entered shuttle tracking region");
                ready.run();
                return;
            }
            if (remaining <= 0) fail(helper, wall, free, plot, storage, List.of(
                    "paired actors did not naturally acquire exterior support at tick=" + tick
                            + " lastMoveTick=" + lastMoveTick.get()+" ack="+acknowledged
                            +" paired="+pairedSnapshot.get()+" native="+OwnedNativeMoveDiagnostic.describe(wall,wallMove.get())),
                    "paired actors did not naturally acquire exterior support");
            else {
                long nextSequence=dispatch(owner,0,0,0,frameXAxisYaw(plot));
                awaitSupport(helper, owner, wall, free, plot, storage, worldTerrainTop, wallMove, freeMove,
                        lastMoveTick, pairedSnapshot, remaining - 1, nextSequence, ready);
            }
        });
    }

    private static long dispatch(LifecycleHandActorFixture.Ready owner, int wishX, int wishZ,
            int buttons, float yaw) {
        long sequence = owner.intentSequence().incrementAndGet();
        owner.dispatch(intentFor(owner.epoch(),sequence,wishX,wishZ,buttons,yaw));
        return sequence;
    }

    static GhostControlPayloads.Intent intentFor(long epoch,long sequence,int wishX,int wishZ,int buttons,float yaw) {
        return new GhostControlPayloads.Intent(epoch,sequence,(short)wishX,(short)wishZ,(byte)0,buttons,yaw,0f);
    }

    static final double MAX_TANGENT_STEP_ERROR = .02;
    static final double MAX_TANGENT_ARC_ERROR = .10;
    static record TangentEvidence(double stepError, double arcError) {
        boolean withinBounds() {
            return Math.abs(stepError) <= MAX_TANGENT_STEP_ERROR
                    && Math.abs(arcError) <= MAX_TANGENT_ARC_ERROR;
        }
    }

    static TangentEvidence tangentEvidence(double actualStep, double requestedStep,
                                           double actualArc, double requestedArc) {
        return new TangentEvidence(actualStep-requestedStep,actualArc-requestedArc);
    }

    private static float frameXAxisYaw(SableFixturePlot plot) {
        var axis = plot.subLevel().logicalPose().orientation().transform(new Vector3d(1, 0, 0));
        return (float) Math.toDegrees(Math.atan2(axis.z, axis.x));
    }

    private static final class ArcProbe {
        private final GameTestHelper helper;
        private final LifecycleHandActorFixture.Ready owner;
        private final PlayerCharacterHarnessEntity wall;
        private final PlayerCharacterHarnessEntity free;
        private final SableFixturePlot plot;
        private final BlockPos storage;
        private final double worldTerrainTop;
        private final LifecyclePairedWorldStepObserver.CaptureWindow captureWindow;
        private final AtomicReference<DesiredInput> desiredInput = new AtomicReference<>();
        private final AtomicBoolean inputPump = new AtomicBoolean();
        private final AtomicLong lastSentSequence = new AtomicLong();
        private final long[] jumpSequences = new long[2];
        private final List<String> samples = new ArrayList<>();
        private int captureCursor;
        private int arc;
        private int ticks;
        private int landingTick = -1;
        private boolean airborne;
        private boolean sideContact;
        private double wallStartY;
        private double freeStartY;
        private Vec3 wallArcStart;
        private Vec3 freeArcStart;
        private Vector3d arcTangent;
        private double maxWallRise;
        private double maxFreeRise;
        private double requestedArcTangent;
        private long previousStepTick = Long.MIN_VALUE;
        private int ackWaitTicks;
        private int jumpObservationWaitTicks;
        private int noCaptureWaitTicks;

        private ArcProbe(GameTestHelper helper, LifecycleHandActorFixture.Ready owner,
                PlayerCharacterHarnessEntity wall, PlayerCharacterHarnessEntity free, SableFixturePlot plot,
                BlockPos storage, double worldTerrainTop,
                LifecyclePairedWorldStepObserver.CaptureWindow captureWindow) {
            this.helper=helper; this.owner=owner; this.wall=wall; this.free=free; this.plot=plot;
            this.storage=storage; this.worldTerrainTop=worldTerrainTop;
            this.captureWindow=captureWindow;
        }

        private void begin(int arcIndex) {
            arc=arcIndex; ticks=0; landingTick=-1; airborne=false; sideContact=false;
            desiredInput.set(new DesiredInput(1000, arc==0?0:250, true));
            inputPump.set(true);
            captureCursor=captureWindow.size();
            BoundedGameTestWait.safeOnEachTick(helper, this::sendHeldInput);
            pollCapturedSteps();
        }

        private void sendHeldInput() {
            if(!inputPump.get())return;
            DesiredInput desired=desiredInput.get();
            if(desired==null)return;
            int buttons=desired.jumpPending()?GhostControlPayloads.BUTTON_JUMP:0;
            long sequence=dispatch(owner,desired.wishX(),desired.wishZ(),buttons,frameXAxisYaw(plot));
            lastSentSequence.set(sequence);
            if(desired.jumpPending()){
                if(jumpSequences[arc]==0)jumpSequences[arc]=sequence;
            }
        }

        private void pollCapturedSteps() {
            helper.runAfterDelay(1, () -> {
                var captured=captureWindow.snapshot();
                int beforeCount=captureCursor;
                for(var sample:captured.subList(captureCursor,captured.size())) {
                    captureCursor++;
                    process(sample);
                    if(!inputPump.get())break;
                }
                if(!inputPump.get()) {
                    checkFinalAcknowledgements();
                    return;
                }
                if(captureCursor==beforeCount&&++noCaptureWaitTicks>=40)
                    fail(helper,wall,free,plot,storage,samples,"no actual world-step samples within bounded wait");
                if(captureCursor>beforeCount)noCaptureWaitTicks=0;
                if(!arcActive&&jumpSequences[arc]>0&&++jumpObservationWaitTicks>=40)
                    fail(helper,wall,free,plot,storage,samples,"submitted jump edge was not observed in actual world-step input");
                if(ticks>MAX_ARC_TICKS) fail(helper,wall,free,plot,storage,samples,
                        "jump arc did not complete within bounded observed steps");
                pollCapturedSteps();
            });
        }

        private void process(LifecyclePairedWorldStepObserver.PairSnapshot paired) {
            var move=paired.ownerMove(); var freeResult=paired.pairedMove();
            long acknowledged=owner.drainAppliedSequence();
            if(move==null||freeResult==null||!move.collisionInfoFresh()||move.collisionScope()==null
                    ||!move.collisionScope().nativeCollisionTraceComplete()) {
                samples.add("tick="+paired.tick()+" input="+paired.wishX()+","+paired.wishZ()+" jump="
                        +paired.jump()+" seqAck="+acknowledged+" paired="+paired+" native="
                        +OwnedNativeMoveDiagnostic.describe(wall,move));
                fail(helper,wall,free,plot,storage,samples,"missing fresh actual native world-step evidence");
            }
            if(!arcActive) {
                if(!paired.jump()) {
                    samples.add("preArc tick="+paired.tick()+" ack="+acknowledged+" input="
                            +paired.wishX()+","+paired.wishZ()+" jump=false ownerBefore="+paired.ownerBefore()
                            +" ownerAfter="+paired.ownerAfter()+" ownerVel="+paired.ownerVelocityBefore()+"->"
                            +paired.ownerVelocityAfter()+" freeBefore="+paired.pairedBefore()+" freeAfter="
                            +paired.pairedAfter()+" freeVel="+paired.pairedVelocityBefore()+"->"
                            +paired.pairedVelocityAfter()+" grounded="+paired.ownerGrounded()+"/"+paired.pairedGrounded()
                            +" ownerMove="+move+" freeMove="+freeResult+" native="
                            +OwnedNativeMoveDiagnostic.describe(wall,move));
                    return;
                }
                int expectedZ=arc==0?0:250;
                if(paired.wishX()!=1000||paired.wishZ()!=expectedZ)
                    fail(helper,wall,free,plot,storage,samples,"observed jump input differs from held arc command");
                if(jumpSequences[arc]<=0)
                    fail(helper,wall,free,plot,storage,samples,"jump edge has no associated submitted intent sequence");
                wallStartY=paired.ownerBefore().y; freeStartY=paired.pairedBefore().y;
                wallArcStart=paired.ownerBefore(); freeArcStart=paired.pairedBefore();
                arcTangent=plot.subLevel().logicalPose().orientation().transform(new Vector3d(0,0,1)).normalize();
                requestedArcTangent=0d;
                desiredInput.set(new DesiredInput(paired.wishX(),paired.wishZ(),false));
                maxWallRise=0;maxFreeRise=0;airborne=false;sideContact=false;landingTick=-1;ticks=0;
                arcActive=true;jumpObservationWaitTicks=0;
            }
            int expectedZ=arc==0?0:250;
            if(paired.wishX()!=1000||paired.wishZ()!=expectedZ||paired.sprint())
                fail(helper,wall,free,plot,storage,samples,"actual held input changed during jump arc");
            if(paired.tick()==previousStepTick)
                fail(helper,wall,free,plot,storage,samples,"repeated physical-step tick in captured arc");
            previousStepTick=paired.tick();
            boolean ownerTakeoff=paired.ownerGroundedBefore()
                    &&move.requestedOwnDisplacement().y()>0&&paired.ownerVelocityAfter().y>0;
            boolean freeTakeoff=paired.pairedGroundedBefore()
                    &&freeResult.requestedOwnDisplacement().y()>0&&paired.pairedVelocityAfter().y>0;
            if(ticks>0&&paired.jump()&&(ownerTakeoff||freeTakeoff))
                fail(helper,wall,free,plot,storage,samples,"additional physical jump takeoff during one held-input arc"
                        +" owner="+ownerTakeoff+" free="+freeTakeoff);
            var info=move.collisionInfo();
            var tracking=info==null?null:info.trackingSubLevel;
            var point=move.afterPoint();
            var rows=move.collisionScope().nativeCollisionTrace();
            boolean selectedSide=rows.stream().anyMatch(row -> plot.engineId().equals(row.frameId())
                    && row.selectedPos()!=null && row.selectedState()!=null
                    && row.selectedState().is(com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get())
                    && row.selectedPos().getY()>=storage.getY()
                    && row.selectedPos().getY()<storage.getY()+ShuttleCabinFixture.HEIGHT
                    && !row.verticalCollision());
            boolean overheadSelection=rows.stream().anyMatch(row -> plot.engineId().equals(row.frameId())
                    && row.verticalCollision() && row.selectedPos()!=null
                    && row.selectedPos().getY()>=storage.getY()+ShuttleCabinFixture.HEIGHT);
            boolean airborneNow=!paired.ownerGrounded()&&!paired.pairedGrounded();
            if(airborneNow)airborne=true;
            if(airborneNow&&selectedSide)sideContact=true;
            maxWallRise=Math.max(maxWallRise,paired.ownerAfter().y-wallStartY);
            maxFreeRise=Math.max(maxFreeRise,paired.pairedAfter().y-freeStartY);
            double divergence=(paired.ownerAfter().y-wallStartY)-(paired.pairedAfter().y-freeStartY);
            Vector3d localFeet=plot.subLevel().logicalPose().transformPositionInverse(new Vector3d(
                    paired.ownerAfter().x,paired.ownerBoundsAfter().minY,paired.ownerAfter().z));
            Vec3 wallDelta=paired.ownerAfter().subtract(paired.ownerBefore());
            Vec3 freeDelta=paired.pairedAfter().subtract(paired.pairedBefore());
            var tangent=arcTangent;
            double wallTangent=wallDelta.x*tangent.x+wallDelta.y*tangent.y+wallDelta.z*tangent.z;
            double freeTangent=freeDelta.x*tangent.x+freeDelta.y*tangent.y+freeDelta.z*tangent.z;
            Vec3 wallArcDelta=paired.ownerAfter().subtract(wallArcStart);
            Vec3 freeArcDelta=paired.pairedAfter().subtract(freeArcStart);
            double wallArcTangent=wallArcDelta.x*tangent.x+wallArcDelta.y*tangent.y+wallArcDelta.z*tangent.z;
            double freeArcTangent=freeArcDelta.x*tangent.x+freeArcDelta.y*tangent.y+freeArcDelta.z*tangent.z;
            var ownRequest=move.requestedOwnDisplacement();
            double requestedStepTangent=ownRequest.x()*tangent.x+ownRequest.y()*tangent.y+ownRequest.z()*tangent.z;
            requestedArcTangent+=requestedStepTangent;
            var tangentEvidence=tangentEvidence(wallTangent,requestedStepTangent,
                    wallArcTangent,requestedArcTangent);
            boolean trackedFrame=tracking==plot.subLevel()&&point!=null&&plot.engineId().equals(point.frameId());
            boolean normalFloorRow=rows.stream().anyMatch(row -> plot.engineId().equals(row.frameId())
                    && row.verticalCollision() && row.selectedPos()!=null && row.selectedState()!=null
                    && row.selectedState().is(com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get())
                    && row.selectedPos().getY()<localFeet.y&&localFeet.y-row.selectedPos().getY()<=1.25);
            boolean worldTerrainStillBelow=paired.ownerBoundsAfter().minY>worldTerrainTop+.05;
            samples.add("arc="+arc+" t="+paired.tick()+" step="+ticks+" seqAck="+acknowledged
                    +" submittedJumpSeq="+jumpSequences[arc]+" actualInput="+paired.wishX()+","+paired.wishZ()
                    +" jump="+paired.jump()+" wallBefore="+paired.ownerBefore()+" wallBeforeVel="+paired.ownerVelocityBefore()
                    +" wallAfter="+paired.ownerAfter()+" wallAfterVel="+paired.ownerVelocityAfter()
                    +" freeBefore="+paired.pairedBefore()+" freeBeforeVel="+paired.pairedVelocityBefore()
                    +" freeAfter="+paired.pairedAfter()+" freeAfterVel="+paired.pairedVelocityAfter()
                    +" wallOwnReq="+move.requestedOwnDisplacement()+" wallOwnAccepted="+move.acceptedOwnDisplacement()
                    +" wallOwnBounded="+move.boundedOwnDisplacement()+" wallCarry="+move.frameVelocityDeltaPerSecond()
                    +" wallOwnMeasured="+move.measuredOwnDisplacement()+" freeOwnReq="+freeResult.requestedOwnDisplacement()
                    +" freeOwnAccepted="+freeResult.acceptedOwnDisplacement()+" freeOwnMeasured="+freeResult.measuredOwnDisplacement()
                    +" freeOwnBounded="+freeResult.boundedOwnDisplacement()+" freeCarry="+freeResult.frameVelocityDeltaPerSecond()
                    +" wallOwnDelta="+wallDelta+" freeOwnDelta="+freeDelta+" tangentDelta="+wallTangent+"/"+freeTangent
                    +" requestedTangent="+requestedStepTangent+" requestedArcTangent="+requestedArcTangent
                    +" cumulativeTangent="+wallArcTangent+" freeDiagnosticTangent="+freeArcTangent
                    +" tangentError="+tangentEvidence.stepError()+" cumulativeTangentError="+tangentEvidence.arcError()
                    +" feetWorld="+paired.ownerBoundsAfter().minY+" feetLocal="+localFeet.y+" deltaY="+divergence
                    +" landingPlaneAbove="+worldTerrainStillBelow+" ground="+paired.ownerGrounded()+"/"+paired.pairedGrounded()
                    +" groundBefore="+paired.ownerGroundedBefore()+"/"+paired.pairedGroundedBefore()
                    +" below="+(info!=null&&info.verticalCollisionBelow)+" vertical="+(info!=null&&info.verticalCollision)
                    +" normalFloorRow="+normalFloorRow+" sideRow="+selectedSide+" overheadRow="+overheadSelection
                    +" track="+(tracking==null?null:tracking.getUniqueId())+" trackedFrame="+trackedFrame
                    +" actorFrame="+(point==null?null:point.frameId())+" pose="+plot.subLevel().logicalPose()
                    +" selected="+rows+" move="+OwnedNativeMoveDiagnostic.describe(wall,move));
            if(paired.ownerGrounded()&&!paired.pairedGrounded()&&airborne&&worldTerrainStillBelow)
                fail(helper,wall,free,plot,storage,samples,"wall actor grounded above terrain while free baseline remained airborne");
            if(airborneNow&&!paired.pairedGrounded()&&worldTerrainStillBelow&&trackedFrame
                    &&normalFloorRow&&info!=null&&info.verticalCollisionBelow)
                fail(helper,wall,free,plot,storage,samples,"airborne wall actor reported native below support");
            if(airborne&&Math.abs(divergence)>MAX_VERTICAL_DIVERGENCE)
                fail(helper,wall,free,plot,storage,samples,"wall/free vertical trajectory diverged="+divergence);
            if(!tangentEvidence.withinBounds())
                fail(helper,wall,free,plot,storage,samples,"wall tangent displacement differed from its actual own request"
                        +" stepError="+tangentEvidence.stepError()+" arcError="+tangentEvidence.arcError()
                        +" freeDiagnostic="+freeTangent);
            if(paired.ownerGrounded()&&paired.pairedGrounded()&&airborne){if(landingTick<0)landingTick=ticks;}
            ticks++;
            if(ticks>=MAX_ARC_TICKS&&landingTick<0)
                fail(helper,wall,free,plot,storage,samples,"jump arc did not return to natural support");
            if(landingTick>=0&&ticks-landingTick>=POST_LANDING_TICKS){
                if(!sideContact)fail(helper,wall,free,plot,storage,samples,"no airborne steel side row during held inward contact");
                if(!(maxWallRise>.2&&maxFreeRise>.2))fail(helper,wall,free,plot,storage,samples,"paired actors did not jump");
                arcActive=false;
                if(arc==0){arc=1;desiredInput.set(new DesiredInput(1000,250,true));return;}
                inputPump.set(false);
            }
        }

        private boolean arcActive;

        private void checkFinalAcknowledgements(){
            long ack=owner.drainAppliedSequence();
            long target=lastSentSequence.get();
            if(ack>=jumpSequences[0]&&ack>=jumpSequences[1]&&ack>=target){helper.succeed();return;}
            if(++ackWaitTicks>=40)fail(helper,wall,free,plot,storage,samples,
                    "bounded registered-input acknowledgement timeout ack="+ack+" straight="+jumpSequences[0]
                            +" diagonal="+jumpSequences[1]+" finalHold="+target);
            helper.runAfterDelay(1,this::pollCapturedSteps);
        }

        private record DesiredInput(int wishX,int wishZ,boolean jumpPending) { }
    }

    private static void fail(GameTestHelper helper, PlayerCharacterHarnessEntity wall,
            PlayerCharacterHarnessEntity free, SableFixturePlot plot, BlockPos storage,
            List<String> samples, String reason) {
        String tag="sustained-exterior-jump:"+wall.getUUID();
        MoonStation14.LOGGER.error("[{}] reason={} wall={} free={} frame={} storage={} mass={} pose={} pivot={} samples={}",
                tag,reason,wall.position()+" feet="+wall.getBoundingBox()+" velocity="+wall.getDeltaMovement()
                        +" ground="+wall.onGround(),free.position()+" feet="+free.getBoundingBox()
                        +" velocity="+free.getDeltaMovement()+" ground="+free.onGround(),plot.engineId(),storage,
                plot.subLevel().getMassTracker().getMass(),plot.subLevel().logicalPose(),
                plot.subLevel().getMassTracker().getCenterOfMass(),samples);
        String compact="sustained wall-jump proof failed: "+reason+" tag="+tag;
        throw new GameTestAssertException(compact.length()<=580?compact:compact.substring(0,577)+"...");
    }
}
