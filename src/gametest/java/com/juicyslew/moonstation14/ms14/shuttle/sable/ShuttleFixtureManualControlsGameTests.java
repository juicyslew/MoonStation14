package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.command.CommandBuiltCabinFixture;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

/** Runtime dispatcher coverage for the native fixture's manual angular controls. */
@GameTestTags({"shuttle-fixture-manual-controls"})
@PrefixGameTestTemplate(false)
public final class ShuttleFixtureManualControlsGameTests {
    private ShuttleFixtureManualControlsGameTests() { }

    @GameTestTags({"shuttle-fixture-angular-controls"})
    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=120)
    public static void rollPitchAndLegacyYawApplyNativeAngularVelocityDeltas(GameTestHelper helper) {
        var level=helper.getLevel();
        var region=SableFixtureScene.acquire(
                helper,new BlockPos(2700,300,2500),32,12,24);
        SableFixtureScene.awaitEmpty(helper,region,40,()->{
            SableFixtureScene.authorStoneSupportPad(level,region);
            var fixture=CommandBuiltCabinFixture.build(helper,region.origin.offset(8,0,8));
            var sub=fixture.subLevel(); var body=RigidBodyHandle.of(sub);
            require(body!=null&&body.isValid(),"fixture has a live native body");
            var dispatcher=level.getServer().getCommands().getDispatcher();
            var source=level.getServer().createCommandSourceStack().withLevel(level).withPermission(2);
            String id=sub.getUniqueId().toString();
            require(dispatcherExec(dispatcher,"ms14 shuttle_fixture nudge "+id+" 0 12 0",source)==1,
                    "lifts cabin clear of pad using native physical impulse");
            var initialPose=new dev.ryanhcode.sable.companion.math.Pose3d();
            dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level).getPipeline().readPose(sub,initialPose);
            double initialHeight=initialPose.position().y();
            require(dispatcherExec(dispatcher,"ms14 shuttle_fixture roll "+id+" .8",source)==1,"seeds body-local roll");
            require(dispatcherExec(dispatcher,"ms14 shuttle_fixture pitch "+id+" .6",source)==1,"seeds body-local pitch");
            require(dispatcherExec(dispatcher,"ms14 shuttle_fixture yaw "+id+" .5",source)==1,"seeds world yaw");
            helper.runAfterDelay(20,()->{
                var pose=new dev.ryanhcode.sable.companion.math.Pose3d();
                dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level).getPipeline().readPose(sub,pose);
                var q=pose.orientation().get(new org.joml.Quaterniond());
                var axisZ=new Vector3d(0,0,1).rotate(q); var axisX=new Vector3d(1,0,0).rotate(q);
                var position=pose.position().get(new Vector3d());
                System.out.println("[manual angular native oracle] position="+position+" initialHeight="+initialHeight
                        +" orientation="+q+" velocity="+body.getLinearVelocity(new Vector3d())
                        +" angularVelocity="+body.getAngularVelocity(new Vector3d())+" mass="+sub.getMassTracker().getMass()
                        +" axisX="+axisX+" axisZ="+axisZ);
                require(position.y>initialHeight+2,"native cabin rose clear of pad after physical nudge");
                require(Math.abs(axisZ.x)+Math.abs(axisZ.y)>.12&&Math.abs(axisX.y)+Math.abs(axisX.z)>.12,
                        "native pose is meaningfully rotated before local-axis oracle");
                checkDelta(dispatcher,source,body,"roll",id,new Vector3d(0,0,0.7).rotate(q),0.7);
                checkDelta(dispatcher,source,body,"pitch",id,new Vector3d(0.6,0,0).rotate(q),0.6);
                checkDelta(dispatcher,source,body,"yaw",id,new Vector3d(0,0.5,0),0.5);
                fixture.close(); helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=160)
    public static void brakeCounteractsNativeMotionAtPostPhysicsWithoutChangingPose(GameTestHelper helper) {
        var level=helper.getLevel();
        var region=SableFixtureScene.acquire(
                helper,new BlockPos(2800,300,2500),32,12,24);
        SableFixtureScene.awaitEmpty(helper,region,40,()->{
            SableFixtureScene.authorStoneSupportPad(level,region);
            var fixture=CommandBuiltCabinFixture.build(helper,region.origin.offset(8,0,8));
            var sub=fixture.subLevel(); var body=RigidBodyHandle.of(sub);
            var dispatcher=level.getServer().getCommands().getDispatcher();
            var source=level.getServer().createCommandSourceStack().withLevel(level).withPermission(2);
            String id=sub.getUniqueId().toString();
            require(dispatcherExec(dispatcher,"ms14 shuttle_fixture motion "+id+" 0 0 0 .3 10",source)==1,
                    "starts native feedback controller");
            require(dispatcherExec(dispatcher,"ms14 shuttle_fixture nudge "+id+" 0 12 0",source)==1,
                    "lifts native fixture using physical impulse");
            require(dispatcherExec(dispatcher,"ms14 shuttle_fixture roll "+id+" .8",source)==1,
                    "adds real roll while controller is active");
            helper.runAfterDelay(20,()->{
                var before=ShuttleFixtureMotionService.snapshot(level,sub);
                require(before!=null&&before.active(),"feedback controller active before brake");
                require(dispatcherExec(dispatcher,"ms14 shuttle_fixture brake "+id,source)==1,"dispatcher accepts brake");
                var released=ShuttleFixtureMotionService.snapshot(level,sub);
                require(released!=null&&!released.active(),"brake synchronously releases feedback request");
                final class Pair {
                    java.util.function.Consumer<dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent> high,low;
                    org.joml.Vector3d preV,preW,postV,postW;
                    org.joml.Quaterniond preQ,postQ;
                    boolean closed;
                    void close(){ if(closed)return; closed=true; NeoForge.EVENT_BUS.unregister(high); NeoForge.EVENT_BUS.unregister(low); }
                }
                Pair pair=new Pair();
                pair.high=e->{ if(e.getPhysicsSystem().getLevel()!=level||pair.closed)return;
                    pair.preV=body.getLinearVelocity(new Vector3d()); pair.preW=body.getAngularVelocity(new Vector3d());
                    var p=new dev.ryanhcode.sable.companion.math.Pose3d(); e.getPhysicsSystem().getPipeline().readPose(sub,p);
                    pair.preQ=p.orientation().get(new org.joml.Quaterniond()); };
                pair.low=e->{ if(e.getPhysicsSystem().getLevel()!=level||pair.closed||pair.preV==null)return;
                    pair.postV=body.getLinearVelocity(new Vector3d()); pair.postW=body.getAngularVelocity(new Vector3d());
                    var p=new dev.ryanhcode.sable.companion.math.Pose3d(); e.getPhysicsSystem().getPipeline().readPose(sub,p);
                    pair.postQ=p.orientation().get(new org.joml.Quaterniond()); pair.close();
                    helper.runAfterDelay(1,()->{
                        double tilt=Math.acos(Math.max(-1,Math.min(1,new Vector3d(0,1,0).rotate(pair.preQ).y)));
                        require(tilt>.1,"brake starts from a genuinely tilted native pose; tilt="+tilt);
                        require(pair.preV.length()+pair.preW.length()>.05,"brake POST observer sees meaningful native velocity before service callback");
                        require(pair.postV.length()<.08&&pair.postW.length()<.08,"counter-impulse cancels native linear/angular velocity within POST callback");
                        require(Math.abs(pair.preQ.dot(pair.postQ))>.999999,"brake callback does not change pose orientation within POST phase");
                        var after=ShuttleFixtureMotionService.snapshot(level,sub);
                        require(after!=null&&!after.active(),"feedback remains released after one-shot brake");
                        fixture.close(); helper.succeed();
                    });
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,pair.high);
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,pair.low);
                helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
                    private void cleanup(){pair.close();}
                    public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo ignored){}
                    public void testPassed(net.minecraft.gametest.framework.GameTestInfo ignored,net.minecraft.gametest.framework.GameTestRunner runner){cleanup();}
                    public void testFailed(net.minecraft.gametest.framework.GameTestInfo ignored,net.minecraft.gametest.framework.GameTestRunner runner){cleanup();}
                    public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo old,net.minecraft.gametest.framework.GameTestInfo next,net.minecraft.gametest.framework.GameTestRunner runner){cleanup();}
                });
            });
        });
    }

    private static int dispatcherExec(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> d,String c,
            net.minecraft.commands.CommandSourceStack s){try{return d.execute(c,s);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new GameTestAssertException("command failed: "+e.getMessage());}}

    private static void checkDelta(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
            net.minecraft.commands.CommandSourceStack source,RigidBodyHandle body,String command,String id,Vector3d expected,double amount) {
        Vector3d before=body.getAngularVelocity(new Vector3d());
        try { require(dispatcher.execute("ms14 shuttle_fixture "+command+" "+id+" "+amount,source)==1,
                command+" dispatcher command accepted"); }
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new GameTestAssertException("command failed: "+e.getMessage()); }
        Vector3d after=body.getAngularVelocity(new Vector3d()).sub(before);
        require(after.distance(expected)<0.04,command+" applies expected native angular velocity delta; expected="+expected+", actual="+after);
    }
    private static void require(boolean ok,String why){if(!ok)throw new GameTestAssertException(why);}
}
