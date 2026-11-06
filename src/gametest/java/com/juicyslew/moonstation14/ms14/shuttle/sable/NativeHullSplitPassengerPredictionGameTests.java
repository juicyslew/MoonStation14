package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.BoundHumanFixture;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep;
import com.juicyslew.moonstation14.ms14.player_body_control.client.ClientFramePredictionState;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Exercises automatic stock heat-map splitting and naturally transferred passenger prediction basis. */
@GameTestTags({"shuttle-split-passenger-prediction"})
@PrefixGameTestTemplate(false)
public final class NativeHullSplitPassengerPredictionGameTests {
    private static final int WAIT = 300;
    private NativeHullSplitPassengerPredictionGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 1100)
    public static void normalBridgeRemovalAutomaticallySplitsPassengerHull(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(2200, 300, 1800);
        var scene = SableFixtureScene.acquire(helper, origin, 30, 12, 24);
        SableFixtureScene.awaitEmpty(helper, scene, 100, () -> {
            SableFixtureScene.authorStoneSupportPad(level, scene);
            Vector3d worldOrigin = new Vector3d(origin.getX() + 8, origin.getY(), origin.getZ() + 7);
            var authored = SableFixturePlot.allocateAndAuthor(level, worldOrigin, plot -> {
                BlockPos storage = plot.storageOrigin();
                plot.ensureStorageFootprint(storage, 18, 1, 8);
                for (int x=0;x<8;x++) for(int z=0;z<8;z++) put(plot,storage.offset(x,0,z));
                for (int x=11;x<17;x++) for(int z=1;z<7;z++) put(plot,storage.offset(x,0,z));
                for (int x=8;x<11;x++) put(plot,storage.offset(x,0,4));
                return storage;
            });
            SableFixturePlot plot=authored.plot();
            SableFixtureScene.trackPlot(helper,plot);
            BlockPos storage=authored.result();
            SableFixtureScene.requireStoneSupportUnderProjectedFootprint(level,scene,plot,storage,18,8);
            SableFixtureScene.awaitStationaryFrame(helper,plot,120,()->{
                Vector3d feet=plot.subLevel().logicalPose().transformPosition(new Vector3d(storage.getX()+13.5,storage.getY()+1.01,storage.getZ()+4.5));
                var human=BoundHumanFixture.create(level,new Vec3(feet.x,feet.y,feet.z),"automatic-split-passenger");
                helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
                    private void close(){if(!human.isRemoved())human.discard();}
                    @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
                    @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){close();}
                    @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){close();}
                    @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){close();}
                });
                new Run(helper,level,plot,storage,human).initial(WAIT);
            });
        });
    }

    private static void put(SableFixturePlot plot,BlockPos p){if(!plot.setStorageBlock(p,Blocks.STONE.defaultBlockState()))throw new GameTestAssertException("fixture hull write rejected at "+p);}

    private static final class Run {
        final GameTestHelper h; final ServerLevel level; final SableFixturePlot parent; final BlockPos storage;
        final com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human;
        final GroundedHarnessWorldStep step=GroundedHarnessWorldStep.observingNativeGeometry();
        final ClientFramePredictionState prediction=new ClientFramePredictionState();
        final Set<UUID> existing=new HashSet<>();
        long stepped=Long.MIN_VALUE; UUID humanId; boolean cut, probeApplied; ServerSubLevel child; SableFixturePlot childCleanup; int transferred;
        SableOwnedBodyMotion.MoveResult lastMove; LoadedFrameRayQuery.ActorPoint preCutPoint; Vec3 preCutWorld;
         Vector3d passengerWorldAnchor, retainedLobeAnchor, probeLocal, probeWorld; NativeFrameInterval.PosePair consumedChildPair;
        Run(GameTestHelper h,ServerLevel l,SableFixturePlot p,BlockPos s,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity b){
            this.h=h;level=l;parent=p;storage=s;human=b;
            for(var sub:SubLevelContainer.getContainer(l).getAllSubLevels())existing.add(sub.getUniqueId());
        }
        void stepOnce(){long tick=level.getServer().getTickCount();if(stepped!=tick){stepped=tick;final SableOwnedBodyMotion.MoveResult[] result={null};step.step(human,0,0,false,false,0f,v->result[0]=v);lastMove=result[0];}}
        void initial(int left){stepOnce();var p=LoadedFrameRayQuery.currentActorPoint(level,human);
            if(human.onGround()&&p!=null&&parent.engineId().equals(p.frameId())){humanId=human.getUUID();preCutPoint=p;preCutWorld=human.position();passengerWorldAnchor=parent.subLevel().logicalPose().transformPosition(new Vector3d(p.storagePoint().x,p.storagePoint().y,p.storagePoint().z));retainedLobeAnchor=parent.subLevel().logicalPose().transformPosition(new Vector3d(storage.getX()+3.5,storage.getY(),storage.getZ()+3.5));h.runAfterDelay(1,this::cutAndPoll);return;}
            if(left<=0)throw fail("passenger did not naturally settle/track on source hull: "+p);
            h.runAfterDelay(1,()->initial(left-1));}
        void cutAndPoll(){stepOnce();var p=LoadedFrameRayQuery.currentActorPoint(level,human);
            if(!human.onGround()||p==null||!parent.engineId().equals(p.frameId()))throw fail("initial source passenger witness lost: "+p);
            checkPreProbeContinuity();
            if(!cut){cut=true;for(int x=8;x<11;x++)if(!parent.setStorageBlock(storage.offset(x,0,4),Blocks.AIR.defaultBlockState()))throw fail("bridge removal rejected");}
            poll(WAIT);}
        void poll(int left){stepOnce();if(child==null)child=findChild();
            checkPreProbeContinuity();
            if(child!=null&&childGeometry()){if(childCleanup==null){var b=childFootprint();childCleanup=SableFixturePlot.wrapAssembled(level,child,new BlockPos(b[0],b[1],b[2]),b[3],b[4],b[5]);SableFixtureScene.trackPlot(h,childCleanup);}if(!sourceBridgeAir()||!sourceDetachedAir())throw fail("source cells were not removed by native split");
                UUID marker=child.getSplitFromSubLevel();if(marker!=null&&!marker.equals(parent.engineId()))throw fail("split marker names wrong parent: "+marker);
                transfer(WAIT);return;}
            if(left<=0)throw fail("stock split did not yield a new child with projected detached lobe");
            h.runAfterDelay(1,()->poll(left-1));}
        ServerSubLevel findChild(){for(var s:SubLevelContainer.getContainer(level).getAllSubLevels())if(s instanceof ServerSubLevel ss&&!existing.contains(ss.getUniqueId())&&!ss.getUniqueId().equals(parent.engineId())&&hasDetachedWitness(ss))return ss;return null;}
        boolean hasDetachedWitness(ServerSubLevel s){Vector3d w=parent.subLevel().logicalPose().transformPosition(new Vector3d(storage.getX()+13.5,storage.getY()+.5,storage.getZ()+4.5));Vector3d q=s.logicalPose().transformPositionInverse(w);var state=LoadedFrameRayQuery.knownServerBlock(level,s,BlockPos.containing(q.x,q.y,q.z));return state!=null&&state.is(Blocks.STONE);}
        boolean childGeometry(){for(int x=11;x<17;x++)for(int z=1;z<7;z++){Vector3d w=parent.subLevel().logicalPose().transformPosition(new Vector3d(storage.getX()+x+.5,storage.getY()+.5,storage.getZ()+z+.5));Vector3d q=child.logicalPose().transformPositionInverse(w);var state=LoadedFrameRayQuery.knownServerBlock(level,child,BlockPos.containing(q.x,q.y,q.z));if(state==null||!state.is(Blocks.STONE))return false;}return true;}
        int[] childFootprint(){double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY,minZ=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;for(int x:new int[]{11,17})for(int y:new int[]{0,1})for(int z:new int[]{1,7}){Vector3d w=parent.subLevel().logicalPose().transformPosition(new Vector3d(storage.getX()+x,storage.getY()+y,storage.getZ()+z));Vector3d q=child.logicalPose().transformPositionInverse(w);minX=Math.min(minX,q.x);minY=Math.min(minY,q.y);minZ=Math.min(minZ,q.z);maxX=Math.max(maxX,q.x);maxY=Math.max(maxY,q.y);maxZ=Math.max(maxZ,q.z);}int ox=(int)Math.floor(minX),oy=(int)Math.floor(minY),oz=(int)Math.floor(minZ);return new int[]{ox,oy,oz,(int)Math.ceil(maxX)-ox,(int)Math.ceil(maxY)-oy,(int)Math.ceil(maxZ)-oz};}
        boolean sourceBridgeAir(){for(int x=8;x<11;x++){var s=LoadedFrameRayQuery.knownServerBlock(level,parent.subLevel(),storage.offset(x,0,4));if(s==null||!s.isAir())return false;}return true;}
        boolean sourceDetachedAir(){for(int x=11;x<17;x++)for(int z=1;z<7;z++){var s=LoadedFrameRayQuery.knownServerBlock(level,parent.subLevel(),storage.offset(x,0,z));if(s==null||!s.isAir())return false;}return true;}
         double neutralBudget(Vec3 reference){double ulp=Math.max(Math.ulp((float)reference.x),Math.max(Math.ulp((float)reference.y),Math.ulp((float)reference.z)));return .005+16*ulp;}
         void checkPreProbeContinuity(){if(!cut||probeApplied)return;if(!humanId.equals(human.getUUID()))throw fail("passenger identity changed");Vector3d anchor=parent.subLevel().logicalPose().transformPosition(new Vector3d(storage.getX()+3.5,storage.getY(),storage.getZ()+3.5));if(anchor.distance(retainedLobeAnchor)>.005)throw fail("fixture premise failed: retained source lobe moved beyond stationary-frame .005 bound");double budget=neutralBudget(preCutWorld);if(human.position().distanceTo(preCutWorld)>budget)throw fail("passenger moved discontinuously across stock split beyond fixture stationarity plus float-ULP bound "+budget);}
        void transfer(int left){stepOnce();var point=LoadedFrameRayQuery.currentActorPoint(level,human);
            if(point!=null&&human.getUUID().equals(humanId)&&child.getUniqueId().equals(point.frameId())){
                checkPreProbeContinuity();transferred++;if(human.onGround()&&transferred>=2){var pair=NativeFrameInterval.PosePair.capture(child.lastPose(),child.logicalPose());
                    prediction.authorityProjected(preCutPoint,1,NativeFrameInterval.PosePair.capture(parent.subLevel().lastPose(),parent.subLevel().logicalPose()));
                    if(prediction.allowNativeCarrier(true,1,point,pair))throw fail("child candidate admitted against old basis");
                    prediction.authorityProjected(point,1,pair);
                    if(prediction.allowNativeCarrier(true,1,point,pair)||prediction.allowNativeCarrier(false,1,point,pair)||prediction.allowNativeCarrier(true,1,point,pair))throw fail("projected child interval replay admitted");
                     consumedChildPair=pair;probeLocal=new Vector3d(point.storagePoint().x,point.storagePoint().y,point.storagePoint().z);probeWorld=child.logicalPose().transformPosition(new Vector3d(probeLocal));dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(child)
                             .addLinearAndAngularVelocity(new Vector3d(.5,0,0),new Vector3d());probeApplied=true;
                     h.runAfterDelay(1,()->fresh(WAIT));return;}}
            if(left<=0)throw fail("native passenger tracking did not transfer within bound: "+point);
            h.runAfterDelay(1,()->transfer(left-1));}
         void fresh(int left){stepOnce();checkPreProbeContinuity();var after=LoadedFrameRayQuery.currentActorPoint(level,human);
            if(lastMove==null)throw fail("child native step omitted move result");
             if(human.onGround()&&after!=null&&child.getUniqueId().equals(after.frameId())&&lastMove.collisionInfoFresh()&&lastMove.collisionScope()!=null&&lastMove.collisionScope().nativeCollisionTraceComplete()){
                 var pair=NativeFrameInterval.PosePair.capture(child.lastPose(),child.logicalPose());Vector3d probePosition=child.logicalPose().transformPosition(new Vector3d(probeLocal));Vec3 currentProbe=new Vec3(probePosition.x,probePosition.y,probePosition.z);Vec3 probeOrigin=new Vec3(probeWorld.x,probeWorld.y,probeWorld.z);double bound=neutralBudget(probeOrigin);
                 if(human.position().distanceTo(currentProbe)>bound)throw fail("passenger diverged from independent physical child probe beyond fixture stationarity plus float-ULP bound "+bound);
                 if(probeOrigin.distanceTo(currentProbe)>bound){if(!pair.equals(consumedChildPair)&&prediction.allowNativeCarrier(true,1,after,pair)){if(prediction.allowNativeCarrier(true,1,after,pair))throw fail("physical child interval replay admitted");h.succeed();return;}}
            }
            if(left<=0)throw fail("native horizontal probe did not produce supported child movement and a changed physical interval within bound");
            h.runAfterDelay(1,()->fresh(left-1));}
        GameTestAssertException fail(String m){return new GameTestAssertException(m);}
    }
}
