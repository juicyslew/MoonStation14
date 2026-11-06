package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleSingleWorldStepObserver;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleMotionMath;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.bus.api.EventPriority;
import org.joml.Vector3d;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Real manual cargo cabin, neutral HUMAN, loose cargo, and native bounded freight-profile proof. */
@GameTestTags("shuttle-freight-flight")
@PrefixGameTestTemplate(false)
public final class ShuttleFreightFlightGameTests {
    private static final int FLIGHT_SECONDS=30;
    private ShuttleFreightFlightGameTests() { }

    @GameTest(templateNamespace="moonstation14",template="empty",batch="shuttle_freight_flight",timeoutTicks=1800)
    @GameTestTags("shuttle-freight-flight")
    public static void nativeFreightProfileCompletesThirtySecondsWithRestartReversalCargoAndGroundedHuman(GameTestHelper helper) {
        // Fixed near-origin scene independent of a GameTest template that may be millions of blocks away.
        MovingCabinAuthorityFixture.startManual(helper,new BlockPos(4400,300,600),"FreightFlight",14.5,1.05,6.5,
                (fixture,body)->armAndProbeLegacyThenRun(helper,fixture,body));
    }

    private static void armAndProbeLegacyThenRun(GameTestHelper helper,MovingCabinAuthorityFixture.Ready fixture,
                                                  PlayerCharacterHarnessEntity body) {
        var level=fixture.level(); var frame=fixture.plot().subLevel();
        var authority= HandActorAuthority.resolve(fixture.owner().actor()).orElseThrow();
        var cargoStack=new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.JUG.get());
        var water=com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water");
        if(water==null) throw new GameTestAssertException("resolved water reagent unavailable");
        cargoStack.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(),
                com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(java.util.Map.of(water,500L)));
        LooseItemEntity cargo=com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.spawnAuthorizedGrant(
                fixture.owner().actor(),authority,cargoStack,stage->System.err.println("freight cargo rejected: "+stage));
        if(cargo==null||!net.minecraft.world.item.ItemStack.matches(cargoStack,cargo.getItem()))
            throw new GameTestAssertException("could not publish authorized real reagent cargo jug");
        helper.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo ignored) { }
            @Override public void testPassed(GameTestInfo ignored,GameTestRunner runner) { cargo.discard(); }
            @Override public void testFailed(GameTestInfo ignored,GameTestRunner runner) { cargo.discard(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest,GameTestInfo newTest,GameTestRunner runner) { cargo.discard(); }
        });

        var actorStart=com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(level,body);
        if(actorStart==null||!frame.getUniqueId().equals(actorStart.frameId()))
            throw new GameTestAssertException("neutral HUMAN not admitted to loaded manual freight cabin");
        var physical=new PhysicalState(body,frame,cargo,cargoStack,
                new Vector3d(actorStart.storagePoint().x,actorStart.storagePoint().y,actorStart.storagePoint().z));
        physical.attempts=new AtomicReference<>(List.of());
        physical.overflow=new AtomicBoolean();
        physical.observerScope=LifecycleSingleWorldStepObserver.installWithNativeContactEvidence(
                fixture.owner().actor(),physical.attempts,1000,physical.overflow);
        helper.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo ignored) { }
            @Override public void testPassed(GameTestInfo ignored,GameTestRunner runner) { physical.closeObserver(); }
            @Override public void testFailed(GameTestInfo ignored,GameTestRunner runner) { physical.closeObserver(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest,GameTestInfo newTest,GameTestRunner runner) { physical.closeObserver(); }
        });

        var dispatcher=level.getServer().getCommands().getDispatcher();
        var source=level.getServer().createCommandSourceStack().withLevel(level).withPermission(2);
        observeCompletedNativeSteps(helper,physical);
        BoundedGameTestWait.await(helper,80,()->"real authorized cargo jug did not settle on its frame floor before flight: "+cargoDiagnostic(physical),
                ()->cargoReadyOnFrame(physical),()->{
                    physical.cargoReady=true;
                    // Explicit flight opt-in arms only this selected loaded ship; the legacy pulse publishes the actual prior command.
                    if(dispatch(dispatcher,"ms14 shuttle_fixture flight "+frame.getUniqueId()+" "+FLIGHT_SECONDS,source)!=1)
                        throw new GameTestAssertException("selected-flight command did not arm the loaded ship");
                    if(dispatch(dispatcher,"ms14 shuttle_fixture motion "+frame.getUniqueId()+" .1 0 0 0 1",source)!=1)
                        throw new GameTestAssertException("legacy pulse could not replace the armed flight target");
                    BoundedGameTestWait.await(helper,80,()->"legacy control did not publish an actual prior command",
                ()->{
                    var snap=ShuttleFixtureMotionService.snapshot(level,frame);
                    if(snap==null||!snap.active()||snap.freightFlight()||snap.commandedAcceleration()==null) return false;
                    Vector3d legacy=vector(snap.commandedAcceleration().linearWorld());
                    if(legacy.x<.05||legacy.length()>.350001)
                        throw new GameTestAssertException("legacy pulse did not produce a within-budget applied command: "+legacy);
                    return true;
                },()->{
                    var legacy=ShuttleFixtureMotionService.snapshot(level,frame).commandedAcceleration();
                    if(dispatch(dispatcher,"ms14 shuttle_fixture flight "+frame.getUniqueId()+" "+FLIGHT_SECONDS,source)!=1)
                        throw new GameTestAssertException("freight takeover from legacy target failed");
                    var immediate=ShuttleFixtureMotionService.snapshot(level,frame).commandedAcceleration();
                    if(!legacy.equals(immediate)) throw new GameTestAssertException("accepted profile restart discarded actual prior legacy command");
                    awaitOneSecondAndRestart(helper,physical,dispatcher,source,legacy);
                });
                });
    }

    private static void awaitOneSecondAndRestart(GameTestHelper helper,PhysicalState physical,
            com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
            net.minecraft.commands.CommandSourceStack source,ShuttleMotionMath.CommandAcceleration legacyCommand) {
        BoundedGameTestWait.await(helper,35,()->"first active profile run did not reach one native second",
                ()->{
                    var snap=ShuttleFixtureMotionService.snapshot(physical.frame.getLevel(),physical.frame);
                    if(snap==null||!snap.freightFlight()) return false;
                    assertFreightLimits(snap);
                    if(snap.profileElapsedSeconds()>0) {
                        if(snap.commandedAcceleration()==null
                                ||vector(snap.commandedAcceleration().linearWorld()).distance(vector(legacyCommand.linearWorld()))
                                    >.1201*snap.profileElapsedSeconds()
                                ||vector(snap.commandedAcceleration().angularWorld()).distance(vector(legacyCommand.angularWorld()))
                                    >.0101*snap.profileElapsedSeconds())
                            throw new GameTestAssertException("first native flight command slewed from the actual legacy command too quickly");
                    }
                    return snap.profileElapsedSeconds()>=1.0;
                },()->{
                    var frame=physical.frame;
                    var beforeRestart=ShuttleFixtureMotionService.snapshot(physical.frame.getLevel(),frame).commandedAcceleration();
                    if(beforeRestart==null||!ShuttleFixtureMotionService.status(physical.frame.getLevel(),frame).contains("profile=heavy-freight"))
                        throw new GameTestAssertException("first active profile lost its native command state");
                    if(dispatch(dispatcher,"ms14 shuttle_fixture flight "+frame.getUniqueId()+" "+FLIGHT_SECONDS,source)!=1)
                        throw new GameTestAssertException("active freight-profile restart failed");
                    var afterRestart=ShuttleFixtureMotionService.snapshot(physical.frame.getLevel(),frame).commandedAcceleration();
                    if(!beforeRestart.equals(afterRestart)||legacyCommand==null)
                        throw new GameTestAssertException("active restart did not preserve last actually applied native command");
                    runThirtySecondNativeProfile(helper,physical,dispatcher,source,afterRestart);
                });
    }

    private static void runThirtySecondNativeProfile(GameTestHelper helper,PhysicalState physical,
            com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
            net.minecraft.commands.CommandSourceStack source,ShuttleMotionMath.CommandAcceleration restartBaseline) {
        ServerSubLevel frame=physical.frame; var level=physical.frame.getLevel();
        var startPose=frame.logicalPose(); Vector3d initialCom=startPose.position().get(new Vector3d());
        var initialOrientation=new org.joml.Quaterniond(startPose.orientation());
        final double[] lastProfileSeconds={0},maxYaw={0},maxWorldX={0},maxWorldZ={0};
        final boolean[] positiveY={false},negativeY={false},firstPostRestart={true},deadline={false};
        final long[] activeSamples={0};
        BoundedGameTestWait.await(helper,660,()->"restarted 30-second native freight profile failed to reach deadline",
                ()->{
                    var snap=ShuttleFixtureMotionService.snapshot(level,frame);
                    if(snap==null) throw new GameTestAssertException("selected native controller disappeared");
                    if(!snap.freightFlight()) {
                        if(activeSamples[0]<590||lastProfileSeconds[0]<29.8)
                            throw new GameTestAssertException("restarted flight ended before 30 native seconds: "+lastProfileSeconds[0]);
                        deadline[0]=true; return true;
                    }
                    activeSamples[0]++; lastProfileSeconds[0]=snap.profileElapsedSeconds();
                    assertFreightLimits(snap);
                    if(firstPostRestart[0]&&snap.profileElapsedSeconds()>0) {
                        double elapsed=snap.profileElapsedSeconds();
                        if(vector(snap.commandedAcceleration().linearWorld()).distance(vector(restartBaseline.linearWorld()))>.1201*elapsed
                                ||vector(snap.commandedAcceleration().angularWorld()).distance(vector(restartBaseline.angularWorld()))>.0101*elapsed)
                            throw new GameTestAssertException("first restarted profile command jumped from the prior actual command");
                        firstPostRestart[0]=false;
                    }
                    if(snap.profileElapsedSeconds()>2&&snap.transportVelocity()!=null) {
                        positiveY[0]|=snap.transportVelocity().y()>.05;
                        negativeY[0]|=snap.transportVelocity().y()<-.05;
                    }
                    var pose=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.require(level)
                            .getPipeline().readPose(frame,new dev.ryanhcode.sable.companion.math.Pose3d());
                    var center=pose.position().get(new Vector3d());
                    maxWorldX[0]=Math.max(maxWorldX[0],Math.abs(center.x-initialCom.x));
                    maxWorldZ[0]=Math.max(maxWorldZ[0],Math.abs(center.z-initialCom.z));
                    maxYaw[0]=Math.max(maxYaw[0],new org.joml.Quaterniond(initialOrientation).invert()
                            .mul(pose.orientation()).normalize().angle());
                    return false;
                },()->{
                    if(!deadline[0]||firstPostRestart[0]||!positiveY[0]||!negativeY[0])
                        throw new GameTestAssertException("full native reversal not observed: activeTicks="+activeSamples[0]
                                +" elapsed="+lastProfileSeconds[0]+" firstDeltaChecked="+!firstPostRestart[0]
                                +" positiveY="+positiveY[0]+" negativeY="+negativeY[0]);
                    if(maxYaw[0]<.015||Math.max(maxWorldX[0],maxWorldZ[0])<.15)
                        throw new GameTestAssertException("compound translation/turn not measured: yaw="+maxYaw[0]
                                +" dx="+maxWorldX[0]+" dz="+maxWorldZ[0]);
                    physical.profileFinished=true;
                    physical.finishAfterObserver=()->{
                        var handle=RigidBodyHandle.of(frame); Vector3d before=handle.getLinearVelocity(new Vector3d());
                        if(dispatch(dispatcher,"ms14 shuttle_fixture motion_stop "+frame.getUniqueId(),source)!=1)
                            throw new GameTestAssertException("motion_stop could not release completed profile");
                        Vector3d after=handle.getLinearVelocity(new Vector3d());
                        if(!before.equals(after)) throw new GameTestAssertException("motion_stop changed native velocity: "+before+" -> "+after);
                        physical.closeObserver(); helper.succeed();
                    };
                });
    }

    private static final class PhysicalState {
        final PlayerCharacterHarnessEntity body; final ServerSubLevel frame; final LooseItemEntity cargo;
        final net.minecraft.world.item.ItemStack stack; final Vector3d initialLocalActor;
        AtomicReference<List<LifecycleSingleWorldStepObserver.Attempt>> attempts;
        AtomicBoolean overflow;
        AutoCloseable observerScope;
        boolean profileFinished;
        Runnable finishAfterObserver;
        long completedTicks;
        long lastObservedServerTick=-1;
        LifecycleSingleWorldStepObserver.Attempt lastAttempt;
        boolean cargoReady;
        PhysicalState(PlayerCharacterHarnessEntity body,ServerSubLevel frame,LooseItemEntity cargo,
                      net.minecraft.world.item.ItemStack stack,Vector3d initialLocalActor) {
            this.body=body;this.frame=frame;this.cargo=cargo;this.stack=stack;this.initialLocalActor=initialLocalActor;
        }
        void closeObserver() {
            if(observerScope==null) return;
            try { observerScope.close(); }
            catch(Exception failure) { com.juicyslew.moonstation14.MoonStation14.LOGGER.error("failed closing freight worldstep observer",failure); }
            observerScope=null;
        }
    }

    /** Consume one copied completed lifecycle step at LOWEST post-server-tick, never SDK scratch from a GameTest poll. */
    private static void observeCompletedNativeSteps(GameTestHelper helper,PhysicalState p) {
        BoundedGameTestWait.observeServerTicksUntil(helper,700,EventPriority.LOWEST,event->{
            long tick=event.getServer().getTickCount();
            if(p.overflow.get()) throw new GameTestAssertException("native worldstep observer overflowed its 1000-record bound");
            var matches=LifecycleSingleWorldStepObserver.attemptsAtServerTick(p.attempts.get(),tick);
            if(matches.size()!=1) throw new GameTestAssertException("expected exactly one completed native lifecycle attempt at serverTick="
                    +tick+" observed="+matches.size()+" attempts="+matches.stream().map(LifecycleSingleWorldStepObserver.Attempt::diagnostic).toList());
            var attempt=matches.getFirst(); p.lastAttempt=attempt;
            if(attempt.serverPhysicalTick()!=tick) throw new GameTestAssertException("attempt tick mismatch: event="+tick+" "+attempt.diagnostic());
            var completed=attempt.completedNativeStep();
            if(!completed.supportedGroundedFrame(p.frame.getUniqueId()))
                throw new GameTestAssertException("completed native step lacks fresh exact-frame grounded support: "
                        +attempt.diagnostic()+" proof="+completed.diagnostic());
            var evidence=completed.move();
            if(evidence.afterStoragePoint()==null)
                throw new GameTestAssertException("completed native support proof lacks a copied after-storage point: "+completed.diagnostic());
            Vector3d localActor=new Vector3d(evidence.afterStoragePoint().x,evidence.afterStoragePoint().y,evidence.afterStoragePoint().z);
            if(localActor.distance(p.initialLocalActor)>1.0)
                throw new GameTestAssertException("native HUMAN cabin-local drift exceeds bound: tick="+tick+" local="+localActor
                        +" baseline="+p.initialLocalActor+" proof="+completed.diagnostic());
            if(p.body.isRemoved()||!p.body.isAlive()||p.body.isNoGravity()||p.body.noPhysics||!attempt.groundedAfter())
                throw new GameTestAssertException("completed native attempt lost live normal-gravity grounded HUMAN: "+attempt.diagnostic());
            if(p.cargoReady) requireCargo(p,tick);
            p.completedTicks++;p.lastObservedServerTick=tick;
        },()->p.profileFinished,()->BoundedGameTestWait.safeDelay(helper,1,()->{
            if(p.finishAfterObserver==null) { helper.fail("completed-profile observer stopped before final verification callback"); return; }
            p.finishAfterObserver.run();
        }),()->helper.testInfo.fail(new GameTestAssertException("bounded native-worldstep monitor expired: completedTicks="
                +p.completedTicks+" lastServerTick="+p.lastObservedServerTick+" overflow="+p.overflow.get()
                +" lastAttempt="+(p.lastAttempt==null?"none":p.lastAttempt.diagnostic()))));
    }

    private static void requireCargo(PhysicalState p,long tick) {
        if(p.cargo.isRemoved()||p.frame.getLevel().getEntity(p.cargo.getUUID())!=p.cargo
                ||!net.minecraft.world.item.ItemStack.matches(p.stack,p.cargo.getItem()))
            throw new GameTestAssertException("cargo jug removed or its stack/component changed at serverTick="+tick);
        var cargoPoint=com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                .currentActorPoint(p.frame.getLevel(),p.cargo);
        if(cargoPoint==null||!p.frame.getUniqueId().equals(cargoPoint.frameId()))
            throw new GameTestAssertException("real cargo jug left its native freight frame at serverTick="+tick);
        Vector3d cargoLocal=p.frame.logicalPose().transformPositionInverse(
                new Vector3d(p.cargo.getX(),p.cargo.getBoundingBox().minY,p.cargo.getZ()));
        BlockPos storage=p.frame.getPlot().getCenterBlock();
        if(cargoLocal.x<storage.getX()+8||cargoLocal.x>storage.getX()+20
                ||cargoLocal.z<storage.getZ()||cargoLocal.z>storage.getZ()+13
                ||cargoLocal.y<storage.getY()+.95)
            throw new GameTestAssertException("cargo jug left manual bay bounds or penetrated its floor at serverTick="+tick+": "+cargoLocal);
    }

    private static boolean cargoReadyOnFrame(PhysicalState p) {
        if(p.cargo.isRemoved()||!net.minecraft.world.item.ItemStack.matches(p.stack,p.cargo.getItem()))
            throw new GameTestAssertException("authorized cargo disappeared or changed before flight");
        var point=com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                .currentActorPoint(p.frame.getLevel(),p.cargo);
        if(point==null||!p.frame.getUniqueId().equals(point.frameId())||!p.cargo.onGround()) return false;
        var local=p.frame.logicalPose().transformPositionInverse(
                new Vector3d(p.cargo.getX(),p.cargo.getBoundingBox().minY,p.cargo.getZ()));
        var storage=p.frame.getPlot().getCenterBlock();
        return local.x>=storage.getX()+8&&local.x<=storage.getX()+20
                &&local.z>=storage.getZ()&&local.z<=storage.getZ()+13
                &&local.y>=storage.getY()+.95;
    }

    private static String cargoDiagnostic(PhysicalState p) {
        var point=com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                .currentActorPoint(p.frame.getLevel(),p.cargo);
        Vector3d local=p.frame.logicalPose().transformPositionInverse(
                new Vector3d(p.cargo.getX(),p.cargo.getBoundingBox().minY,p.cargo.getZ()));
        return "entity="+p.cargo+" onGround="+p.cargo.onGround()+" actorPoint="+point+" local="+local;
    }

    private static Vector3d vector(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleMotionMath.Vector v) {
        return new Vector3d(v.x(),v.y(),v.z());
    }
    private static void assertFreightLimits(ShuttleFixtureMotionService.Snapshot snap) {
        if(snap.commandedAcceleration()==null||!snap.freightLimitsMaintained()
                ||snap.maxFreightLinearAcceleration()>.350001||snap.maxFreightAngularAcceleration()>.020001
                ||snap.maxFreightLinearJerk()>.120001||snap.maxFreightAngularJerk()>.010001)
            throw new GameTestAssertException("actual native-substep controller limits failed: "+snap);
    }
    private static int dispatch(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
            String command,net.minecraft.commands.CommandSourceStack source) {
        try { return dispatcher.execute(command,source); }
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new GameTestAssertException("command dispatch failed: "+command+": "+failure.getMessage());
        }
    }
}
