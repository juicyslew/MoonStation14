package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Owner-feedback reproduction: observe the real authored hull against a disposable loaded stone pad. */
@PrefixGameTestTemplate(false)
public final class HullTerrainImpactGameTests {
    private static final int SUPPORT_SURFACE_Y = -47;
    private static final int PAD = 64;
    private static final int SUPPORT_LAYERS = 4;
    private static final int[] SPEEDS = {20, 40, 80};
    private enum PlacementMode { FURNISHED_DIRECT, SHELL_DIRECT, SHELL_STOCK }
    private HullTerrainImpactGameTests() { }

    @GameTestTags({"shuttle-hull-impact-reproduction", "shuttle", "sable"})
    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=1200)
    public static void twentyMetresPerSecondControl(GameTestHelper helper) { reproduce(helper, 20, 3600); }

    @GameTestTags({"shuttle-hull-impact-reproduction", "shuttle-hull-impact-40", "shuttle", "sable"})
    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=1200)
    public static void fortyMetresPerSecondUpwardNudge(GameTestHelper helper) { reproduce(helper, 40, 3800); }

    @GameTestTags({"shuttle-hull-impact-reproduction", "shuttle", "sable"})
    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=1200)
    public static void eightyMetresPerSecondUpwardNudge(GameTestHelper helper) { reproduce(helper, 80, 4000); }

    @GameTestTags({"shuttle-hull-impact-reproduction", "shuttle-hull-shell-placement-control", "shuttle", "sable"})
    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=1200)
    public static void shellDirectEightyMetresPerSecondControl(GameTestHelper helper) { reproduce(helper,80,4200,PlacementMode.SHELL_DIRECT); }

    @GameTestTags({"shuttle-hull-impact-reproduction", "shuttle-hull-shell-placement-control", "shuttle", "sable"})
    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=1200)
    public static void shellStockAssemblyEightyMetresPerSecondControl(GameTestHelper helper) { reproduce(helper,80,4400,PlacementMode.SHELL_STOCK); }

    /** Furnished 20 m/s reproduction with narrowly scoped setup/sample hooks for occupied prediction proofs. */
    static void furnishedTwentyMetresPerSecond(GameTestHelper helper, int x,
                                                BiConsumer<SableFixturePlot, Runnable> beforeLaunch,
                                                Consumer<HullTerrainImpactProbe.Observation> sampleObserver,
                                                Runnable beforeSuccess) {
        reproduce(helper,20,x,PlacementMode.FURNISHED_DIRECT,beforeLaunch,sampleObserver,beforeSuccess);
    }

    private static void reproduce(GameTestHelper helper, int launchSpeed, int x) {
        reproduce(helper,launchSpeed,x,PlacementMode.FURNISHED_DIRECT);
    }

    private static void reproduce(GameTestHelper helper, int launchSpeed, int x, PlacementMode mode) {
        reproduce(helper,launchSpeed,x,mode,null,null);
    }

    private static void reproduce(GameTestHelper helper, int launchSpeed, int x, PlacementMode mode,
                                  BiConsumer<SableFixturePlot, Runnable> beforeLaunch,
                                  Consumer<HullTerrainImpactProbe.Observation> sampleObserver) {
        reproduce(helper,launchSpeed,x,mode,beforeLaunch,sampleObserver,null);
    }

    private static void reproduce(GameTestHelper helper, int launchSpeed, int x, PlacementMode mode,
                                  BiConsumer<SableFixturePlot, Runnable> beforeLaunch,
                                  Consumer<HullTerrainImpactProbe.Observation> sampleObserver,
                                  Runnable beforeSuccess) {
        if (java.util.Arrays.stream(SPEEDS).noneMatch(speed -> speed == launchSpeed))
            throw new IllegalArgumentException("unregistered impact scenario");
        ServerLevel level=helper.getLevel();
        BlockPos floorOrigin=new BlockPos(x,SUPPORT_SURFACE_Y,3600);
        var scene=SableFixtureScene.acquire(helper,floorOrigin,PAD,8,PAD,SUPPORT_LAYERS);
        SableFixtureScene.awaitEmpty(helper,scene,60,()->{
            if(mode!=PlacementMode.SHELL_STOCK)SableFixtureScene.authorStoneSupportPad(level,scene);
            try {
                SableFixturePlot plot;
                ShuttleCabinFixture.Built cabin;
                if(mode==PlacementMode.SHELL_STOCK) {
                    BlockPos physicalOrigin=new BlockPos(x+28,scene.supportSurfaceY(),3600+28);
                    ShuttleCabinFixture.buildHull(level,physicalOrigin);
                    cabin=null;
                    var before=HullTerrainImpactProbe.capturePhysical(level,physicalOrigin,ShuttleCabinFixture.WIDTH,
                            ShuttleCabinFixture.HEIGHT,ShuttleCabinFixture.DEPTH);
                    var blocks=new java.util.ArrayList<BlockPos>();
                    for(int bx=0;bx<ShuttleCabinFixture.WIDTH;bx++)for(int by=0;by<ShuttleCabinFixture.HEIGHT;by++)
                        for(int bz=0;bz<ShuttleCabinFixture.DEPTH;bz++) {
                            BlockPos p=physicalOrigin.offset(bx,by,bz);
                            if(!level.getBlockState(p).isAir())blocks.add(p.immutable());
                        }
                    if(blocks.size()!=before.cells().size())throw new GameTestAssertException("stock assembly stage=SETUP_MISMATCH source selection differs from bounded physical snapshot");
                    BlockPos max=physicalOrigin.offset(ShuttleCabinFixture.WIDTH-1,ShuttleCabinFixture.HEIGHT-1,ShuttleCabinFixture.DEPTH-1);
                    var assembled=SubLevelAssemblyHelper.assembleBlocks(level,physicalOrigin,blocks,
                            new BoundingBox3i(physicalOrigin.getX(),physicalOrigin.getY(),physicalOrigin.getZ(),max.getX(),max.getY(),max.getZ()));
                    BlockPos storage=assembled.getPlot().getCenterBlock().immutable();
                    try {
                        plot=SableFixturePlot.wrapAssembled(level,assembled,storage,ShuttleCabinFixture.WIDTH,
                                ShuttleCabinFixture.HEIGHT,ShuttleCabinFixture.DEPTH);
                    } catch(RuntimeException|Error wrapFailure) {
                        SableFixturePlot.retireReturnedAssembly(level,assembled);
                        throw wrapFailure;
                    }
                    SableFixtureScene.trackPlot(helper,plot);
                    plot.validateAssembledFootprint(storage,ShuttleCabinFixture.WIDTH,
                            ShuttleCabinFixture.HEIGHT,ShuttleCabinFixture.DEPTH);
                    String mismatch=before.mismatch(HullTerrainImpactProbe.capturePhysical(level,storage,
                            ShuttleCabinFixture.WIDTH,ShuttleCabinFixture.HEIGHT,ShuttleCabinFixture.DEPTH));
                    if(mismatch!=null)throw new GameTestAssertException("stock assembly stage=SETUP_MISMATCH physical-input mismatch: "+mismatch);
                    SableFixtureScene.authorStoneSupportPad(level,scene);
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("[hull-impact-placement] path=SHELL_STOCK physicalOnly=true externalOwners=not-authored cells={} sha256={} mass={} com={} pivot={} pose={} storage={} source={} bounds={} noActor=true",
                            before.cells().size(),before.sha256Fingerprint(),assembled.getMassTracker().getMass(),
                            assembled.getMassTracker().getCenterOfMass(),assembled.logicalPose().rotationPoint(),
                            assembled.logicalPose(),storage,physicalOrigin,ShuttleCabinFixture.WIDTH+"x"+ShuttleCabinFixture.HEIGHT+"x"+ShuttleCabinFixture.DEPTH);
                } else {
                    if(mode==PlacementMode.FURNISHED_DIRECT) {
                        var authored=SableFixturePlot.allocateAndAuthor(level,
                            new Vector3d(x+PAD/2d,scene.supportSurfaceY(),3600+PAD/2d),fixturePlot->{
                                BlockPos storage=fixturePlot.storageOrigin();
                                fixturePlot.ensureStorageFootprint(storage,ShuttleCabinFixture.WIDTH,
                                        ShuttleCabinFixture.HEIGHT,ShuttleCabinFixture.DEPTH);
                                return ShuttleCabinFixture.build(level,storage);
                            });
                        plot=authored.plot(); cabin=authored.result();
                    } else {
                        var authored=SableFixturePlot.allocateAndAuthor(level,
                            new Vector3d(x+PAD/2d,scene.supportSurfaceY(),3600+PAD/2d),fixturePlot->{
                                BlockPos storage=fixturePlot.storageOrigin();
                                fixturePlot.ensureStorageFootprint(storage,ShuttleCabinFixture.WIDTH,
                                        ShuttleCabinFixture.HEIGHT,ShuttleCabinFixture.DEPTH);
                                ShuttleCabinFixture.buildHull(level,storage,fixturePlot::setStorageBlock);
                                return Boolean.TRUE;
                            });
                        plot=authored.plot(); cabin=null;
                    }
                }
                if(mode!=PlacementMode.SHELL_STOCK)SableFixtureScene.trackPlot(helper,plot);
                final SableFixturePlot activePlot=plot;
                final ShuttleCabinFixture.Built activeCabin=cabin;
                var readyHandle=RigidBodyHandle.of(plot.subLevel());
                BoundedGameTestWait.await(helper,80,()->"native cabin body registration",readyHandle::isValid,
                         ()->{
                             Runnable stationary=()->SableFixtureScene.awaitStationaryFrame(helper,activePlot,100,
                                       ()->runImpact(helper,level,scene,activePlot,readyHandle,launchSpeed,mode,
                                               beforeLaunch,sampleObserver,beforeSuccess));
                              if(mode!=PlacementMode.FURNISHED_DIRECT) stationary.run();
                             else PowerFixtureReadiness.await(helper,level,500,
                                ()->PowerRuntime.airAlarmMeter(level,(com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity)
                                        level.getBlockEntity(activeCabin.at(ShuttleCabinFixture.MACHINE))).known()
                                        &&PowerRuntime.airAlarmMeter(level,(com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity)
                                        level.getBlockEntity(activeCabin.at(ShuttleCabinFixture.MACHINE))).deliveredWatts()==100,
                                ()->"powered cabin alarm readiness",stationary);
                         });
            } catch(RuntimeException|Error failure) { throw failure; }
        });
    }

    private static void runImpact(GameTestHelper helper,ServerLevel level,SableFixtureScene.Region scene,
                                  SableFixturePlot plot,RigidBodyHandle readyHandle,int launchSpeed,PlacementMode mode) {
        runImpact(helper,level,scene,plot,readyHandle,launchSpeed,mode,null,null);
    }

    private static void runImpact(GameTestHelper helper,ServerLevel level,SableFixtureScene.Region scene,
                                  SableFixturePlot plot,RigidBodyHandle readyHandle,int launchSpeed,PlacementMode mode,
                                  BiConsumer<SableFixturePlot, Runnable> beforeLaunch,
                                  Consumer<HullTerrainImpactProbe.Observation> sampleObserver) {
        runImpact(helper,level,scene,plot,readyHandle,launchSpeed,mode,beforeLaunch,sampleObserver,null);
    }

    private static void runImpact(GameTestHelper helper,ServerLevel level,SableFixtureScene.Region scene,
                                  SableFixturePlot plot,RigidBodyHandle readyHandle,int launchSpeed,PlacementMode mode,
                                  BiConsumer<SableFixturePlot, Runnable> beforeLaunch,
                                  Consumer<HullTerrainImpactProbe.Observation> sampleObserver,
                                  Runnable beforeSuccess) {
                                        try {
                                            SableFixtureScene.requireStoneSupportUnderProjectedFootprint(level,scene,
                                                    plot,plot.storageOrigin(),ShuttleCabinFixture.WIDTH,
                                                    ShuttleCabinFixture.DEPTH);
                                            var probe=new HullTerrainImpactProbe(level,scene,plot,plot.storageOrigin());
                                            int effectiveSubsteps=SubLevelPhysicsSystem.require(level).getConfig().substepsPerTick;
                                            String expectedSubsteps=System.getProperty(
                                                    "moonstation14.hullImpactExpectedSubsteps");
                                            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                                                    "[hull-impact-config] speed={} effectiveSubsteps={} expectedSubsteps={} outerStepSeconds={}",
                                                    launchSpeed,effectiveSubsteps,
                                                    expectedSubsteps==null?"none":expectedSubsteps,
                                                    1.0/20.0/effectiveSubsteps);
                                            try {
                                                HullImpactSubstepsExpectation.requireExpected(effectiveSubsteps,expectedSubsteps);
                                            } catch(IllegalArgumentException|IllegalStateException invalidConfig) {
                                                throw new GameTestAssertException("hull-impact stage=SETUP invalid diagnostic substeps: "
                                                        +invalidConfig.getMessage());
                                            }
                                            if(probe.hullPartCount()<100)throw new GameTestAssertException(
                                                    "expected production cabin's many actual collision-shape pieces, got "+probe.hullPartCount());
                                              var grounded=probe.sample();
                                            if(!HullTerrainGeometry.nearSupportSurface(grounded.hullBounds(),
                                                    scene.supportSurfaceY(),.10,.10)
                                                    ||grounded.linearVelocity().lengthSquared()>.0025
                                                    ||grounded.angularVelocity().lengthSquared()>.0001)
                                                throw failure("pre-launch natural ground support was not established",
                                                        launchSpeed,new Vector3d(),grounded,0);
                                             var mass=plot.subLevel().getMassTracker();
                                             if(mass==null||!Double.isFinite(mass.getMass())||mass.getMass()<=0
                                                     ||mass.getCenterOfMass()==null)
                                                 throw new GameTestAssertException("hull-impact stage=SETUP_MISMATCH invalid public Sable mass/COM");
                                             var physical=HullTerrainImpactProbe.capturePhysical(level,plot.storageOrigin(),
                                                     ShuttleCabinFixture.WIDTH,ShuttleCabinFixture.HEIGHT,ShuttleCabinFixture.DEPTH);
                                             var orientation=plot.subLevel().logicalPose().orientation();
                                             var bounds=grounded.hullBounds();
                                             String normalizedBounds="["+(bounds.minX-scene.origin.getX())+","+(bounds.minY-scene.supportSurfaceY())+","+(bounds.minZ-scene.origin.getZ())+"]->["+(bounds.maxX-scene.origin.getX())+","+(bounds.maxY-scene.supportSurfaceY())+","+(bounds.maxZ-scene.origin.getZ())+"]";
                                             com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                                                     "[hull-impact-placement] path={} physicalOnly={} cells={} sha256={} hullParts={} mass={} com={} pivot={} pose={} orientation={} normalizedProjectedBounds={} nativeLinear={} nativeAngular={} externalOwners={}",
                                                       mode,mode!=PlacementMode.FURNISHED_DIRECT,physical.cells().size(),physical.sha256Fingerprint(),probe.hullPartCount(),mass.getMass(),
                                                       mass.getCenterOfMass(),plot.subLevel().logicalPose().rotationPoint(),
                                                       plot.subLevel().logicalPose(),orientation,normalizedBounds,grounded.linearVelocity(),grounded.angularVelocity(),
                                                       mode==PlacementMode.FURNISHED_DIRECT?"present-not-migrated-by-stock":"not-authored-in-shell");
                                             if(mode!=PlacementMode.FURNISHED_DIRECT
                                                     &&!HullTerrainGeometry.nearIdentityOrientation(orientation))
                                                 throw new GameTestAssertException("hull-impact stage=SETUP_MISMATCH shell placement orientation="
                                                         +orientation+" exceeds near-identity tolerance="
                                                         +HullTerrainGeometry.IDENTITY_ORIENTATION_TOLERANCE
                                                         +" for vector norm and |abs(w)-1|");
                                              Runnable launch=()->{
                                              readyHandle.addLinearAndAngularVelocity(new Vector3d(0,launchSpeed,0),new Vector3d());
                                            Vector3d admitted=readyHandle.getLinearVelocity(new Vector3d());
                                            if(admitted.y<launchSpeed*.9)throw new GameTestAssertException(
                                                    "native launch nudge was not admitted: requested="+launchSpeed+" velocity="+admitted);
                                            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                                                    "[hull-impact-start] stage=TRAJECTORY speed={} frame={} supportSurfaceY={} supportBottomY={} pose={} nativeVelocity={}",
                                                    launchSpeed,plot.engineId(),probe.supportSurfaceY(),probe.supportBottomY(),
                                                    plot.subLevel().logicalPose().position(),admitted);
                                              trace(helper,plot,probe,new HullImpactTraceState(),launchSpeed,admitted,
                                                      0,false,false,false,0,sampleObserver,beforeSuccess);
                                              };
                                              if(beforeLaunch==null)launch.run();
                                              else beforeLaunch.accept(plot,launch);
                                        }catch(RuntimeException|Error failure){throw failure;}
    }

    private static void trace(GameTestHelper helper,SableFixturePlot plot,HullTerrainImpactProbe probe,
                              HullImpactTraceState trace,int speed,Vector3d admitted,int tick,
                              boolean ascended,boolean descending,boolean contacted,int settledTicks) {
        trace(helper,plot,probe,trace,speed,admitted,tick,ascended,descending,contacted,settledTicks,null);
    }

    private static void trace(GameTestHelper helper,SableFixturePlot plot,HullTerrainImpactProbe probe,
                              HullImpactTraceState trace,int speed,Vector3d admitted,int tick,
                              boolean ascended,boolean descending,boolean contacted,int settledTicks,
                              Consumer<HullTerrainImpactProbe.Observation> sampleObserver) {
        trace(helper,plot,probe,trace,speed,admitted,tick,ascended,descending,contacted,settledTicks,
                sampleObserver,null);
    }

    private static void trace(GameTestHelper helper,SableFixturePlot plot,HullTerrainImpactProbe probe,
                              HullImpactTraceState trace,int speed,Vector3d admitted,int tick,
                              boolean ascended,boolean descending,boolean contacted,int settledTicks,
                              Consumer<HullTerrainImpactProbe.Observation> sampleObserver,
                              Runnable beforeSuccess) {
        HullTerrainImpactProbe.Observation row=trace.requiresNativeOnlySample()
                ?probe.sampleNativeOnly():probe.sample();
        boolean hadFailure=trace.failure()!=null;
        trace.observe(tick,row);
        if(sampleObserver!=null)sampleObserver.accept(row);
        if(!hadFailure&&trace.failure()!=null)logFailureEvidence(helper,trace,speed,admitted,probe);
        else if(hadFailure)logPostFailureSample(helper,trace,speed,row,tick);
        ascended|=row.position().y()>probe.supportSurfaceY()+5;
        descending|=ascended&&row.linearVelocity().y()<-.5;
        boolean nearPad=HullTerrainGeometry.nearSupportSurface(row.hullBounds(),probe.supportSurfaceY(),.02,.10);
        contacted|=nearPad;
        boolean stopped=nearPad&&row.linearVelocity().lengthSquared()<.0025
                &&row.angularVelocity().lengthSquared()<.0001;
        if(stopped)settledTicks++;else settledTicks=0;
        if(!hadFailure&&(tick%20==0||row.maxTerrainPenetration()>.02))
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info("[hull-impact-probe] speed={} tick={} pose={} linear={} angular={} penetration={} cell={} mtv={} belowPad={}",
                    speed,tick,row.position(),row.linearVelocity(),row.angularVelocity(),row.maxTerrainPenetration(),
                    row.contactPos(),row.contactMtv(),row.fullyBelowSupportSlab());
        if(trace.failure()!=null&&trace.finishFailureDiagnostics(settledTicks,tick))
            throw latchedFailure(trace,speed,admitted,tick,settledTicks);
        if(settledTicks>=10&&trace.failure()==null) {
            if(!ascended||!descending||!contacted)throw failure("flight did not prove ascent/descent/terrain response",speed,admitted,row,tick);
            if(beforeSuccess!=null)beforeSuccess.run();
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                    "[hull-impact-result] outcome=PASS speed={} tick={} ascended={} descending={} contacted={} settledSamples={} maximumPenetration={}",
                    speed,tick,ascended,descending,contacted,settledTicks,trace.maximumPenetration());
            helper.succeed();return;
        }
        if(tick>=900) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                    "[hull-impact-result] outcome=FAIL reason=trajectory-timeout speed={} tick={} maximumPenetration={}",
                    speed,tick,trace.maximumPenetration());
            throw failure("no natural post-impact settling within 900 samples",speed,admitted,row,tick);
        }
        int nextSettled=settledTicks;
        boolean wasAscended=ascended,wasDescending=descending,wasContacted=contacted;
        helper.runAfterDelay(1,()->trace(helper,plot,probe,trace,speed,admitted,tick+1,wasAscended,
                wasDescending,wasContacted,nextSettled,sampleObserver,beforeSuccess));
    }

    private static void logFailureEvidence(GameTestHelper helper,HullImpactTraceState trace,int speed,
                                           Vector3d admitted,HullTerrainImpactProbe probe) {
        var first=trace.failure();
        com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                "[hull-impact-failure] stage=TRAJECTORY speed={} frame={} admitted={} supportSurfaceY={} supportBottomY={} reason={} witnessTick={} confirmedTick={} maxPenetration={}",
                speed,probe.frameId(),admitted,probe.supportSurfaceY(),probe.supportBottomY(),first.reason(),
                first.firstTick(),first.confirmedTick(),trace.maximumPenetration());
        var mismatch=probe.firstMismatch();
        if(mismatch!=null)com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                "[hull-impact-state-mismatch] speed={} frame={} tick={} position={} authored={} observed={} authoredBoxes={} observedBoxes={} boxesEqual={} shapeContextReadComplete={}",
                speed,probe.frameId(),mismatch.serverTick(),mismatch.position(),mismatch.authoredState(),
                mismatch.observedState(),mismatch.authoredBoxes(),mismatch.observedBoxes(),mismatch.boxesEqual(),
                mismatch.shapeContextReadComplete());
        for(var sample:trace.recentHistory())logObservation("hull-impact-history",speed,sample.tick(),sample.observation());
    }

    private static void logPostFailureSample(GameTestHelper helper,HullImpactTraceState trace,int speed,
                                             HullTerrainImpactProbe.Observation row,int tick) {
        logObservation("hull-impact-postfailure",speed,tick,row);
        if(tick%10==0||trace.finishFailureDiagnostics(0,tick))
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                    "[hull-impact-progress] speed={} tick={} postFailureSamples={} settledSlabsEscaped={} maxPenetration={}",
                    speed,tick,trace.postFailureSamples(),trace.supportSlabEscapeObserved(),trace.maximumPenetration());
    }

    private static void logObservation(String event,int speed,int tick,
                                       HullTerrainImpactProbe.Observation row) {
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                "[{}] serverTick={} speed={} pose={} orientation={} linear={} angular={} hull={} penetration={} cell={} mtv={} coverage={} hullUnchanged={} escapedSlab={}",
                event,row.serverTick(),speed,row.position(),row.orientation(),row.linearVelocity(),row.angularVelocity(),
                row.hullBounds(),row.maxTerrainPenetration(),row.contactPos(),row.contactMtv(),row.sceneComplete(),
                row.hullUnchanged(),row.fullyBelowSupportSlab());
    }

    private static GameTestAssertException latchedFailure(HullImpactTraceState trace,int speed,Vector3d admitted,
                                                          int tick,int settledTicks) {
        var first=trace.failure();
        com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                "[hull-impact-result] outcome=FAIL stage=TRAJECTORY speed={} admitted={} reason={} witnessTick={} confirmedTick={} finalTick={} postFailureSamples={} settledSamples={} escapedSlab={} maxPenetration={}",
                speed,admitted,first.reason(),first.firstTick(),first.confirmedTick(),tick,
                trace.postFailureSamples(),settledTicks,trace.supportSlabEscapeObserved(),trace.maximumPenetration());
        return new GameTestAssertException("hull impact failure speed="+speed+" reason="+first.reason()
                +" witness="+first.firstTick()+" confirmed="+first.confirmedTick()+" final="+tick
                +" post="+trace.postFailureSamples()+" maxDepth="+trace.maximumPenetration());
    }

    private static GameTestAssertException failure(String reason,int speed,Vector3d admitted,
                                                    HullTerrainImpactProbe.Observation row,int tick) {
        return new GameTestAssertException(reason+" speed="+speed+" tick="+tick+" admitted="+admitted
                +" pose="+row.position()+" orientation="+row.orientation()+" linear="+row.linearVelocity()
                +" angular="+row.angularVelocity()+" hull="+row.hullBounds()+" penetration="
                +row.maxTerrainPenetration()+" terrain="+row.contactPos()+" mtv="+row.contactMtv()
                +" complete="+row.sceneComplete()+" hullUnchanged="+row.hullUnchanged());
    }
}
