package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.command.CommandBuiltCabinFixture;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.CommandSource;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

/** Exercises the registered native fixture through the actual operator dispatcher. */
@GameTestTags({"shuttle-admin-motion", "shuttle"})
@PrefixGameTestTemplate(false)
public final class ShuttleAdminCommandMotionGameTests {
    private ShuttleAdminCommandMotionGameTests() { }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=480)
    public static void actualCommandsLiftTranslateAndTurnNativeCabin(GameTestHelper helper) {
        var level=helper.getLevel();
        var region=SableFixtureScene.acquire(helper,new BlockPos(1960,300,1660),24,12,24);
        SableFixtureScene.awaitEmpty(helper,region,40,()->{
            SableFixtureScene.authorStoneSupportPad(level,region);
            BlockPos origin=region.origin.offset(8,0,8);
            var fixture=CommandBuiltCabinFixture.build(helper,origin);
            var sub=fixture.subLevel();
            var handle=RigidBodyHandle.of(sub);
            require(handle!=null&&handle.isValid(),"command-authored fixture owns ready native body");
            awaitMass(helper,level,sub,handle,100,()->{
                var initialPosition=sub.logicalPose().position().get(new Vector3d());
                var initialY=initialPosition.y;
                var initialYaw=sub.logicalPose().orientation().getEulerAnglesXYZ(new Vector3d()).y;
                String id=sub.getUniqueId().toString();
                var dispatcher=level.getServer().getCommands().getDispatcher();
                var source=level.getServer().createCommandSourceStack().withLevel(level).withPermission(2);
                require(exec(dispatcher,"ms14 shuttle_fixture motion "+id+" .5 0 0 0 8",source)==1,
                        "real motion command accepts grounded world-X target");
                var groundStart=ShuttleFixtureMotionService.snapshot(level,sub);
                require(groundStart!=null&&groundStart.active()&&groundStart.sdkSubsteps()==6
                                &&groundStart.configuredGravity().y()<0,
                        "grounded command uses registered native controller, normal cadence, and gravity");
                helper.runAfterDelay(40,()->{
                    var groundedPosition=sub.logicalPose().position().get(new Vector3d());
                    var moving=ShuttleFixtureMotionService.snapshot(level,sub);
                    double deltaX=groundedPosition.x-initialPosition.x;
                    double deltaY=groundedPosition.y-initialPosition.y;
                    require(moving!=null&&moving.active()&&moving.remainingTicks()>0
                                    &&deltaX>.35&&Math.abs(deltaY)<.25&&moving.worldVelocity().x()>.25&&moving.sdkSubsteps()==6,
                            "grounded command delayed native observation; start="+initialPosition+", current="+groundedPosition
                                    +", deltaX="+deltaX+", deltaY="+deltaY+", nativeVelocity="
                                    +(moving==null?null:moving.worldVelocity())+", request="+(moving==null?null:moving.request())
                                    +", callbacks="+(moving==null?-1:moving.appliedSubsteps())+", remaining="+(moving==null?0:moving.remainingTicks()));
                    require(sub.getUniqueId().equals(fixture.subLevel().getUniqueId()),"grounded motion retains exact command-built owner");
                    continueFlight(helper,level,fixture,sub,initialPosition,initialY,initialYaw,id,dispatcher,source);
                });
            });
        });
    }

    private static void continueFlight(GameTestHelper helper,net.minecraft.server.level.ServerLevel level,
            CommandBuiltCabinFixture fixture,dev.ryanhcode.sable.sublevel.ServerSubLevel sub,
            Vector3d initialPosition,double initialY,double initialYaw,String id,
            com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
            net.minecraft.commands.CommandSourceStack source){
                require(exec(dispatcher,"ms14 shuttle_fixture motion "+id+" 0 .75 0 0 12",source)==1,"real motion command accepts lift target");
                var snap=ShuttleFixtureMotionService.snapshot(level,sub);
                require(snap!=null&&snap.active()&&snap.configuredGravity().y() < 0,"controller snapshot confirms active target and gravity");
                 require(snap.sdkSubsteps()==6,"command-built fixture uses the normal six-substep SDK cadence");
                 var denied=level.getServer().createCommandSourceStack().withLevel(level).withPermission(0);
                 require(exec(dispatcher,"ms14 shuttle_fixture motion "+id+" 100 -25 50 -2 8",source)==1,
                         "actual permission-2 command accepts high finite world velocity and negative yaw targets");
                 var highFinite=ShuttleFixtureMotionService.snapshot(level,sub);
                 require(highFinite!=null&&highFinite.active()&&highFinite.request().targetX()==100
                                 &&highFinite.request().targetY()==-25&&highFinite.request().targetZ()==50
                                 &&highFinite.request().targetYawRate()==-2,
                         "registered native fixture records exact finite command target without arbitrary caps");
                 require(exec(dispatcher,"ms14 shuttle_fixture motion "+id+" 0 .75 0 0 12",source)==1,
                         "retargets high finite diagnostic input to safe nominal lift before flight checks");
                 var stableRequest=snap.request();
                 var safe=ShuttleFixtureMotionService.snapshot(level,sub);
                 stableRequest=safe.request();
                 long stableRemaining=safe.remainingTicks();
                 requireInvalid(dispatcher,"ms14 shuttle_fixture motion "+id+" 0 1 0 15",source,
                         "omitted duration form is syntax-rejected before motion handler");
                 var afterOmittedDuration=ShuttleFixtureMotionService.snapshot(level,sub);
                 require(afterOmittedDuration!=null&&afterOmittedDuration.request().equals(stableRequest)
                                 &&afterOmittedDuration.remainingTicks()==stableRemaining,
                         "omitted duration syntax leaves active request and deadline unchanged");
                 require(exec(dispatcher,"ms14 shuttle_fixture motion "+id+" 0 1 0 0 15",source)==1,
                         "explicit lift target and 15-second duration accepted");
                 var explicitLift=ShuttleFixtureMotionService.snapshot(level,sub);
                 require(explicitLift!=null&&explicitLift.request().targetY()==1
                                 &&explicitLift.request().targetYawRate()==0&&explicitLift.request().durationTicks()==300,
                         "explicit lift records vertical target, zero yaw, and 300-tick duration");
                 stableRequest=explicitLift.request();
                 stableRemaining=explicitLift.remainingTicks();
                 boolean permissionDenied=false;
                try { dispatcher.execute("ms14 shuttle_fixture motion "+id+" 0 0 0 0 5",denied); }
                catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) { permissionDenied=true; }
                require(permissionDenied,"permission-zero motion command is refused by dispatcher requirement");
                 var afterDenied=ShuttleFixtureMotionService.snapshot(level,sub);
                require(afterDenied!=null&&afterDenied.request().equals(stableRequest)
                                &&afterDenied.remainingTicks()==stableRemaining,
                        "denied motion leaves active target and deadline unchanged");
                var beforeInvalidPosition=sub.logicalPose().position().get(new Vector3d());
                 requireInvalid(dispatcher,"ms14 shuttle_fixture motion "+id+" NaN 0 0 0 5",source,
                         "non-finite NaN command target is refused");
                 requireInvalid(dispatcher,"ms14 shuttle_fixture motion "+id+" Infinity 0 0 0 5",source,
                         "non-finite infinite command target is refused");
                 requireInvalid(dispatcher,"ms14 shuttle_fixture motion "+id+" 0 0 0 0 0",source,
                         "out-of-range duration command is refused");
                var afterInvalidPosition=sub.logicalPose().position().get(new Vector3d());
                var afterInvalid=ShuttleFixtureMotionService.snapshot(level,sub);
                require(afterInvalid!=null&&afterInvalid.request().equals(stableRequest)
                                &&afterInvalid.remainingTicks()==stableRemaining
                                &&afterInvalidPosition.distance(beforeInvalidPosition)<1e-9,
                        "invalid retarget preserves active target, deadline, and pose synchronously");
                Output out=new Output();
                require(exec(dispatcher,"ms14 shuttle_fixture status "+id,source.withSource(out))==1
                        &&out.text!=null&&out.text.contains("worldVelocity="),"status reports measured native velocity");
                helper.runAfterDelay(100,()->{
                    require(sub.logicalPose().position().y()>initialY+1,"command physically lifts cabin above pad");
                    var raised=sub.logicalPose().position().get(new Vector3d());
                    require(exec(dispatcher,"ms14 shuttle_fixture motion "+id+" 1 0 0 .05 8",source)==1,"command retargets translation and yaw");
                    helper.runAfterDelay(100,()->{
                        var pose=sub.logicalPose();
                        var position=pose.position().get(new Vector3d());
                        var yaw=pose.orientation().getEulerAnglesXYZ(new Vector3d()).y;
                        require(position.x-raised.x>2,"native projected cabin translates positively in world X");
                        require(Math.abs(position.z-raised.z)<1,"world-X command has bounded world-Z drift");
                        require(yaw-initialYaw>.1,"native cabin orientation changes positively under commanded yaw");
                        require(Math.abs(position.y-raised.y)<1,"cabin altitude remains bounded during translation/turning");
                        var measured=ShuttleFixtureMotionService.snapshot(level,sub);
                        require(measured!=null&&measured.worldVelocity().x()>.7&&measured.worldVelocity().x()<1.2
                                        &&measured.worldAngularVelocity().y()>.03&&measured.worldAngularVelocity().y()<.07,
                                "native measured world velocities track commanded translation and yaw within fixture bounds");
                        require(exec(dispatcher,"ms14 shuttle_fixture motion_stop "+id,source)==1,"motion_stop disables controller forces");
                        fixture.close();
                        helper.succeed();
                    });
                });
    }

    private static void awaitMass(GameTestHelper h,net.minecraft.server.level.ServerLevel level,
            dev.ryanhcode.sable.sublevel.ServerSubLevel sub,RigidBodyHandle body,int left,Runnable ready){
        var s=ShuttleFixtureMotionService.snapshot(level,sub);
        if(s!=null&&s.mergedMass()>0){ready.run();return;}
        if(left<=0)throw new GameTestAssertException("native fixture mass failed bounded warmup");
        h.runAfterDelay(1,()->awaitMass(h,level,sub,body,left-1,ready));
    }
    private static int exec(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>d,String c,net.minecraft.commands.CommandSourceStack s){
        try{return d.execute(c,s);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new GameTestAssertException("command failed: "+e.getMessage());}
    }
    private static void requireInvalid(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>d,
            String command,net.minecraft.commands.CommandSourceStack source,String reason){
        boolean rejected=false;
        try { d.execute(command,source); }
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){ rejected=true; }
        require(rejected,reason);
    }
    private static void require(boolean ok,String why){if(!ok)throw new GameTestAssertException(why);}
    private static final class Output implements CommandSource { String text; public void sendSystemMessage(Component c){text=c.getString();}
        public boolean acceptsSuccess(){return true;} public boolean acceptsFailure(){return true;} public boolean shouldInformAdmins(){return false;} }
}
