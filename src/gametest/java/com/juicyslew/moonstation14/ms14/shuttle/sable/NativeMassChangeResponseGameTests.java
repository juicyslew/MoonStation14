package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleMotionRequest;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.function.Consumer;

/** Live authored-block mass-property refresh checks at the first completed POST boundary after each ordinary edit. */
@GameTestTags({"shuttle-mass-edit-response", "shuttle"})
@PrefixGameTestTemplate(false)
public final class NativeMassChangeResponseGameTests {
    private NativeMassChangeResponseGameTests() { }
    private static final int BASE_Y=240, SPAWN_Y=288;
    enum EditPhase { NONE, ADD_PENDING, REMOVE_PENDING }

    @GameTest(templateNamespace="moonstation14",template="empty",timeoutTicks=120)
    public static void freeBodyMassEditRefreshesNativeInertia(GameTestHelper helper){setup(helper,9400,false);}
    @GameTest(templateNamespace="moonstation14",template="empty",timeoutTicks=120)
    public static void pilotedBodyMassEditRefreshesNativeInertia(GameTestHelper helper){setup(helper,9600,true);}

    private static void setup(GameTestHelper helper,int x,boolean piloted){
        var level=helper.getLevel(); var region=SableFixtureScene.acquire(helper,new BlockPos(x,BASE_Y,6000),24,72,24);
        if(BASE_Y-1<level.getMinBuildHeight()||BASE_Y+71>=level.getMaxBuildHeight()){
            helper.fail("mass-edit fixture outside build bounds");return;
        }
        SableFixtureScene.awaitEmpty(helper,region,80,()->{
            var built=SableFixturePlot.allocateAndAuthor(level,new Vector3d(x+8.5,SPAWN_Y,6005.5),plot->{
                var o=plot.storageOrigin(); plot.ensureStorageFootprint(o,3,2,1);
                plot.setStorageBlock(o,Blocks.STONE.defaultBlockState());
                plot.setStorageBlock(o.offset(1,0,0),Blocks.STONE.defaultBlockState());
                plot.setStorageBlock(o.offset(0,1,0),Blocks.STONE.defaultBlockState());
                return o.immutable();
            });
            SableFixtureScene.trackPlot(helper,built.plot()); ServerSubLevel owner=built.plot().subLevel();
            if(piloted){ShuttleFixtureMotionService.registerFixture(owner);
                require(ShuttleFixtureMotionService.start(level,owner,new ShuttleMotionRequest(0,0,0,3,80)),"controller fixture starts");}
            var obs=new EditObserver(helper,level,owner,RigidBodyHandle.of(owner),built.result(),built.plot(),piloted);
            obs.start();
        });
    }
    private static final class EditObserver{
        final GameTestHelper helper;final ServerLevel level;final ServerSubLevel owner;final RigidBodyHandle body;
        final BlockPos storage;final SableFixturePlot plot;final boolean piloted;
        Consumer<ForgeSablePostPhysicsTickEvent> listener;boolean closed,baseline,added,removed;int calls;
        double initialMass;Matrix3d initialI;Vector3d initialCom;long editTick;EditPhase pending=EditPhase.NONE;int editGeneration;
        TensorRoundTripBudget tensorBudget;
        EditObserver(GameTestHelper h,ServerLevel l,ServerSubLevel o,RigidBodyHandle b,BlockPos s,SableFixturePlot p,boolean ctrl){helper=h;level=l;owner=o;body=b;storage=s;plot=p;piloted=ctrl;
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){} public void testPassed(GameTestInfo i,GameTestRunner r){close();}
                public void testFailed(GameTestInfo i,GameTestRunner r){close();} public void testAddedForRerun(GameTestInfo a,GameTestInfo b,GameTestRunner r){close();}});}
        void start(){listener=this::post;NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,listener);}
        void close(){if(closed)return;closed=true;if(listener!=null)NeoForge.EVENT_BUS.unregister(listener);}
        void post(ForgeSablePostPhysicsTickEvent e){if(closed||e.getPhysicsSystem().getLevel()!=level)return;
            if(owner.isRemoved()||owner.getLevel()!=level){fail(new IllegalStateException("owner removed"));return;}
            try{
                if(++calls>60)throw new IllegalStateException("bounded native readiness/edit callbacks exceeded");
                if(!body.isValid())throw new IllegalStateException("invalid native rigid body");
                checkClearance(); var tracker=owner.getMassTracker();
                if(tracker==null||!Double.isFinite(tracker.getMass())||tracker.getMass()<=0){
                    if(pending!=EditPhase.NONE)throw new IllegalStateException("first completed POST missing updated mass tracker phase="+pending+" generation="+editGeneration+" observedTick="+level.getGameTime());
                    return;
                }
                var tensor=new Matrix3d(tracker.getInertiaTensor());var com=tracker.getCenterOfMass();
                var pose=e.getPhysicsSystem().getPipeline().readPose(owner,new Pose3d());
                if(!baseline){
                    var readinessProbe=probe(tensor,pose.orientation());if(!readinessProbe.passed())return;
                    initialMass=tracker.getMass();initialI=tensor;initialCom=new Vector3d(com);
                    var omega=body.getAngularVelocity(new Vector3d());
                    Vector3d impulse=tensor.transform(new Vector3d(.7,3,.4).sub(omega).rotate(new Quaterniond(pose.orientation()).conjugate()));
                    body.applyLinearAndAngularImpulse(new Vector3d(),impulse);
                    baseline=true;editTick=level.getGameTime();
                    helper.runAfterDelay(1,()->edit(Blocks.STONE.defaultBlockState(),EditPhase.ADD_PENDING,"ballast addition"));
                    return;
                }
                if(pending!=EditPhase.NONE){
                    EditPhase observed=pending; pending=EditPhase.NONE; // consume exactly the first completed POST boundary after this edit
                    double massDelta=tracker.getMass()-initialMass;Vector3d comDelta=new Vector3d(com).sub(initialCom);
                    double tensorDelta=maxTensorDifference(tensor,initialI);
                    if(observed==EditPhase.ADD_PENDING)tensorBudget=roundTripBudget(initialMass,tracker.getMass(),initialCom,new Vector3d(com),fixtureCenters(storage),initialI,tensor);
                    if(tensorBudget==null)throw new IllegalStateException("round-trip tensor error budget unavailable at first completed edit boundary");
                    boolean changed=observed==EditPhase.ADD_PENDING
                            ?Math.abs(massDelta)>=1e-8&&comDelta.length()>=1e-8&&tensorMatchesEdit(observed,tensorDelta,tensorBudget.maxTensorDrift())
                            :Math.abs(massDelta)<=1e-8&&comDelta.length()<=1e-7&&tensorMatchesEdit(observed,tensorDelta,tensorBudget.maxTensorDrift());
                    var probeResult=probe(tensor,pose.orientation());boolean response=probeResult.passed();
                    String observation="phase="+observed+" generation="+editGeneration+" editTick="+editTick+" observedTick="+level.getGameTime()+" callback="+calls
                            +" trackerMass="+Double.toString(tracker.getMass())+" baselineMass="+Double.toString(initialMass)+" massDelta="+Double.toString(massDelta)
                            +" currentCOM="+vec(com)+" baselineCOM="+vec(initialCom)+" COMDelta="+vec(comDelta)
                            +" currentI="+matrix(tensor)+" baselineI="+matrix(initialI)+" deltaI="+matrixDelta(tensor,initialI)
                            +" maxAbsTensorDelta="+Double.toString(tensorDelta)+" sourceRoundoffBudget="+tensorBudget
                            +" sameTensorExact="+sameTensor(tensor,initialI)+" trackerExpected="+changed+" nativeProbePassed="+response
                            +" nativeProbeAxes="+probeDetails(probeResult)+" Q="+quat(pose.orientation())+" omegaWorld="+vec(body.getAngularVelocity(new Vector3d()));
                    if(!validFirstCompletedBoundary(observed,changed,response))throw new IllegalStateException("first completed POST boundary failed mass/native inertia refresh: "+observation);
                    MoonStation14.LOGGER.info("[native mass edit first-POST PASS] {}",observation);
                    if(observed==EditPhase.ADD_PENDING){added=true;helper.runAfterDelay(1,()->edit(Blocks.AIR.defaultBlockState(),EditPhase.REMOVE_PENDING,"ballast removal"));}
                    else {removed=true;close();helper.succeed();}
                }
            }catch(RuntimeException ex){fail(ex);}
        }
        void edit(net.minecraft.world.level.block.state.BlockState state,EditPhase phase,String label){
            if(closed)return;
            var omega=body.getAngularVelocity(new Vector3d());
            if(!Double.isFinite(omega.x)||!Double.isFinite(omega.y)||!Double.isFinite(omega.z)||omega.length()<=.1){
                fail(new IllegalStateException(label+" requires actual nontrivial angular velocity before edit; omega="+omega));return;}
            var beforePose=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level).getPipeline().readPose(owner,new Pose3d());
            if(piloted){var snapshot=ShuttleFixtureMotionService.snapshot(level,owner);
                if(snapshot==null||!snapshot.active()){fail(new IllegalStateException(label+" requires active controller snapshot; status="+ShuttleFixtureMotionService.status(level,owner)));return;}}
            MoonStation14.LOGGER.info("[native mass edit before] phase={} generation={} tick={} omegaWorld={} Q={} controllerActive={}",phase,editGeneration+1,level.getGameTime(),omega,beforePose.orientation(),piloted);
            boolean ok=plot.setStorageBlock(storage.offset(2,0,0),state);
            if(!ok){fail(new IllegalStateException("ordinary "+label+" rejected"));return;}
            pending=phase;editGeneration++;editTick=level.getGameTime();
        }
        NativeAngularImpulseProbe.Result probe(Matrix3dc i,org.joml.Quaterniondc q){
            return NativeAngularImpulseProbe.run(body,i,q);
        }
        void checkClearance(){var b=owner.boundingBox().toMojang().inflate(.2);if(b.minY<260||b.maxY>=311)throw new IllegalStateException("native hull left bounded clearance="+b);
            int ax=BlockPos.containing(b.minX,b.minY,b.minZ).getX()>>4,bx=BlockPos.containing(b.maxX,b.maxY,b.maxZ).getX()>>4;
            int az=BlockPos.containing(b.minX,b.minY,b.minZ).getZ()>>4,bz=BlockPos.containing(b.maxX,b.maxY,b.maxZ).getZ()>>4;
            for(int x=ax;x<=bx;x++)for(int z=az;z<=bz;z++)if(level.getChunkSource().getChunk(x,z,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false)==null)throw new IllegalStateException("unknown unloaded collision chunk");
            if(level.getBlockCollisions(null,b).iterator().hasNext())throw new IllegalStateException("native hull contacts world geometry="+b);}
        void fail(Throwable cause){
            close();String message="native mass edit response terminal failure phase="+pending+" generation="+editGeneration
                    +" editTick="+editTick+" observedTick="+level.getGameTime()+" callback="+calls+" cause="+cause.getClass().getName()+": "+cause.getMessage();
            MoonStation14.LOGGER.error("[native mass edit failure] {}",message,cause);
            helper.testInfo.fail(new GameTestAssertException(message));
        }
    }
    static boolean validFirstCompletedBoundary(EditPhase phase,boolean trackerMatchesExpected,boolean nativeResponseMatchesExpected){
        return phase!=EditPhase.NONE&&trackerMatchesExpected&&nativeResponseMatchesExpected;
    }
    static boolean tensorMatchesEdit(EditPhase phase,double maxDelta,double bound){
        return Double.isFinite(maxDelta)&&Double.isFinite(bound)&&bound>0
                &&(phase==EditPhase.ADD_PENDING?maxDelta>bound:phase==EditPhase.REMOVE_PENDING&&maxDelta<=bound);
    }
    private static String vec(org.joml.Vector3dc v){return "["+Double.toString(v.x())+','+Double.toString(v.y())+','+Double.toString(v.z())+"]";}
    private static String quat(org.joml.Quaterniondc q){return "["+Double.toString(q.x())+','+Double.toString(q.y())+','+Double.toString(q.z())+','+Double.toString(q.w())+"]";}
    private static String matrix(Matrix3dc m){return "["+Double.toString(m.m00())+','+Double.toString(m.m01())+','+Double.toString(m.m02())+';'+Double.toString(m.m10())+','+Double.toString(m.m11())+','+Double.toString(m.m12())+';'+Double.toString(m.m20())+','+Double.toString(m.m21())+','+Double.toString(m.m22())+"]";}
    private static String matrixDelta(Matrix3dc a,Matrix3dc b){return matrix(new Matrix3d(a).sub(b));}
    private static String probeDetails(NativeAngularImpulseProbe.Result result){
        StringBuilder out=new StringBuilder("[");
        for(var axis:result.axes()){
            if(out.length()>1)out.append(';');
            Vector3d observed=axis.after()==null?new Vector3d(Double.NaN):new Vector3d(axis.after()).sub(axis.before());
            out.append("axis=").append(vec(axis.axis())).append(" J=").append(vec(axis.impulse()))
                    .append(" before=").append(vec(axis.before())).append(" expectedDelta=").append(vec(axis.expected()))
                    .append(" observedDelta=").append(vec(observed)).append(" responseError=")
                    .append(Double.toString(observed.distance(axis.expected())))
                    .append(" restored=").append(vec(axis.restored())).append(" restoreError=")
                    .append(Double.toString(axis.restored().distance(axis.before())))
                    .append(" responseOK=").append(axis.responseOk()).append(" restoreOK=").append(axis.restoredOk());
        }
        return out.append(']').toString();
    }
    private static boolean sameTensor(Matrix3dc a,Matrix3dc b){return a.m00()==b.m00()&&a.m01()==b.m01()&&a.m02()==b.m02()&&a.m10()==b.m10()&&a.m11()==b.m11()&&a.m12()==b.m12()&&a.m20()==b.m20()&&a.m21()==b.m21()&&a.m22()==b.m22();}
    record TensorRoundTripBudget(double maxTensorDrift,double addComComponentError,double removeComComponentError,
                                 double radius,double operationScale,int comOperationCount,int inertiaOperationCount) { }
    static TensorRoundTripBudget roundTripBudget(double baseMass,double addedMass,org.joml.Vector3dc baseCom,
            org.joml.Vector3dc addedCom,org.joml.Vector3dc[] centers,Matrix3dc baseI,Matrix3dc addedI){
        if(!Double.isFinite(baseMass)||!Double.isFinite(addedMass)||baseMass<=0||addedMass<=baseMass
                ||baseCom==null||addedCom==null||centers==null||centers.length!=4||baseI==null||addedI==null)
            throw new IllegalArgumentException("invalid finite three-to-four cell round-trip inputs");
        double ballastMass=addedMass-baseMass, u=Math.scalb(1.0,-53);
        // Pinned Sable ad8 MassTracker.addBlockMass (source lines 197-242): weighted COM update is two
        // multiplies, add/subtract, and divide; gamma(4) bounds each component. Initial 3-cell capture
        // is bounded identically. Removing the same mass propagates prior COM error by M_old/M_new.
        double initialWeightedMagnitude=0;
        for(int i=0;i<3;i++)initialWeightedMagnitude+=baseMass/3*(absMax(centers[i])+absMax(baseCom));
        double initialE=gamma(4,u)*initialWeightedMagnitude/baseMass;
        var ballast=centers[3];
        double addE=baseMass/addedMass*initialE+gamma(4,u)*(baseMass*absMax(baseCom)+ballastMass*absMax(ballast))/addedMass;
        double removeE=addedMass/baseMass*addE+gamma(4,u)*(addedMass*absMax(addedCom)+ballastMass*absMax(ballast))/baseMass;
        double radius=0;
        for(var center:centers)radius=Math.max(radius,Math.max(center.distance(baseCom),center.distance(addedCom)));
        double maxTensor=Math.max(maxAbsTensor(baseI),maxAbsTensor(addedI));
        // Parallel-axis entry: products, subtraction, and accumulation; k=32 exceeds the source's
        // per-entry multiply/add count. S bounds all four-cell mass*radius^2 intermediates plus I.
        int k=32;double scale=maxTensor+4*addedMass*radius*radius;
        double tensorRound=2*gamma(k,u)*scale;
        double comSensitive=ballastMass*(4*radius*(initialE+addE+removeE)+2*(initialE*initialE+addE*addE+removeE*removeE));
        double budget=tensorRound+comSensitive;
        double physicalAdd=maxTensorDifference(addedI,baseI);
        if(!Double.isFinite(budget)||!Double.isFinite(radius)||!Double.isFinite(scale)||budget<=0||budget>=physicalAdd/1000)
            throw new IllegalArgumentException("roundoff bound is nonfinite or not separated from physical edit: budget="+budget+" physicalAdd="+physicalAdd);
        return new TensorRoundTripBudget(budget,addE,removeE,radius,scale,4,k);
    }
    private static double gamma(int k,double u){return k*u/(1-k*u);}
    private static double absMax(org.joml.Vector3dc v){return Math.max(Math.abs(v.x()),Math.max(Math.abs(v.y()),Math.abs(v.z())));}
    private static double maxAbsTensor(Matrix3dc m){return Math.max(Math.max(Math.max(Math.abs(m.m00()),Math.abs(m.m01())),Math.max(Math.abs(m.m02()),Math.abs(m.m10()))),Math.max(Math.max(Math.abs(m.m11()),Math.abs(m.m12())),Math.max(Math.abs(m.m20()),Math.max(Math.abs(m.m21()),Math.abs(m.m22())))));}
    static double maxTensorDifference(Matrix3dc a,Matrix3dc b){return Math.max(Math.max(Math.max(Math.abs(a.m00()-b.m00()),Math.abs(a.m01()-b.m01())),Math.max(Math.abs(a.m02()-b.m02()),Math.abs(a.m10()-b.m10()))),Math.max(Math.max(Math.abs(a.m11()-b.m11()),Math.abs(a.m12()-b.m12())),Math.max(Math.abs(a.m20()-b.m20()),Math.max(Math.abs(a.m21()-b.m21()),Math.abs(a.m22()-b.m22())))));}
    private static org.joml.Vector3dc[] fixtureCenters(BlockPos storage){return new org.joml.Vector3dc[]{new Vector3d(storage.getX()+.5,storage.getY()+.5,storage.getZ()+.5),new Vector3d(storage.getX()+1.5,storage.getY()+.5,storage.getZ()+.5),new Vector3d(storage.getX()+.5,storage.getY()+1.5,storage.getZ()+.5),new Vector3d(storage.getX()+2.5,storage.getY()+.5,storage.getZ()+.5)};}
    private static void require(boolean ok,String message){if(!ok)throw new GameTestAssertException(message);}
}
