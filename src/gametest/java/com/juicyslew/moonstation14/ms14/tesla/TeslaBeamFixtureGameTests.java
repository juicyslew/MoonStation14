package com.juicyslew.moonstation14.ms14.tesla;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"tesla", "tesla-beam-fixture"})
@PrefixGameTestTemplate(false)
public final class TeslaBeamFixtureGameTests {
    private TeslaBeamFixtureGameTests() { }
    private static void check(boolean ok,String message){if(!ok)throw new GameTestAssertException(message);}
    private static float shock(LivingEntity entity){return com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(entity)
            .getOrDefault(com.juicyslew.moonstation14.ms14.damage.DamageKeys.SHOCK,0f);}

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=430)
    public static void queuedFixtureBeamUsesNaturalServerTickAndResolvedLightningContact(GameTestHelper h){
        ServerLevel level=(ServerLevel)h.getLevel();
        BlockPos origin=h.absolutePos(new BlockPos(4,4,4));
        var lease=com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.acquire(h, new AABB(origin).inflate(8));
        var bodyRef=new LivingEntity[1];
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            private void clean(){if(bodyRef[0]!=null&&!bodyRef[0].isRemoved())bodyRef[0].discard();lease.close();}
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->{
            var pig=net.minecraft.world.entity.EntityType.PIG.create(level);
            check(pig!=null,"native pig fixture target constructs");bodyRef[0]=pig;
            pig.setNoAi(true);pig.setNoGravity(true);
            pig.moveTo(origin.getX()+2,origin.getY(),origin.getZ()+.5);
            check(level.addFreshEntity(pig),"pig enters entity-ticking leased fixture span");
            var beamCells=new java.util.LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
            for(int x=0;x<=3;x++){BlockPos cell=origin.east(x);beamCells.put(cell,level.getBlockState(cell));}
            h.runAfterDelay(1,()->{
                float baseline=shock(pig);
                check(level.getEntitiesOfClass(TeslaEnergyBallEntity.class,new AABB(origin).inflate(8)).isEmpty(),"fixture target area contains no Tesla source ball");
                String command="ms14 tesla_fixture beam_test "+origin.getX()+" "+origin.getY()+" "+origin.getZ();
                var dispatcher=level.getServer().getCommands().getDispatcher();
                var unprivileged=level.getServer().createCommandSourceStack().withLevel(level).withPermission(0);
                boolean denied=false;
                try{dispatcher.execute(command,unprivileged);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){denied=true;}
                check(denied,"registered beam-test command rejects a console source below permission level 2");
                var console=level.getServer().createCommandSourceStack().withLevel(level).withPermission(2);
                int queued;
                try{queued=dispatcher.execute(command,console);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException failure){
                    throw new GameTestAssertException("registered privileged explicit-coordinate beam command failed to parse/execute: "+failure.getMessage());
                }
                check(queued==1,"registered explicit-coordinate console command queues its targetless beam");
                long queuedAt=level.getGameTime();
                h.runAfterDelay(98,()->{
                    check(Math.abs(shock(pig)-baseline)<.01,"queued fixture beam causes no early shock before its 100-tick server deadline");
                    pig.moveTo(origin.getX()+2,origin.getY(),origin.getZ()+3.5);
                    h.runAfterDelay(5,()->{
                        pig.moveTo(origin.getX()+2,origin.getY(),origin.getZ()+.5);
                        h.runAfterDelay(3,()->{
                            float first=shock(pig);
                            check(Math.abs(first-baseline-7f)<.01,"normal service tick emits the queued 5-second beam and shocks the entered pig; queuedAt="+queuedAt+" now="+level.getGameTime()+" shock="+first+" baseline="+baseline);
                            h.runAfterDelay(5,()->{
                                check(Math.abs(shock(pig)-first)<.01,"continued overlap receives no repeated beam shock");
                                pig.moveTo(origin.getX()+2,origin.getY(),origin.getZ()+3.5);
                                int lifetime=(int)Math.round(ModTeslaProfiles.balls(level).get(ModTeslaProfiles.MINI_ID).lightning().sourceLifetimeSeconds()*20);
                                h.runAfterDelay(lifetime+5,()->{
                                    pig.moveTo(origin.getX()+2,origin.getY(),origin.getZ()+.5);
                                    h.runAfterDelay(3,()->{
                                        check(Math.abs(shock(pig)-first)<.01,"expired fixture beam cannot shock a later entrant");
                                        check(beamCells.entrySet().stream().allMatch(e->level.getBlockState(e.getKey()).equals(e.getValue())),"beam fixture leaves every beam-span block unchanged");
                                        check(level.getEntitiesOfClass(TeslaEnergyBallEntity.class,new AABB(origin).inflate(8)).isEmpty(),"beam fixture creates no Tesla source ball");
                                        h.succeed();
                                    });
                                });
                            });
                        });
                    });
                });
            });
        },origin.offset(-8,-8,-8),origin.offset(8,8,8));
    }
}
