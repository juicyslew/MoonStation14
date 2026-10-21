package com.juicyslew.moonstation14.ms14.singularity.fixture;

import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.entities.SingularityEntity;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Live real-entity/operator-fixture coverage; no reactor or containment commissioning. */
@PrefixGameTestTemplate(false)
@GameTestTags({"singularity-gravity", "singularity-fixture", "singularity-prototype"})
public final class SingularityGravityFixtureGameTests {
    private SingularityGravityFixtureGameTests() { }

    @GameTest(templateNamespace="moonstation14",template="pa_contained_clear",batch="singularity_gravity_fixture",timeoutTicks=120)
    public static void gravityFixtureSpawnsResolvedStageTracksAndClearsOnlyOwnedSource(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        BlockPos playerPos=h.absolutePos(new BlockPos(3,4,3));
        var lease=FixtureChunkTickets.acquire(h,new AABB(playerPos).inflate(80));
        RegisteredServerPlayerFixture playerFixture=RegisteredServerPlayerFixture.register(level,"gravity");
        var player=playerFixture.player(); player.moveTo(playerPos.getX()+.5,playerPos.getY(),playerPos.getZ()+.5,0,0);
        player.setYRot(0);
        SingularityEntity unrelated=ModEntities.SINGULARITY.get().create(level);
        if(unrelated==null)throw new GameTestAssertException("unrelated real source created");
        unrelated.setPos(player.position().add(0,0,65));
        if(!level.addFreshEntity(unrelated))throw new GameTestAssertException("unrelated source added");
        h.testInfo.addListener(new GameTestListener(){
            private void clean(){SingularityFixtureService.clearGravity(level,player); unrelated.discard(); playerFixture.close(); lease.close();}
            public void testStructureLoaded(GameTestInfo i){}
            public void testPassed(GameTestInfo i,GameTestRunner r){clean();}
            public void testFailed(GameTestInfo i,GameTestRunner r){clean();}
            public void testAddedForRerun(GameTestInfo a,GameTestInfo b,GameTestRunner r){clean();}
        });
        var profile=com.juicyslew.moonstation14.ms14.singularity.ModSingularityGameplay.catalog(level)
                .get(com.juicyslew.moonstation14.ms14.singularity.ModSingularityGameplay.DEFAULT_ID);
        SingularityEntity previous=null;
        for(int stage=1;stage<=6;stage++) {
            check(execute(level,player,"ms14 singularity_fixture gravity "+stage)==1,"registered gravity stage command accepts stage "+stage);
            SingularityEntity selected=SingularityFixtureService.ownedGravitySource(level,player);
            check(selected!=null&&selected.singularityLevel()==stage,"real registered entity immediately initializes stage "+stage);
            check(selected.gravityProfile()==profile,"live entity consumes resolved gameplay prototype at stage "+stage);
            double expected=SingularityFixtureService.gravityFixtureEnergy(profile,stage);
            check(Math.abs(selected.energy()-expected)<.1,"stage "+stage+" initializes selected profile-derived energy; expected="+expected+" actual="+selected.energy());
            double distance=selected.position().distanceTo(player.position());
            check(distance>profile.gravityPullRangeAtLevel(stage),"observer begins outside stage "+stage+" effective gravity range");
            double horizontalDistance=Math.sqrt(Math.pow(selected.getX()-player.getX(),2)+Math.pow(selected.getZ()-player.getZ(),2));
            check(Math.abs(horizontalDistance-(profile.gravityPullRangeAtLevel(stage)+4))<1e-6,
                    "spawn clearance derives from stage "+stage+" effective range plus four-block margin");
            check(distance>profile.eventHorizonRadiusAtLevel(stage)+1,"observer begins outside stage "+stage+" general horizon with clearance");
            check(distance>profile.playerLethalRadiusAtLevel(stage)+1,"observer begins outside stage "+stage+" player-lethal core with clearance");
            if(previous!=null)check(previous.isRemoved(),"successful replacement removes only previously owned stage source");
            String status=SingularityFixtureService.gravityStatus(level,player);
            check(status.contains("stage="+stage)&&status.contains("distance=")&&status.contains(selected.getUUID().toString()),
                    "live status reports current owned stage/distance/UUID: "+status);
            check(!status.contains("READY"),"gravity status makes no false powered-readiness claim");
            previous=selected;
        }
        check(execute(level,player,"ms14 singularity_fixture gravity_status")==1,"registered live status command succeeds");
        check(execute(level,player,"ms14 singularity_fixture gravity 2")==1,"return to draining stage for natural-drain check");
         SingularityEntity first=SingularityFixtureService.ownedGravitySource(level,player);
        check(first!=null&&first.singularityLevel()==2&&previous.isRemoved(),"stage-two source replaced the immediate stage-six source");
        check(Math.abs(first.energy()-350)<.1,"stage-two reserve begins thirty seconds of resolved drain below next threshold");
        double before=first.energy();
        h.runAfterDelay(3,()->{
            check(first.energy()<before,"ordinary configured stage-two drain continues after fixture setup");
            check(execute(level,player,"ms14 singularity_fixture gravity 2")==1,"repeat setup succeeds");
             SingularityEntity second=SingularityFixtureService.ownedGravitySource(level,player);
            check(second!=null&&second!=first&&first.isRemoved(),"repeat setup removes only prior owned UUID and creates replacement");
            check(level.getEntity(unrelated.getUUID())==unrelated&&!unrelated.isRemoved(),"unrelated singularity survives replacement");
            check(execute(level,player,"ms14 singularity_fixture gravity_clear")==1,"explicit clear succeeds");
            check(second.isRemoved(),"cleared source is removed");
            check(level.getEntity(unrelated.getUUID())==unrelated&&!unrelated.isRemoved(),"clear preserves unrelated source");
            h.succeed();
        });
    }

    private static int execute(ServerLevel level,net.minecraft.server.level.ServerPlayer player,String command) {
        try { return level.getServer().getCommands().getDispatcher().execute(command,player.createCommandSourceStack().withPermission(2)); }
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new GameTestAssertException("gravity fixture command syntax: "+e.getMessage());}
    }
    private static void check(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}
}
