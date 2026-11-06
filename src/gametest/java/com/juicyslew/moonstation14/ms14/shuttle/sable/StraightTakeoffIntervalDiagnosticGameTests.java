package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.BoundHumanFixture;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleMotionRequest;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleManualFixture;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestInfo;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import java.util.concurrent.atomic.AtomicReference;

/** Server-only capture of one natural takeoff and its exact native Last-to-Logical interval. */
@GameTestTags({"shuttle-straight-takeoff-interval-diagnostic"})
@PrefixGameTestTemplate(false)
public final class StraightTakeoffIntervalDiagnosticGameTests {
    private StraightTakeoffIntervalDiagnosticGameTests() { }
    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=500)
    public static void captureStraightTakeoffInterval(GameTestHelper h) {
        var level=h.getLevel(); var region=SableFixtureScene.acquire(h,new BlockPos(3000,300,2600),32,12,40);
        SableFixtureScene.awaitEmpty(h,region,100,()->{
            SableFixtureScene.authorStoneSupportPad(level,region);
            var authored=SableFixturePlot.allocateAndAuthor(level,new Vector3d(3008.5,300,2605.5),plot->{
                var storage=plot.storageOrigin(); plot.ensureStorageFootprint(storage,ShuttleManualFixture.WIDTH,ShuttleManualFixture.HEIGHT,ShuttleManualFixture.DEPTH);
                return ShuttleManualFixture.build(level,storage,plot.engineId(),plot::setStorageBlock); });
            var plot=authored.plot(); SableFixtureScene.trackPlot(h,plot); var frame=plot.subLevel();
            ShuttleFixtureMotionService.registerFixture(frame); var handle=RigidBodyHandle.of(frame);
            var humanRef=new AtomicReference<PlayerCharacterHarnessEntity>();
            h.testInfo.addListener(new GameTestListener(){private void clean(){var human=humanRef.get();if(human!=null&&human.isAlive())human.discard();ShuttleFixtureMotionService.stop(level,frame);}
                public void testStructureLoaded(GameTestInfo i){} public void testPassed(GameTestInfo i,GameTestRunner r){clean();}
                public void testFailed(GameTestInfo i,GameTestRunner r){clean();} public void testAddedForRerun(GameTestInfo a,GameTestInfo b,GameTestRunner r){clean();}});
            var initial=ShuttleFixtureMotionService.snapshot(level,frame);
            require(handle!=null&&handle.isValid()&&initial!=null&&initial.sdkSubsteps()==6&&initial.configuredGravity().y() < -1
                    &&Double.isFinite(initial.configuredUniversalDrag()),"normal native handle/gravity/drag/six substeps");
            h.runAfterDelay(30,()->{
                var warmed=ShuttleFixtureMotionService.snapshot(level,frame);
                require(!frame.isRemoved()&&RigidBodyHandle.of(frame)!=null&&RigidBodyHandle.of(frame).isValid()
                        &&warmed!=null&&warmed.sdkSubsteps()==6&&warmed.configuredGravity().y() < -1
                        &&Double.isFinite(warmed.configuredUniversalDrag()),"native fixture remains live with normal solver/gravity/drag after 30 ordinary initialization ticks");
                require(ShuttleFixtureMotionService.start(level,frame,new ShuttleMotionRequest(0,0,2,0,240)),"nominal straight request starts");
                 var storage=plot.storageOrigin();
                 awaitTransport(h,level,frame,handle,storage,80,0,()->{
                     requireLaunchClearance(level,frame,storage);
                    Vector3d local=new Vector3d(storage.getX()+13.5,storage.getY()+1.05,storage.getZ()+6.5);
                    Vector3d world=frame.logicalPose().transformPosition(local,new Vector3d());
                    PlayerCharacterHarnessEntity human=BoundHumanFixture.create(level,new net.minecraft.world.phys.Vec3(world.x,world.y,world.z),"straight-takeoff-interval"); humanRef.set(human);
                    System.out.println("TAKEOFF_CLEARANCE {spawnLocal=(13.5,1.05,6.5),floorLocalY=0,freeHeight=5,bodyHeight="+Double.toString(human.getBbHeight())+",jumpVelocity="+Double.toString(com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics.JUMP_VELOCITY_PER_SECOND)+",gravity="+Double.toString(com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics.GRAVITY_PER_SECOND_SQUARED)+",tickSeconds="+Double.toString(com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics.TICK_SECONDS)+"}");
                    restStep(h,human,frame,handle,80,0);
                });
            });
        });
    }
    static void requireLaunchClearance(net.minecraft.server.level.ServerLevel level,ServerSubLevel frame,BlockPos storage){
        for(int x=12;x<=14;x++)for(int z=5;z<=7;z++){
            var floorPos=storage.offset(x,0,z);var floor=LoadedFrameRayQuery.knownServerBlock(level,frame,floorPos);
            require(floor!=null&&!floor.isAir()&&!floor.getCollisionShape(level,floorPos).isEmpty(),"known matching-frame launch floor at "+floorPos);
            double floorTop=floor.getCollisionShape(level,floorPos).max(net.minecraft.core.Direction.Axis.Y);
            require(floorTop==1.0,"manual bay support must retain its authored full-block floor height at "+floorPos+" top="+floorTop);
            for(int y=1;y<=5;y++){var pos=storage.offset(x,y,z);var state=LoadedFrameRayQuery.knownServerBlock(level,frame,pos);
                require(state!=null&&state.isAir()&&state.getCollisionShape(level,pos).isEmpty(),"known collision-free launch clearance cell "+pos+" state="+state);}
        }
        var roofPos=storage.offset(13,6,6);var roof=LoadedFrameRayQuery.knownServerBlock(level,frame,roofPos);
        require(roof!=null&&!roof.isAir()&&!roof.getCollisionShape(level,roofPos).isEmpty(),"known manual-bay roof bounds clearance column");
    }
    private static void awaitTransport(GameTestHelper h,net.minecraft.server.level.ServerLevel l,ServerSubLevel f,RigidBodyHandle body,BlockPos storage,int left,int steady,Runnable ready){
        var s=ShuttleFixtureMotionService.snapshot(l,f); Vector3d sdk=body.getLinearVelocity(new Vector3d()), omega=body.getAngularVelocity(new Vector3d());
        var t=s==null?null:s.transportVelocity();
        var pair=NativeFrameInterval.PosePair.capture(f.lastPose(),f.logicalPose());
        var intervalDelta=deltaAtStorage(pair,new Vector3d(storage.getX()+13.5,storage.getY()+1,storage.getZ()+6.5));
        var intervalRate=intervalDelta.mul(1.0/com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics.TICK_SECONDS);
        boolean ok=s!=null&&s.active()&&s.remainingTicks()>0&&s.sdkSubsteps()==6&&s.request().equals(new ShuttleMotionRequest(0,0,2,0,240))
                &&intervalRate.z()>=1.8&&intervalRate.z()<=2.2&&Math.abs(intervalRate.x)<.25&&Math.abs(intervalRate.y)<.25&&omega.length()<.1;
        if(ok&&steady>=3){ready.run();return;} if(left<=0)throw new GameTestAssertException("transport premise failure request="+(s==null?null:s.request())+" active="+(s!=null&&s.active())+" remaining="+(s==null?-1:s.remainingTicks())+" interval20Hz="+vec(intervalRate)+" SDKsubstep="+vec(sdk)+" COMsubstep="+t+" omega="+vec(omega));
        h.runAfterDelay(1,()->awaitTransport(h,l,f,body,storage,left-1,ok?steady+1:0,ready));
    }
    private static void restStep(GameTestHelper h,PlayerCharacterHarnessEntity human,ServerSubLevel frame,RigidBodyHandle body,int left,int stable){
        var captured=new AtomicReference<SableOwnedBodyMotion.MoveResult>(); var step=new GroundedHarnessWorldStep().step(human,0,0,false,false,0f,captured::set);
        var m=captured.get(); var p=m==null?null:m.beforePoint(); double ownSpeed=human.getDeltaMovement().horizontalDistance()*20;
        if(m==null||step.isEmpty()){System.out.println("TAKEOFF_REST_REJECT {step="+step.isPresent()+",move="+brief(m)+",ground="+human.onGround()+",point="+p+"}");throw new GameTestAssertException("ordinary owned rest move rejected or unobserved");}
        boolean supported=human.onGround()&&p!=null&&frame.getUniqueId().equals(p.frameId());
        int n=supported&&ownSpeed<.05?stable+1:0;
        if(n>=3){h.runAfterDelay(1,()->jump(h,human,frame,body));return;}
        if(left<=1)throw new GameTestAssertException("rest deadline, own horizontal speed="+ownSpeed+"m/s move="+brief(m));
        h.runAfterDelay(1,()->restStep(h,human,frame,body,left-1,n));
    }
    private static void jump(GameTestHelper h,PlayerCharacterHarnessEntity human,ServerSubLevel frame,RigidBodyHandle body){
        var level=h.getLevel(); var pre=human.position(); var box=human.getBoundingBox(); var velocity=human.getDeltaMovement(); boolean grounded=human.onGround();
        var point=LoadedFrameRayQuery.currentActorPoint(level,human); var pointVelocity=point==null?null:SableFrameVelocity.pointVelocityAt(level,point);
        var pair=NativeFrameInterval.PosePair.capture(frame.lastPose(),frame.logicalPose()); Matrix4d last=new Matrix4d().set(pair.last().matrix()),logical=new Matrix4d().set(pair.logical().matrix());
        var controller=ShuttleFixtureMotionService.snapshot(level,frame);
        boolean supported=grounded&&point!=null&&frame.getUniqueId().equals(point.frameId());
        var captured=new AtomicReference<SableOwnedBodyMotion.MoveResult>(); var callbackCount=new java.util.concurrent.atomic.AtomicInteger(); var outcome=new GroundedHarnessWorldStep().step(human,0,0,true,false,0f,m->{callbackCount.incrementAndGet();captured.set(m);});
        var m=captured.get(); var post=human.position(); var postV=human.getDeltaMovement(); var postPoint=LoadedFrameRayQuery.currentActorPoint(level,human);
        String record=record(level,frame,body,human,pre,box,velocity,grounded,point,pointVelocity,pair,last,logical,post,postV,postPoint,m,outcome.isPresent(),supported,controller,callbackCount.get());
        System.out.println("STRAIGHT_TAKEOFF_INTERVAL "+record+",callbackCount="+callbackCount.get()+",yawSamplesComplete="+(scopeComplete(m))+",yawSampleCount="+(m==null||m.collisionScope()==null||m.collisionScope().yawSamples()==null?-1:m.collisionScope().yawSamples().size()));
        var scope=m==null?null:m.collisionScope(); var metrics=metrics(pre,m,post,point,pair);
        boolean yawComplete=scope!=null&&scope.yawSamplesComplete()&&scope.yawSamples()!=null&&!scope.yawSamples().isEmpty()&&scope.yawSamples().size()<=10;
         double intervalZRate=metrics.staticDelta()==null?Double.NaN:metrics.staticDelta().z()/com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics.TICK_SECONDS;
         boolean quality=m!=null&&callbackCount.get()==1&&outcome.isPresent()&&supported&&!human.onGround()&&controller!=null&&controller.active()&&controller.remainingTicks()>0&&controller.sdkSubsteps()==6&&controller.request().equals(new ShuttleMotionRequest(0,0,2,0,240))&&intervalZRate>=1.8&&intervalZRate<=2.2&&yawComplete&&finite(pre)&&finite(velocity)&&finite(post)&&finite(postV)&&finite(pointVelocity)&&finite(last)&&finite(logical)&&metrics.finite();
          if(!quality)throw new GameTestAssertException("takeoff data emitted but complete active-controller/native interval quality conditions failed"); h.succeed();
    }
    private static String record(net.minecraft.server.level.ServerLevel level,ServerSubLevel f,RigidBodyHandle body,PlayerCharacterHarnessEntity human,net.minecraft.world.phys.Vec3 pre,net.minecraft.world.phys.AABB box,net.minecraft.world.phys.Vec3 vel,boolean grounded,LoadedFrameRayQuery.ActorPoint point,net.minecraft.world.phys.Vec3 pv,NativeFrameInterval.PosePair pair,Matrix4d last,Matrix4d logical,net.minecraft.world.phys.Vec3 post,net.minecraft.world.phys.Vec3 postV,LoadedFrameRayQuery.ActorPoint postPoint,SableOwnedBodyMotion.MoveResult m,boolean accepted,boolean supported,ShuttleFixtureMotionService.Snapshot s,int callbackCount){
        net.minecraft.world.phys.Vec3 carrier=m==null?null:post.subtract(pre).subtract(vec(m.measuredOwnDisplacement()));
        Vector3d staticA=point==null?null:new Vector3d(point.storagePoint().x,point.storagePoint().y,point.storagePoint().z), postOwn=null, fdStatic=null,fdOwn=null;
        if(staticA!=null){Vector3d a=new Vector3d(staticA);last.transformPosition(a);Vector3d b=new Vector3d(staticA);logical.transformPosition(b);fdStatic=b.sub(a);}
        if(point!=null&&m!=null){var d=m.measuredOwnDisplacement();Vector3d world=new Vector3d(pre.x+d.x(),pre.y+d.y(),pre.z+d.z());postOwn=new Vector3d(world);last.invert(new Matrix4d()).transformPosition(postOwn);Vector3d a=new Vector3d(postOwn);last.transformPosition(a);Vector3d b=new Vector3d(postOwn);logical.transformPosition(b);fdOwn=b.sub(a);}
        var info=m==null?null:m.collisionInfo(); var corr=m==null?null:m.positionCorrection(); var proof=m==null?null:m.nativeContactProofDiagnostic();
        var scope=m==null?null:m.collisionScope();
        return "{request="+(s==null?null:s.request())+",active="+(s!=null&&s.active())+",remaining="+(s==null?-1:s.remainingTicks())+",sdk="+vec(body.getLinearVelocity(new Vector3d()))+",COM="+(s==null?null:s.transportVelocity())+",angular="+(s==null?null:s.worldAngularVelocity())+",substeps="+(s==null?-1:s.sdkSubsteps())+",gravity="+(s==null?null:s.configuredGravity())+",drag="+(s==null?Double.NaN:s.configuredUniversalDrag())+",pre="+vec(pre)+",preAABB="+box+",preGround="+grounded+",prePoint="+point+",pointVelocity="+vec(pv)+",preVelocity="+vec(vel)+",post="+vec(post)+",postVelocity="+vec(postV)+",postGround="+human.onGround()+",postPoint="+postPoint+",requestedOwn="+(m==null?null:m.requestedOwnDisplacement())+",measuredOwn="+(m==null?null:m.measuredOwnDisplacement())+",acceptedOwn="+(m==null?null:m.acceptedOwnDisplacement())+",boundedOwn="+(m==null?null:m.boundedOwnDisplacement())+",fresh="+(m!=null&&m.collisionInfoFresh())+",motion="+motion(info)+",inherited="+motion(info==null?null:info.inheritedMotion)+",flags="+(info==null?null:(info.verticalCollision+"/"+info.verticalCollisionBelow+"/"+info.horizontalCollision+"/"+info.subLevelHorizontalCollision))+",tracking="+(info==null||info.trackingSubLevel==null?null:info.trackingSubLevel.getUniqueId())+",frameVelocityDelta="+(m==null?null:m.frameVelocityDeltaPerSecond())+",carrier="+vec(carrier)+",posePair="+pair+",storageAnchor="+vec(staticA)+",storageFloorDelta="+vec(fdStatic)+",storageZExcess="+(carrier==null||fdStatic==null?Double.NaN:carrier.z-fdStatic.z)+",postOwnStorageAnchor="+vec(postOwn)+",postOwnFloorDelta="+vec(fdOwn)+",postOwnZExcess="+(carrier==null||fdOwn==null?Double.NaN:carrier.z-fdOwn.z)+",correctionBounded="+(corr==null?null:corr.boundedOwnDisplacement())+",correctionGeometry="+(corr==null?null:corr.geometryCorrection())+",proof="+proof+",yawSamples="+(scope==null?null:scope.yawSamples())+",accepted="+accepted+",preSupported="+supported+"}";
    }
    private static String brief(SableOwnedBodyMotion.MoveResult m){return m==null?"null":"requested="+m.requestedOwnDisplacement()+" measured="+m.measuredOwnDisplacement()+" fresh="+m.collisionInfoFresh()+" proof="+m.nativeContactProofDiagnostic();}
    private record Metrics(Vector3d carrier,Vector3d staticAnchor,Vector3d staticDelta,Vector3d ownAnchor,Vector3d ownDelta,double staticExcess,double ownExcess){boolean finite(){return StraightTakeoffIntervalDiagnosticGameTests.finite(carrier)&&StraightTakeoffIntervalDiagnosticGameTests.finite(staticAnchor)&&StraightTakeoffIntervalDiagnosticGameTests.finite(staticDelta)&&StraightTakeoffIntervalDiagnosticGameTests.finite(ownAnchor)&&StraightTakeoffIntervalDiagnosticGameTests.finite(ownDelta)&&Double.isFinite(staticExcess)&&Double.isFinite(ownExcess);}}
     private static Vector3d deltaAtStorage(NativeFrameInterval.PosePair pair,Vector3d anchor){Matrix4d last=new Matrix4d().set(pair.last().matrix()),logical=new Matrix4d().set(pair.logical().matrix());Vector3d a=new Vector3d(anchor);last.transformPosition(a);Vector3d b=new Vector3d(anchor);logical.transformPosition(b);return b.sub(a);}
     private static Metrics metrics(net.minecraft.world.phys.Vec3 pre,SableOwnedBodyMotion.MoveResult move,net.minecraft.world.phys.Vec3 post,LoadedFrameRayQuery.ActorPoint point,NativeFrameInterval.PosePair pair){
        if(move==null||pre==null||post==null||point==null)return new Metrics(null,null,null,null,null,Double.NaN,Double.NaN);
        var own=move.measuredOwnDisplacement(); Vector3d carrier=new Vector3d(post.x-pre.x-own.x(),post.y-pre.y-own.y(),post.z-pre.z-own.z());
         Matrix4d last=new Matrix4d().set(pair.last().matrix()),logical=new Matrix4d().set(pair.logical().matrix());
         Vector3d anchor=new Vector3d(point.storagePoint().x,point.storagePoint().y,point.storagePoint().z); Vector3d delta=deltaAtStorage(pair,anchor);
        Vector3d world=new Vector3d(pre.x+own.x(),pre.y+own.y(),pre.z+own.z()); Vector3d ownAnchor=new Vector3d(world);last.invert(new Matrix4d()).transformPosition(ownAnchor);Vector3d c=new Vector3d(ownAnchor);last.transformPosition(c);Vector3d d=new Vector3d(ownAnchor);logical.transformPosition(d);Vector3d ownDelta=d.sub(c);
        return new Metrics(carrier,anchor,delta,ownAnchor,ownDelta,carrier.z-delta.z,carrier.z-ownDelta.z);
    }
    private static boolean scopeComplete(SableOwnedBodyMotion.MoveResult m){return m!=null&&m.collisionScope()!=null&&m.collisionScope().yawSamplesComplete();}
    private static net.minecraft.world.phys.Vec3 vec(com.juicyslew.moonstation14.ms14.movement.MovementVector v){return new net.minecraft.world.phys.Vec3(v.x(),v.y(),v.z());}
    private static String vec(Vector3d v){return v==null?"null":"("+Double.toString(v.x)+","+Double.toString(v.y)+","+Double.toString(v.z)+")";}
    private static String vec(net.minecraft.world.phys.Vec3 v){return v==null?"null":"("+Double.toString(v.x)+","+Double.toString(v.y)+","+Double.toString(v.z)+")";}
    private static String motion(net.minecraft.world.phys.Vec3 v){return vec(v);}
    private static String motion(dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision.CollisionInfo info){return info==null||info.motion==null?"null":vec(new net.minecraft.world.phys.Vec3(info.motion.x,info.motion.y,info.motion.z));}
    private static boolean finite(net.minecraft.world.phys.Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static boolean finite(Vector3d v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static boolean finite(Matrix4d m){double[] a=new double[16];m.get(a);for(double v:a)if(!Double.isFinite(v))return false;return true;}
    private static void require(boolean b,String why){if(!b)throw new GameTestAssertException(why);}
}
