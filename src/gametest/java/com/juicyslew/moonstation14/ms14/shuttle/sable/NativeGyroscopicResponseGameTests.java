package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.*;
import java.util.function.Consumer;

/** Controller-free diagnostic regression: R29 confirmed the principal-frame backend omission; PASS requires FRAME_CORRECT. */
@GameTestTags({"shuttle-gyroscopic-response", "shuttle"})
@PrefixGameTestTemplate(false)
public final class NativeGyroscopicResponseGameTests {
    private NativeGyroscopicResponseGameTests() { }
    private static final double DT = 1.0 / 120.0;
    private static final Vector3d SEED = new Vector3d(.7, 3, .4);
    static final int SCENE_BASE_Y=240, SCENE_HEIGHT=72, SPAWN_Y=288, MIN_HULL_Y=260;

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=200)
    public static void uprightNativeGyroscopicResponse(GameTestHelper helper) { setup(helper, 9000, false); }
    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=200)
    public static void tiltedNativeGyroscopicResponse(GameTestHelper helper) { setup(helper, 9200, true); }

    private static void setup(GameTestHelper helper, int x, boolean tilted) {
        modelSelfCheck();
        ServerLevel level = helper.getLevel();
        int sceneMin=SCENE_BASE_Y-1, sceneMax=SCENE_BASE_Y+SCENE_HEIGHT-1;
        require(validSceneBounds(level.getMinBuildHeight(),level.getMaxBuildHeight(),sceneMin,sceneMax),
                "gyro scene outside build bounds: scene="+sceneMin+".."+sceneMax+" level="
                        +level.getMinBuildHeight()+".."+level.getMaxBuildHeight());
        var region = SableFixtureScene.acquire(helper, new BlockPos(x, SCENE_BASE_Y, 6000), 24, SCENE_HEIGHT, 24);
        SableFixtureScene.awaitEmpty(helper, region, 80, () -> {
            var authored = SableFixturePlot.allocateAndAuthor(level, new Vector3d(x + 8.5, SPAWN_Y, 6005.5), plot -> {
                var o = plot.storageOrigin();
                plot.ensureStorageFootprint(o, 3, 2, 1);
                plot.setStorageBlock(o, Blocks.STONE.defaultBlockState());
                plot.setStorageBlock(o.offset(1, 0, 0), Blocks.STONE.defaultBlockState());
                plot.setStorageBlock(o.offset(0, 1, 0), Blocks.STONE.defaultBlockState());
                return Boolean.TRUE;
            });
            SableFixtureScene.trackPlot(helper, authored.plot());
            ServerSubLevel owner = authored.plot().subLevel();
            var handle = RigidBodyHandle.of(owner);
            require(handle != null && handle.isValid(), "authored free-flight native body valid");
            if (tilted) {
                var q = new Quaterniond().rotationXYZ(Math.toRadians(37), 0, Math.toRadians(23));
                owner.logicalPose().orientation().set(q);
                handle.teleport(owner.logicalPose().position(), q);
                owner.updateLastPose(); owner.updateBoundingBox();
            }
            var observer = new Observer(helper, level, owner, handle, tilted);
            observer.start();
        });
    }

    private static final class Observer {
        final GameTestHelper helper; final ServerLevel level; final ServerSubLevel owner;
        final RigidBodyHandle handle; final boolean tilted;
        final List<Sample> samples = new ArrayList<>();
        Consumer<ForgeSablePrePhysicsTickEvent> listener;
        boolean closed, seeded; int callbacks;
        org.joml.Matrix3d inertia; double mass, drag;
        Vector3d principal; Vector3d previousOmega; Quaterniond previousQ;
        record Sample(Quaterniond q, Vector3d w, double dt, Vector3d full, Vector3d[] diag) { }
        Observer(GameTestHelper helper, ServerLevel level, ServerSubLevel owner, RigidBodyHandle handle, boolean tilted) {
            this.helper=helper; this.level=level; this.owner=owner; this.handle=handle; this.tilted=tilted;
            helper.testInfo.addListener(new GameTestListener() {
                private void cleanup(){close();}
                public void testStructureLoaded(GameTestInfo i){}
                public void testPassed(GameTestInfo i, GameTestRunner r){cleanup();}
                public void testFailed(GameTestInfo i, GameTestRunner r){cleanup();}
                public void testAddedForRerun(GameTestInfo a, GameTestInfo b, GameTestRunner r){cleanup();}
            });
        }
        void start(){ listener=this::onPre; NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, listener); }
        void close(){ if(closed)return; closed=true; if(listener!=null)NeoForge.EVENT_BUS.unregister(listener); }
        boolean probeInstalledInertia(Quaterniondc q){
            checkClearance();
            Vector3d[] axes={new Vector3d(1,0,0),new Vector3d(0,1,0),new Vector3d(0,0,1)};
            boolean ready=true;
            for(Vector3d axis:axes){
                Vector3d before=handle.getAngularVelocity(new Vector3d());
                Vector3d j=inertia.transform(new Vector3d(axis).mul(.1));
                Vector3d after=null;
                try{
                    handle.applyLinearAndAngularImpulse(new Vector3d(),new Vector3d(j));
                    after=handle.getAngularVelocity(new Vector3d());
                }finally{
                    handle.applyLinearAndAngularImpulse(new Vector3d(),new Vector3d(j).negate());
                }
                Vector3d restored=handle.getAngularVelocity(new Vector3d());
                if(restored.distance(before)>1e-5)fail("readiness counterimpulse failed axis="+axis+" before="+before+" restored="+restored+" I="+inertia);
                Vector3d expected=q.transform(new Vector3d(axis).mul(.1),new Vector3d());
                if(after==null||after.sub(before).distance(expected)>1e-5)ready=false;
            }
            if(!ready)com.juicyslew.moonstation14.MoonStation14.LOGGER.debug("native merged-inertia readiness retry callback={} Q={} I={}",callbacks,q,inertia);
            return ready;
        }
        void checkClearance(){
            var box=owner.boundingBox().toMojang().inflate(.2);
            if(!Double.isFinite(box.minY)||!Double.isFinite(box.maxY)||box.minY<MIN_HULL_Y||box.maxY>=SCENE_BASE_Y+SCENE_HEIGHT-1)
                fail("native hull outside bounded vertical clearance reserve; bounds="+box+" permitted="+MIN_HULL_Y+".."+(SCENE_BASE_Y+SCENE_HEIGHT-1));
            int minX=net.minecraft.core.BlockPos.containing(box.minX,box.minY,box.minZ).getX();
            int maxX=net.minecraft.core.BlockPos.containing(box.maxX,box.maxY,box.maxZ).getX();
            int minZ=net.minecraft.core.BlockPos.containing(box.minX,box.minY,box.minZ).getZ();
            int maxZ=net.minecraft.core.BlockPos.containing(box.maxX,box.maxY,box.maxZ).getZ();
            for(int cx=minX>>4;cx<=maxX>>4;cx++)for(int cz=minZ>>4;cz<=maxZ>>4;cz++)
                if(level.getChunkSource().getChunk(cx,cz,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false)==null)
                    fail("native hull clearance unknown: unloaded world chunk "+cx+','+cz);
            if(level.getBlockCollisions(null,box).iterator().hasNext())fail("native hull not contact-free within .2m world margin; bounds="+box);
        }
        // Sable ad8 SubLevelPhysicsSystem posts PRE with dt=1/(20*6), then runs pipeline.physicsTick(dt).
        // Rapier 38e92 angvel_with_gyroscopic_forces supplies the frame predictor; damping follows once per base dt.
        void onPre(ForgeSablePrePhysicsTickEvent e) {
            if(closed || e.getPhysicsSystem().getLevel()!=level)return;
            if(owner.isRemoved() || owner.getLevel()!=level || dev.ryanhcode.sable.api.sublevel.SubLevelContainer
                    .getContainer(level).getSubLevel(owner.getUniqueId())!=owner){
                terminateFailure(new IllegalStateException("native diagnostic owner removed/replaced")); return;
            }
            try {
                if(++callbacks>60) fail("native mass/inertia readiness or 12-interval probe exceeded 60 callbacks");
                double dt=e.getTimeStep();
                if(!Double.isFinite(dt)||Math.abs(dt-DT)>1e-10) fail("expected six-substep dt="+DT+" got="+dt);
                if(!seeded){
                    var tracker=owner.getMassTracker();
                    if(tracker==null||!Double.isFinite(tracker.getMass())||tracker.getMass()<=0)return;
                    mass=tracker.getMass(); inertia=new Matrix3d(tracker.getInertiaTensor());
                    if(!finite(inertia))return;
                    guardLShapeTensor(inertia);
                    principal=principalValues(inertia);
                    var pose=e.getPhysicsSystem().getPipeline().readPose(owner,new Pose3d());
                    var q=pose.orientation();
                    if(tilted && Math.abs(q.dot(new Quaterniond().rotationXYZ(Math.toRadians(37),0,Math.toRadians(23))))<.999999)
                        fail("tilted native Q mismatch="+q);
                    if(!handle.isValid()) fail("native body invalid before seed/readiness probe");
                    if(!probeInstalledInertia(q))return;
                    pose=e.getPhysicsSystem().getPipeline().readPose(owner,new Pose3d()); q=pose.orientation();
                    var before=handle.getAngularVelocity(new Vector3d());
                    checkClearance();
                    var impulse=inertia.transform(new Vector3d(SEED).sub(before).rotate(new Quaterniond(q).conjugate()));
                    handle.applyLinearAndAngularImpulse(new Vector3d(),impulse);
                    var actual=handle.getAngularVelocity(new Vector3d());
                    if(actual.distance(SEED)>1e-5)fail("physical seed impulse response mismatch J="+impulse+" observed="+actual);
                    var cfg=e.getPhysicsSystem().getConfig();
                    if(cfg.substepsPerTick!=6)fail("expected six substeps got="+cfg.substepsPerTick);
                    drag=dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getUniversalDrag(level);
                    if(!Double.isFinite(drag))fail("invalid dimension drag="+drag);
                    // Seed is a real wake=true impulse; next PRE records first completed native interval.
                    seeded=true; previousOmega=actual; previousQ=new Quaterniond(q); return;
                }
                var pose=e.getPhysicsSystem().getPipeline().readPose(owner,new Pose3d());
                var q=new Quaterniond(pose.orientation()); var w=handle.getAngularVelocity(new Vector3d());
                checkClearance();
                var currentI = owner.getMassTracker().getInertiaTensor();
                if (!sameTensor(inertia,currentI) || owner.getMassTracker().getMass()!=mass)
                    fail("merged mass/inertia changed during response mass="+owner.getMassTracker().getMass()+" I="+currentI+" expected="+mass+'/'+inertia);
                var full=predict(previousQ,previousOmega,dt,drag,inertia);
                var diagonal=new Vector3d[6];
                double[] vals={principal.x,principal.y,principal.z}; int[][] p={{0,1,2},{0,2,1},{1,0,2},{1,2,0},{2,0,1},{2,1,0}};
                for(int i=0;i<6;i++)diagonal[i]=predictDiagonal(previousQ,previousOmega,dt,drag,vals[p[i][0]],vals[p[i][1]],vals[p[i][2]]);
                samples.add(new Sample(new Quaterniond(q),new Vector3d(w),dt,full,diagonal));
                previousQ=q; previousOmega=w;
                if(samples.size()==12) finish();
            } catch(RuntimeException failure){ terminateFailure(failure); }
        }
        void finish(){
            double fullSq=0,fullMax=0; double[] sq=new double[6],max=new double[6]; double minSep=Double.POSITIVE_INFINITY;
            for(var s:samples){var err=new Vector3d(s.w).sub(s.full); double n=err.length();fullSq+=n*n;fullMax=Math.max(fullMax,n);
                for(int i=0;i<6;i++){double d=new Vector3d(s.w).sub(s.diag[i]).length();sq[i]+=d*d;max[i]=Math.max(max[i],d);minSep=Math.min(minSep,new Vector3d(s.full).distance(s.diag[i]));}}
            double rms=Math.sqrt(fullSq/12); int winner=-1; double best=Double.POSITIVE_INFINITY;
            StringBuilder wrong=new StringBuilder(); for(int i=0;i<6;i++){double r=Math.sqrt(sq[i]/12);wrong.append(" p").append(i).append(" rms/max=").append(r).append('/').append(max[i]);if(r<best){best=r;winner=i;}}
            double a=inertia.m00(),b=inertia.m11(),c=inertia.m01();
            if(Math.abs(c)<1e-5 || Math.abs(principal.x-principal.y)<1e-5)fail("fixture tensor lacks measured xy coupling/anisotropy I="+inertia);
            boolean separated=true;
            for(int i=0;i<6;i++) separated &= modelSeparation(i)>1e-4;
            double[] wrongRms=new double[6];for(int i=0;i<6;i++)wrongRms[i]=Math.sqrt(sq[i]/12);
            var classification=classifyGyro(rms,wrongRms,minSep,separated&&Double.isFinite(fullMax));
            String verdict=classification.verdict();
            String summary="native gyro verdict="+verdict+" tilted="+tilted+" mass="+mass+" I="+inertia+" eig="+principal
                    +" q="+samples.get(0).q+" spin="+SEED+" dt="+DT+" drag="+drag+" clearanceReserve=20m fullRms/max="+rms+'/'+fullMax
                    +" wrong="+wrong+" minPredictionSeparation="+minSep;
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(summary);
            close(); if(classification.pass())helper.succeed(); else helper.testInfo.fail(new GameTestAssertException(summary));
        }
        void terminateFailure(Throwable cause){
            dispatchTerminalFailure(this::close, helper.testInfo::fail,
                    new GameTestAssertException("native gyro observer terminal failure: "+cause));
        }
        void fail(String text){throw new GameTestAssertException(text);}
        double modelSeparation(int i){double sum=0;for(var s:samples){double d=new Vector3d(s.full).distance(s.diag[i]);sum+=d*d;}return Math.sqrt(sum/12);}
    }
    record GyroClassification(String verdict,boolean pass) { }
    static GyroClassification classifyGyro(double fullRms,double[] wrongRms,double minSeparation,boolean finiteSamples){
        if(!finiteSamples||!Double.isFinite(fullRms)||!Double.isFinite(minSeparation)||wrongRms==null||wrongRms.length!=6
                ||Arrays.stream(wrongRms).anyMatch(r->!Double.isFinite(r))||minSeparation<=1e-4)
            return new GyroClassification("INCONCLUSIVE",false);
        boolean wrongSeparated=Arrays.stream(wrongRms).allMatch(r->r>1e-4);
        double bestWrong=Arrays.stream(wrongRms).min().orElse(Double.NaN);
        if(fullRms<=2e-5&&wrongSeparated)return new GyroClassification("FRAME_CORRECT",true);
        if(bestWrong<=2e-5&&fullRms>1e-4)return new GyroClassification("BACKEND_PRINCIPAL_FRAME_OMISSION",false);
        return new GyroClassification("INCONCLUSIVE",false);
    }
    private static Vector3d predict(Quaterniondc q, Vector3dc worldW,double dt,double drag,Matrix3dc I){
        var w=new Vector3d(worldW).rotate(new Quaterniond(q).conjugate()); var p=I.transform(w,new Vector3d());
        var t=new Vector3d(p).sub(new Vector3d(w).cross(p).mul(dt)); t.normalize(p.length());
        var local=I.invert(new Matrix3d()).transform(t,new Vector3d()).div(1+dt*drag);
        return q.transform(local,new Vector3d());
    }
    private static Vector3d predictDiagonal(Quaterniondc q,Vector3dc worldW,double dt,double drag,double x,double y,double z){
        var w=new Vector3d(worldW).rotate(new Quaterniond(q).conjugate());var p=new Vector3d(x*w.x,y*w.y,z*w.z);
        var t=new Vector3d(p).sub(new Vector3d(w).cross(p).mul(dt));t.normalize(p.length());
        return q.transform(new Vector3d(t.x/x,t.y/y,t.z/z).div(1+dt*drag),new Vector3d());
    }
    /** Independent deterministic oracle: P is an explicit proper 45-degree principal-frame rotation. */
    private static Vector3d principalReference(Quaterniondc q,Vector3dc worldW,double dt,double drag,
                                               Quaterniondc principalRotation,Vector3dc d){
        Quaterniond p=new Quaterniond(principalRotation);
        Vector3d body=new Vector3d(worldW).rotate(new Quaterniond(q).conjugate());
        Vector3d wp=body.rotate(new Quaterniond(p).conjugate());
        Vector3d momentum=new Vector3d(d.x()*wp.x,d.y()*wp.y,d.z()*wp.z);
        Vector3d corrected=new Vector3d(momentum).sub(new Vector3d(wp).cross(momentum).mul(dt));
        corrected.normalize(momentum.length());
        Vector3d nextP=new Vector3d(corrected.x/d.x(),corrected.y/d.y(),corrected.z/d.z()).div(1+dt*drag);
        return q.transform(p.transform(nextP,new Vector3d()),new Vector3d());
    }
    static void modelSelfCheck(){
        Matrix3d i=new Matrix3d(3,1,0, 1,3,0, 0,0,6);
        Vector3d eig=principalValues(i);
        require(eig.distance(new Vector3d(2,4,6))<1e-12,"analytic 2x2 eigenvalue self-check: "+eig);
        Quaterniond p=new Quaterniond().rotationZ(Math.PI/4);
        Vector3d d=new Vector3d(2,4,6);
        Matrix3d composed=new Matrix3d().rotation(p).scale(d.x,d.y,d.z).rotate(new Quaterniond(p).conjugate());
        // Verify the explicit principal-frame oracle against matrix-space predictor over twelve states;
        // aggregate all six fixed diagonal permutations over the entire sample set.
        double[] residual=new double[6]; int[][] perm={{0,1,2},{0,2,1},{1,0,2},{1,2,0},{2,0,1},{2,1,0}};
        double[] v={d.x,d.y,d.z}; Quaterniond q=new Quaterniond().rotationXYZ(.31,-.42,.19);
        Set<Integer> pointWinners=new HashSet<>();
        for(int n=0;n<12;n++){
            Vector3d w=new Vector3d(.7+.13*n,2.4-.07*n,.5+.09*n);
            Vector3d reference=principalReference(q,w,DT,.18,p,d);
            Vector3d matrix=predict(q,w,DT,.18,composed);
            require(reference.distance(matrix)<1e-12,"full tensor/principal frame independent oracle mismatch");
            double pointBest=Double.POSITIVE_INFINITY; int pointWinner=-1;
            for(int k=0;k<6;k++){
                Vector3d candidate=predictDiagonal(q,w,DT,.18,v[perm[k][0]],v[perm[k][1]],v[perm[k][2]]);
                double error=candidate.distance(reference); require(Double.isFinite(error),"permutation prediction finite");
                residual[k]+=error*error;
                if(error<pointBest){pointBest=error;pointWinner=k;}
            }
            pointWinners.add(pointWinner);
        }
        require(Arrays.stream(residual).allMatch(Double::isFinite),"fixed-permutation aggregate residuals finite");
        int aggregateWinner=0;for(int k=1;k<6;k++)if(residual[k]<residual[aggregateWinner])aggregateWinner=k;
        require(aggregateWinner==2 && pointWinners.size()>1,"whole-series fixed winner differs from varying per-step winners; aggregate="+aggregateWinner+" perStep="+pointWinners+" residual="+Arrays.toString(residual));
    }
    static boolean validSceneBounds(int levelMin,int levelMaxExclusive,int sceneMin,int sceneMaxInclusive){
        return sceneMin>=levelMin && sceneMaxInclusive<levelMaxExclusive
                && sceneMin<=MIN_HULL_Y && SPAWN_Y<sceneMaxInclusive
                && MIN_HULL_Y>=SCENE_BASE_Y+20 && SPAWN_Y-MIN_HULL_Y>=20;
    }
    static void dispatchTerminalFailure(Runnable closeObserver, Consumer<Throwable> failureSink, Throwable cause){
        closeObserver.run();
        failureSink.accept(cause);
    }
    static Vector3d principalValues(Matrix3dc I){
        double a=I.m00(),b=I.m11(),c=I.m01(),d=I.m22(),mid=(a+b)/2,spread=Math.hypot((a-b)/2,c);
        var v=new Vector3d(mid-spread,mid+spread,d);
        if(!finite(v)||v.x<=0||v.y<=0||v.z<=0)throw new GameTestAssertException("invalid measured tensor eigenvalues="+v+" I="+I);
        return v;
    }
    static void guardLShapeTensor(Matrix3dc m){
        require(finite(m),"actual merged tensor finite symmetric: "+m);
        double scale=Math.max(Math.max(Math.abs(m.m00()),Math.abs(m.m11())),Math.abs(m.m22()));
        require(scale>0&&m.m00()>0&&m.m00()*m.m11()-m.m01()*m.m10()>0&&m.determinant()>0,
                "actual merged tensor positive definite: "+m);
        // L-shape guarantees XZ/YZ decoupling; unequal positive XX/YY remains valid for the general 2x2 oracle.
        double fp=128*Math.ulp(scale);
        require(Math.abs(m.m02())<=fp&&Math.abs(m.m12())<=fp,
                "actual merged tensor violates authored L-shape XZ/YZ decoupling: "+tensorDetails(m)+" roundoff="+fp);
        require(Math.abs(m.m01())>scale*1e-4,"authored L-shape tensor lacks meaningful measured Ixy: "+m);
    }
    private static boolean finite(Matrix3dc m){return Double.isFinite(m.m00())&&Double.isFinite(m.m01())&&Double.isFinite(m.m02())&&Double.isFinite(m.m10())&&Double.isFinite(m.m11())&&Double.isFinite(m.m12())&&Double.isFinite(m.m20())&&Double.isFinite(m.m21())&&Double.isFinite(m.m22())&&Math.abs(m.m01()-m.m10())<=128*Math.ulp(Math.max(1,Math.abs(m.m01())))&&Math.abs(m.m02()-m.m20())<=128*Math.ulp(Math.max(1,Math.abs(m.m02())))&&Math.abs(m.m12()-m.m21())<=128*Math.ulp(Math.max(1,Math.abs(m.m12())));}
    private static String tensorDetails(Matrix3dc m){return "["+Double.toString(m.m00())+','+Double.toString(m.m01())+','+Double.toString(m.m02())+';'+Double.toString(m.m10())+','+Double.toString(m.m11())+','+Double.toString(m.m12())+';'+Double.toString(m.m20())+','+Double.toString(m.m21())+','+Double.toString(m.m22())+"] diagDifference="+Double.toString(m.m00()-m.m11())+" xz="+Double.toString(m.m02())+" yz="+Double.toString(m.m12());}
    private static boolean sameTensor(Matrix3dc a, Matrix3dc b){return b!=null&&a.m00()==b.m00()&&a.m01()==b.m01()&&a.m02()==b.m02()&&a.m10()==b.m10()&&a.m11()==b.m11()&&a.m12()==b.m12()&&a.m20()==b.m20()&&a.m21()==b.m21()&&a.m22()==b.m22();}
    private static boolean finite(Vector3dc v){return Double.isFinite(v.x())&&Double.isFinite(v.y())&&Double.isFinite(v.z());}
    private static void require(boolean ok,String message){if(!ok)throw new GameTestAssertException(message);}
}
