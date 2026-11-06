package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.hands.network.LifecycleHandActorFixture;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleSingleWorldStepObserver;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleMotionRequest;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Natural supported jump, observed only at the lifecycle-owned physical world-step boundary. */
@PrefixGameTestTemplate(false)
public final class NaturalMovingJumpLifecycleGameTests {
    private NaturalMovingJumpLifecycleGameTests() { }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=700)
    @GameTestTags("shuttle-natural-moving-jump-regression")
    public static void lifecycleOwnerCompletesNaturalJumpOnStraightMovingCabin(GameTestHelper helper) {
        start(helper, false);
    }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=700)
    @GameTestTags("shuttle-natural-moving-boarding-regression")
    public static void lifecycleOwnerBoardsMovingManualCabinFromWorldPad(GameTestHelper helper) {
        start(helper, true);
    }

    private static void start(GameTestHelper helper, boolean boarding) {
        MovingCabinAuthorityFixture.startManual(helper, new BlockPos(3400, 300, 2800),
                boarding?"natural-moving-boarding":"natural-moving-jump",
                13.5, 1.05, 6.5, (fixture, body) -> {
            var frame = fixture.plot().subLevel();
            var handle = RigidBodyHandle.of(frame);
            fixture.setIntentPumpEnabled(false);
            var request = new ShuttleMotionRequest(0, 0, 2, 0, 240);
            ShuttleFixtureMotionService.registerFixture(frame);
            // Match the known-good diagnostic's ordinary post-readiness native initialization window.
            helper.runAfterDelay(30, () -> {
                boolean admitted = ShuttleFixtureMotionService.start(fixture.level(), frame, request);
                var status = ShuttleFixtureMotionService.status(fixture.level(), frame);
                if (handle == null || !handle.isValid() || !admitted) {
                    helper.fail("natural jump cabin motion admission failed: admitted=" + admitted
                            + " request=" + request + " handle=" + (handle != null && handle.isValid())
                            + " readiness=" + status); return;
                }
                awaitMotion(helper, fixture, body, 100, 0, boarding);
            });
        });
    }

    private static void awaitMotion(GameTestHelper h, MovingCabinAuthorityFixture.Ready fixture,
                                    PlayerCharacterHarnessEntity body, int left, int stable, boolean boarding) {
        var f=fixture.plot().subLevel(); var s=ShuttleFixtureMotionService.snapshot(fixture.level(),f);
        var interval=NativeFrameInterval.PosePair.capture(f.lastPose(),f.logicalPose());
        var anchor=new org.joml.Vector3d(fixture.plot().storageOrigin().getX()+13.5,
                fixture.plot().storageOrigin().getY()+1,fixture.plot().storageOrigin().getZ()+6.5);
        var a=new org.joml.Vector3d(anchor); new org.joml.Matrix4d().set(interval.last().matrix()).transformPosition(a);
        var b=new org.joml.Vector3d(anchor); new org.joml.Matrix4d().set(interval.logical().matrix()).transformPosition(b);
        var delta=b.sub(a);
        double rate=delta.z()/com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics.TICK_SECONDS;
        boolean valid=s!=null&&s.active()&&s.remainingTicks()>0&&s.sdkSubsteps()==6
                &&s.request().equals(new ShuttleMotionRequest(0,0,2,0,240))&&rate>=1.8&&rate<=2.2
                &&Math.abs(delta.x())<.25&&Math.abs(delta.y())<.25;
        var point=LoadedFrameRayQuery.currentActorPoint(fixture.level(),body);
        boolean supported=valid&&body.onGround()&&point!=null&&fixture.plot().engineId().equals(point.frameId())
                &&Math.abs(body.getDeltaMovement().horizontalDistance())<.05;
        if(supported&&stable>=3){
            Flight flight=new Flight(h,fixture,body);
            if(boarding) flight.beginBoarding(); else flight.begin();
            return;
        }
        if(left<=0){h.fail("moving cabin/HUMAN did not reach stable matching-frame support; rate20Hz="+rate
                +" snapshot="+ShuttleFixtureMotionService.status(fixture.level(),f)+" point="+point);return;}
        h.runAfterDelay(1,()->awaitMotion(h,fixture,body,left-1,supported?stable+1:0,boarding));
    }

    private static final class Flight {
        final GameTestHelper h; final MovingCabinAuthorityFixture.Ready fixture;
        final PlayerCharacterHarnessEntity body;
        final AtomicReference<List<LifecycleSingleWorldStepObserver.Attempt>> attempts=new AtomicReference<>(List.of());
        final HashSet<Long> physicalTicks=new HashSet<>();
        final ArrayList<String> evidence=new ArrayList<>();
        AutoCloseable scope; int attemptCursor, polls, noStepPolls, supportedAfter, boardTicks, worldRestSteps, ascendingSteps, releasedAscendingSteps, postContactSteps, verifiedAirReleaseSteps;
        boolean boarding, airborne, descending, landed, exactAckObserved, releaseAckObserved, neutralAckObserved;
        boolean releaseSent, neutralStarted, worldStepEvidence, boardBranchSeen, jumpStarted;
        long revision, jumpSequence, releaseSequence, neutralSequence, lastSubmittedSequence, stageSequence, latestPayloadAck;
        long jumpServerSubmitTick, jumpWorldSubmitTick, ackServerTick=Long.MIN_VALUE, releaseAckServerTick=Long.MIN_VALUE;
        long takeoffServerTick=Long.MIN_VALUE, takeoffWorldTick=Long.MIN_VALUE, firstBoardingContactTick=Long.MIN_VALUE;
        double lastGap=Double.NaN; String lastStage="setup";

        Flight(GameTestHelper h,MovingCabinAuthorityFixture.Ready f,PlayerCharacterHarnessEntity b){this.h=h;fixture=f;body=b;}
        void install(){
            var owner=fixture.owner(); revision=owner.latestSnapshot().get()==null?0:owner.latestSnapshot().get().movementRebaseRevision();
            scope=LifecycleSingleWorldStepObserver.install(owner.actor(),attempts); cleanup();
        }
        void begin(){boarding=false;install();dispatchJump(0,false);}

        void beginBoarding(){
            boarding=true;install();var owner=fixture.owner(); owner.setWish(0,0);owner.buttons().set(0);owner.setSprint(false);
            var frame=fixture.plot().subLevel();var storage=fixture.plot().storageOrigin();
            var localDoor=new org.joml.Vector3d(storage.getX()+13.5,storage.getY(),storage.getZ()-.4);
            var doorWorld=frame.logicalPose().transformPosition(localDoor);
            double padTop=fixture.scene().supportSurfaceY();
            var target=new net.minecraft.world.phys.Vec3(doorWorld.x,padTop+.05,doorWorld.z);
            var support=net.minecraft.core.BlockPos.containing(target.x,padTop-.01,target.z);
            if(!fixture.scene().tickets.entityTicking(support)
                    ||!fixture.level().getBlockState(support).is(net.minecraft.world.level.block.Blocks.STONE)){
                fail("one-time world-pad candidate lacks loaded authored stone support: point="+target+" support="+support);return;
            }
            body.moveTo(target.x,target.y,target.z,body.getYRot(),body.getXRot());
            lastStage="single pre-jump world placement="+target+" support="+support;
            stageSequence=owner.neutralIntent(body);lastSubmittedSequence=stageSequence;stageBoarding();
        }

        long dispatchIntent(int wishZ,int buttons){
            var owner=fixture.owner();long sequence=owner.intentSequence().incrementAndGet();
            owner.dispatch(new GhostControlPayloads.Intent(owner.epoch(),sequence,(short)0,(short)wishZ,(byte)0,
                    buttons,body.getYRot(),body.getXRot(),owner.supportedMovementReference().get()));return sequence;
        }
        void dispatchJump(int wishZ,boolean sprint){
            var owner=fixture.owner();jumpServerSubmitTick=owner.actor().level().getServer().getTickCount();
            jumpWorldSubmitTick=owner.actor().level().getGameTime();jumpStarted=true;
            jumpSequence=dispatchIntent(wishZ,GhostControlPayloads.BUTTON_JUMP|(sprint?GhostControlPayloads.BUTTON_SPRINT:0));
            lastSubmittedSequence=jumpSequence;poll();
        }

        void stageBoarding(){
            h.runAfterDelay(1,()->{
                if(!refreshSnapshots()||!processAttempts())return;
                var owner=fixture.owner();boardTicks++;
                var point=LoadedFrameRayQuery.currentActorPoint(fixture.level(),body);
                var nativeInfo=((EntityMovementExtension)(Object)body).sable$getCollisionInfo();
                double gap=frameRelativeGap();
                var all=attempts.get();var latest=all.isEmpty()?null:all.get(all.size()-1);
                boolean freshWorld=latest!=null&&latest.move()!=null&&isWorldClipEvidence(latest.move())
                        &&latest.move().afterPoint()!=null&&latest.move().afterPoint().frameId()==null
                        &&latest.groundedAfter();
                worldStepEvidence|=freshWorld;
                boolean worldGround=body.onGround()&&point!=null&&point.frameId()==null&&worldStepEvidence
                        &&(nativeInfo==null||nativeInfo.trackingSubLevel==null);
                boolean rest=body.getDeltaMovement().horizontalDistance()<.05;
                worldRestSteps=worldGround&&rest&&freshWorld?worldRestSteps+1:0;
                boolean inWindow=gap>=-.72&&gap<=-.60;
                boolean ackReady=latestPayloadAck>=stageSequence;
                String reason=worldRestSteps<1?"await-grounded-world-step":!inWindow?"await-gap-window":!ackReady?"await-original-neutral-ACK":"ready";
                lastGap=gap;lastStage="world-gate elapsed="+boardTicks+" gap="+gap+" point="+point+" worldGround="+worldGround
                        +" rest="+rest+" groundedRestWorldSteps="+worldRestSteps+" worldEvidence="+worldStepEvidence
                        +" inWindow="+inWindow+" ackReady="+ackReady+" reason="+reason
                        +" submitted="+stageSequence+" actualSnapshotAck="+latestPayloadAck;
                if(owner.intentSequence().get()!=lastSubmittedSequence){fail("staging input sequence changed: "+lastStage);return;}
                if(worldRestSteps>=1&&inWindow&&ackReady){
                    dispatchJump(1000,true);return;
                }
                if(gap<-.72||boardTicks>=14){fail("boarding approach window missed without retry; actualGap="+gap
                        +" waitTicks="+boardTicks+" actorPoint="+point+" pos="+body.position()+" gate="+lastStage);return;}
                // The submitted neutral remains the session's held input. Re-sending each poll
                // would continually move the actual-ACK threshold and can miss a transient gap.
                stageBoarding();
            });
        }

        double frameRelativeGap(){
            var frame=fixture.plot().subLevel();var storage=fixture.plot().storageOrigin();
            var local=frame.logicalPose().transformPositionInverse(new org.joml.Vector3d(body.getX(),body.getY(),body.getZ()));
            return local.z-storage.getZ();
        }
        boolean refreshSnapshots(){
            var owner=fixture.owner();
            for(var snapshot:owner.drainSnapshots()){
                if(snapshot.movementRebaseRevision()!=revision){fail("actual snapshot rebase changed: "+snapshot);return false;}
                latestPayloadAck=snapshot.acknowledgedAppliedSequence();
                if(jumpSequence>0&&latestPayloadAck==jumpSequence){exactAckObserved=true;ackServerTick=snapshot.serverGameTick();}
                if(releaseSequence>0&&latestPayloadAck==releaseSequence){releaseAckObserved=true;releaseAckServerTick=snapshot.serverGameTick();}
                if(neutralSequence>0&&latestPayloadAck>=neutralSequence)neutralAckObserved=true;
                if(jumpSequence>0&&!releaseSent&&latestPayloadAck>jumpSequence){fail("ACK advanced beyond sole jump before controlled release: "+snapshot);return false;}
            }
            if(owner.latestSnapshot().get()!=null&&owner.latestSnapshot().get().movementRebaseRevision()!=revision){fail("latest snapshot rebase changed");return false;}
            return true;
        }

        boolean processAttempts(){
            var all=attempts.get();
            if(attemptCursor==all.size()){
                if(++noStepPolls>3){fail("bounded wait without lifecycle wrapper invocation; stage="+lastStage);return false;}
            }else noStepPolls=0;
            while(attemptCursor<all.size()){
                var a=all.get(attemptCursor++);
                for(var actualMove:a.moves()){
                    var proof=actualMove.nativeContactProofDiagnostic();
                    if(proof!=null&&proof.branch()==NativeContactProofDiagnostic.Branch.ASCENDING_FLOOR_AIRBORNE){
                        boardBranchSeen=true;
                        if(firstBoardingContactTick==Long.MIN_VALUE)firstBoardingContactTick=a.serverPhysicalTick();
                        String witness="ASCENDING_FLOOR_AIRBORNE serverTick="+a.serverPhysicalTick()+" worldTick="+a.worldGameTick()
                                +" requestY="+actualMove.requestedOwnDisplacement().y()+" pos="+a.after()
                                +" stage="+proof.positionStage()+" correction="+proof.proofGeometryCorrection()
                                +" boundedOwn="+proof.proofBoundedOwn()+" clearance="+proof.clearanceObservation().token()
                                +" grounding="+proof.groundingDecision()+" reason="+proof.reason()
                                +" cert="+proof.fresh()+"/"+proof.traceComplete()+"/"+proof.geometryComplete();
                        remember(witness);System.out.println("NATURAL_MOVING_BOARDING_BRANCH "+witness);
                        if(boarding){
                            String releaseWitness="boarding-release tick="+a.serverPhysicalTick()+" beforePoint="+actualMove.beforePoint()
                                    +" afterPoint="+actualMove.afterPoint()+" frameVelocityDeltaPerSecond="+actualMove.frameVelocityDeltaPerSecond()
                                    +" measuredOwn="+actualMove.measuredOwnDisplacement()+" request="+actualMove.requestedOwnDisplacement()
                                    +" proof="+proof.clearanceObservation().token()+" geometryCorrection="+proof.proofGeometryCorrection();
                            remember(releaseWitness);System.out.println("NATURAL_MOVING_BOARDING_RELEASE "+releaseWitness);
                            if(actualMove.afterPoint()==null||actualMove.afterPoint().frameId()!=null
                                    ||!nearZero(actualMove.frameVelocityDeltaPerSecond())){
                                fail("authorized boarding contact did not release ownership into world without carrier impulse: "
                                        +releaseWitness);return false;
                            }
                        }
                        if(!actualMove.collisionInfoFresh()||!proof.fresh()||!proof.traceComplete()||!proof.geometryComplete()
                                ||actualMove.collisionScope()==null||!actualMove.collisionScope().nativeCollisionTraceComplete()
                                ||!actualMove.collisionScope().geometryTraceComplete()){
                            fail("ascending-floor branch lacked fresh complete native evidence: "+witness);return false;
                        }
                        var clearance=proof.clearanceObservation();
                        double requestY=actualMove.requestedOwnDisplacement().y();
                        if(clearance.mode()!=NativeContactProofDiagnostic.QueryMode.BODY_VOLUME
                                ||clearance.captured()!=NativeFinalFootClearance.Result.CLEAR
                                ||clearance.finalResult()!=NativeFinalFootClearance.Result.CLEAR
                                ||clearance.authorization()!=NativeContactProofDiagnostic.Authorization.AUTHORIZED
                                ||proof.positionStage()!=NativeContactProofDiagnostic.PositionStage.ACCEPTED
                                ||proof.groundingDecision()!=SableOwnedBodyMotion.GroundingDecision.ASCENDING_FLOOR_AIRBORNE
                                ||proof.proofGeometryCorrection()==null
                                ||proof.proofGeometryCorrection().y()<=com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.MAX_RESIDUAL
                                ||proof.proofBoundedOwn()==null
                                ||Math.abs(proof.proofBoundedOwn().y()-requestY)>com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.MAX_RESIDUAL
                                ||Math.abs(requestY-.248136)>1.0e-6){
                            fail("ascending-floor live body-volume clearance/position contract rejected: "+witness);return false;
                        }
                    }
                }
                if(!physicalTicks.add(a.serverPhysicalTick())){fail("duplicate wrapper invocation physicalTick="+a.serverPhysicalTick());return false;}
                if(a.callbackCount()!=1){fail("native callback count != 1: "+compact(a));return false;}
                var m=a.move();
                if(a.failure()!=null||a.result()==null||a.result().isEmpty()||m==null){
                    fail("lifecycle attempt failed: "+compact(a)+" boardBranch="+boardBranchSeen);return false;
                }
                if(boarding&&firstBoardingContactTick!=Long.MIN_VALUE
                        &&a.serverPhysicalTick()>firstBoardingContactTick)postContactSteps++;
                if(boarding&&firstBoardingContactTick!=Long.MIN_VALUE
                        &&a.serverPhysicalTick()>firstBoardingContactTick
                        &&!a.groundedBefore()&&!a.groundedAfter()&&isContactFreeWorldAirMove(a,m)){
                    var before=m.beforePoint();var after=m.afterPoint();
                    var own=m.measuredOwnDisplacement();
                    var worldDelta=new com.juicyslew.moonstation14.ms14.movement.MovementVector(
                            a.after().x-a.before().x,a.after().y-a.before().y,a.after().z-a.before().z);
                    var extraCarrier=new com.juicyslew.moonstation14.ms14.movement.MovementVector(
                            worldDelta.x()-own.x(),worldDelta.y()-own.y(),worldDelta.z()-own.z());
                    String carryWitness="air-release serverTick="+a.serverPhysicalTick()+" beforePoint="+before
                            +" afterPoint="+after+" measuredOwn="+own+" actualWorldDelta="+worldDelta
                            +" extraCarrier="+extraCarrier+" nativeTrace="+m.collisionScope().nativeCollisionTrace().size()
                            +" worldClip="+m.collisionScope().worldClipObservation().input()+"->"
                            +m.collisionScope().worldClipObservation().output();
                    remember(carryWitness);System.out.println("NATURAL_MOVING_AIR_RELEASE_CARRY "+carryWitness);
                    if(before==null||before.frameId()!=null||after==null||after.frameId()!=null||own==null
                            ||!nearZero(extraCarrier)){
                        fail("contact-free AIR->AIR move did not remain world-owned with no extra carrier displacement: "
                                +carryWitness);return false;
                    }
                    verifiedAirReleaseSteps++;
                }
                boolean frameContact=m.afterPoint()!=null&&fixture.plot().engineId().equals(m.afterPoint().frameId())
                        ||m.collisionInfo()!=null&&m.collisionInfo().trackingSubLevel==fixture.plot().subLevel();
                if(boarding&&frameContact&&(!m.collisionInfoFresh()||m.collisionScope()==null
                        ||!m.collisionScope().nativeCollisionTraceComplete()||!m.collisionScope().geometryTraceComplete())){
                    fail("manual-cabin frame contact lacks fresh complete native evidence: "+compact(a));return false;
                }
                if(boarding&&m.afterPoint()!=null&&m.afterPoint().frameId()==null){
                    if(isWorldClipEvidence(m))worldStepEvidence=true;
                }
                if(!boarding&&!m.collisionInfoFresh()){fail("on-cabin attempt lacks fresh native collision result: "+compact(a));return false;}
                remember(compact(a));
                boolean jumpTakeoff=jumpStarted&&a.jump()&&a.wishZ()==(boarding?1000:0)&&(!boarding||a.sprint())
                        &&a.groundedBefore()&&!a.groundedAfter()&&m.requestedOwnDisplacement().y()>=.41
                        &&m.requestedOwnDisplacement().y()<=.43&&a.afterVelocity().y>0;
                if(jumpTakeoff&&boarding&&(m.beforePoint()==null||m.beforePoint().frameId()!=null
                        ||a.beforeVelocity().horizontalDistance()>=.05)){
                    fail("boarding takeoff did not begin world-grounded at rest (<0.05 body blocks/tick): beforePoint="
                            +m.beforePoint()+" beforeVelocity="+a.beforeVelocity()+" "+compact(a));return false;
                }
                if(jumpTakeoff){
                    if(takeoffServerTick==Long.MIN_VALUE){takeoffServerTick=a.serverPhysicalTick();takeoffWorldTick=a.worldGameTick();}
                    airborne=true;ascendingSteps++;
                }else if(jumpStarted&&airborne&&!a.groundedAfter()&&a.afterVelocity().y>0)ascendingSteps++;
                if(boarding&&releaseAckObserved&&!a.jump()&&a.sprint()&&a.wishZ()==1000
                        &&a.serverPhysicalTick()>releaseAckServerTick&&!a.groundedAfter()&&a.afterVelocity().y>0)releasedAscendingSteps++;
                if(airborne&&a.afterVelocity().y<0)descending=true;
                var point=LoadedFrameRayQuery.currentActorPoint(fixture.level(),body);
                if(airborne&&!landed&&a.groundedAfter()){
                    var collision=m.collisionInfo();
                    if(!descending||point==null||!fixture.plot().engineId().equals(point.frameId())||collision==null
                            ||collision.trackingSubLevel!=fixture.plot().subLevel()||!collision.verticalCollisionBelow){
                        fail("landing lacked descending matching-frame floor proof: point="+point+" collision="+collision);return false;
                    }
                    landed=true;supportedAfter=1;
                }else if(landed&&a.groundedAfter()){
                    if(point!=null&&fixture.plot().engineId().equals(point.frameId()))supportedAfter++;
                    else{fail("postlanding support left ship floor");return false;}
                }else if(landed){fail("lost support before three postlanding steps");return false;}
            }
            return true;
        }

        boolean isWorldClipEvidence(SableOwnedBodyMotion.MoveResult m){
            return m!=null&&m.collisionScope()!=null&&m.collisionScope().worldClipObservation()!=null
                    &&m.collisionScope().worldClipObservation().complete();
        }
        boolean isContactFreeWorldAirMove(LifecycleSingleWorldStepObserver.Attempt a,SableOwnedBodyMotion.MoveResult m){
            if(a.callbackCount()!=1||m==null||m.collisionScope()==null
                    ||!m.collisionScope().nativeCollisionTraceComplete()||!m.collisionScope().geometryTraceComplete()
                    ||!m.collisionScope().nativeCollisionTrace().isEmpty()||m.measuredOwnDisplacement()==null)return false;
            var clip=m.collisionScope().worldClipObservation();
            return clip!=null&&clip.complete()&&clip.input()!=null&&clip.output()!=null&&clip.input().equals(clip.output());
        }
        boolean nearZero(com.juicyslew.moonstation14.ms14.movement.MovementVector value){
            double eps=com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.MAX_RESIDUAL;
            return value!=null&&Math.abs(value.x())<=eps&&Math.abs(value.y())<=eps&&Math.abs(value.z())<=eps;
        }
        String compact(LifecycleSingleWorldStepObserver.Attempt a){
            var m=a.move();var p=m==null?null:m.nativeContactProofDiagnostic();
            return "tick="+a.serverPhysicalTick()+"/"+a.worldGameTick()+" input="+a.wishX()+","+a.wishZ()
                    +" jump/sprint="+a.jump()+"/"+a.sprint()+" ground="+a.groundedBefore()+"->"+a.groundedAfter()
                    +" pos="+a.before()+"->"+a.after()+" request="+(m==null?null:m.requestedOwnDisplacement())
                    +" beforePoint="+(m==null?null:m.beforePoint())+" beforeVelocity="+a.beforeVelocity()
                    +" callbacks="+a.callbackCount()+" result="+a.result()+" failure="+a.failure()
                    +" fresh="+(m!=null&&m.collisionInfoFresh())+" afterFrame="+(m==null?null:m.afterPoint())
                    +" proof="+(p==null?null:p.branch()+"/"+p.positionStage()+"/"+p.reason()
                    +" boundedOwn="+p.proofBoundedOwn()+" correction="+p.proofGeometryCorrection()
                    +" clearance="+p.clearanceObservation().token()+" grounding="+p.groundingDecision());
        }
        void remember(String sample){if(evidence.size()==12)evidence.remove(0);evidence.add(sample);}

        void poll(){
            h.runAfterDelay(1,()->{
                if(boarding){lastGap=frameRelativeGap();lastStage="flight gap="+lastGap+" pos="+body.position()
                        +" takeoff="+(takeoffServerTick!=Long.MIN_VALUE)+" branch="+boardBranchSeen
                        +" firstBoardingContactTick="+firstBoardingContactTick+" postContactSteps="+postContactSteps
                        +" releaseAsc="+releasedAscendingSteps+" releaseAck="+releaseAckObserved
                        +" verifiedAirReleaseSteps="+verifiedAirReleaseSteps
                        +" neutralDispatched="+neutralStarted;}
                if(!refreshSnapshots()||!processAttempts())return;
                var owner=fixture.owner();
                if(owner.intentSequence().get()!=lastSubmittedSequence){fail("unexpected intent submission; expected="+lastSubmittedSequence+" actual="+owner.intentSequence().get());return;}
                if(exactAckObserved&&takeoffServerTick!=Long.MIN_VALUE&&!releaseSent){
                    if(boarding){releaseSequence=dispatchIntent(1000,GhostControlPayloads.BUTTON_SPRINT);lastSubmittedSequence=releaseSequence;releaseSent=true;}
                    else{neutralSequence=dispatchIntent(0,0);lastSubmittedSequence=neutralSequence;neutralStarted=true;releaseSent=true;}
                }
                if(boarding&&releaseAckObserved&&postContactSteps>=3&&boardBranchSeen&&!neutralStarted){
                    neutralSequence=dispatchIntent(0,0);lastSubmittedSequence=neutralSequence;neutralStarted=true;
                }
                if(neutralStarted&&neutralAckObserved&&(!boarding||worldStepEvidence&&verifiedAirReleaseSteps>=1)&&landed&&supportedAfter>=3){
                    System.out.println("NATURAL_MOVING_JUMP_LIFECYCLE boarding="+boarding+" attempts="+evidence.size()
                            +" worldEvidence="+worldStepEvidence+" boardBranch="+boardBranchSeen+" releaseAscent="+releasedAscendingSteps
                            +" firstBoardingContactTick="+firstBoardingContactTick+" postContactSteps="+postContactSteps
                            +" releaseAck="+releaseAckObserved+" verifiedAirReleaseSteps="+verifiedAirReleaseSteps
                            +" neutralDispatched="+neutralStarted
                            +" landingSteps="+supportedAfter+" payloadServer/World="+jumpServerSubmitTick+"/"+jumpWorldSubmitTick
                            +" ackServer="+ackServerTick+" takeoffServer/World="+takeoffServerTick+"/"+takeoffWorldTick
                            +" releaseAckServer="+releaseAckServerTick+" stage="+lastStage+" evidence="+evidence);h.succeed();return;
                }
                if(++polls>60){
                    if(neutralStarted&&!neutralAckObserved){
                        fail("neutral-input ACK timeout: submittedSequence="+neutralSequence+" actualSnapshotAck="+latestPayloadAck);return;
                    }
                    fail("natural flight timeout; boardBranch="+boardBranchSeen+" worldEvidence="+worldStepEvidence
                            +" releaseAsc="+releasedAscendingSteps+" firstBoardingContactTick="+firstBoardingContactTick
                            +" postContactSteps="+postContactSteps+" releaseAck="+releaseAckObserved
                            +" verifiedAirReleaseSteps="+verifiedAirReleaseSteps
                            +" neutralDispatched="+neutralStarted
                            +" stage="+lastStage+" gap="+lastGap+" pos="+body.position()+" latest="+latestCompact());return;
                }
                poll();
            });
        }

        String latestCompact(){var all=attempts.get();return all.isEmpty()?"none":compact(all.get(all.size()-1));}
        void fail(String why){h.fail(why+" stage="+lastStage+" gap="+lastGap+" branch="+boardBranchSeen+" evidence="+evidence);}
        void cleanup(){h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            void close(){try{if(scope!=null)scope.close();}catch(Exception e){throw new IllegalStateException(e);}}
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){close();}
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){close();}
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){close();}});}
    }
}
