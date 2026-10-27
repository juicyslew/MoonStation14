package com.juicyslew.moonstation14.ms14.tesla;

import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.juicyslew.moonstation14.ms14.singularity.containment.ContainmentFixture;

@GameTestTags({"tesla"})
@PrefixGameTestTemplate(false)
public final class TeslaMiniOwnershipGameTests {
    private TeslaMiniOwnershipGameTests() { }
    private static void check(boolean ok,String message){if(!ok)throw new GameTestAssertException(message);}

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=160)
    public static void registeredMiniRetainsMainAcrossEightBlockOrbitAndClosesGap(GameTestHelper h) {
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();
        int baseY=level.getMaxBuildHeight()-40;
        var origin=h.absolutePos(new BlockPos(3,baseY+3,3));
        var lease=FixtureChunkTickets.acquire(h,new AABB(origin.offset(-10,-4,-10)).expandTowards(24,12,20));
        var refs=new TeslaEnergyBallEntity[2];
        var fields=new java.util.ArrayList<com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity>();
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            private void clean(){for(var e:refs)if(e!=null&&!e.isRemoved())e.discard();for(var f:fields)if(!f.isRemoved())f.getLevel().removeBlock(f.getBlockPos(),false);lease.close();}
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->{
            fields.addAll(ContainmentFixture.placeBox(h,new BlockPos(0,baseY,0)));
            var main=ModEntities.TESLA_ENERGY_BALL.get().create(level);var mini=ModEntities.TESLA_ENERGY_BALL.get().create(level);
            check(main!=null&&mini!=null&&main.initializeSourceProfile(ModTeslaProfiles.MAIN_ID)&&mini.initializeSourceProfile(ModTeslaProfiles.MINI_ID),"normal main and mini profiles initialize");refs[0]=main;refs[1]=mini;
             // Begin inside ordinary acquisition range; move the mini to the opposite
             // side only after ownership is established below.
             main.moveTo(origin.getX()+.5,origin.getY()+.5,origin.getZ()+.5);mini.moveTo(origin.getX()+4.5,origin.getY()+.5,origin.getZ()+.5);
            // Delay jump/shoot cadences through the entity's existing serialized scheduler fields.
            var tag=new CompoundTag();main.saveWithoutId(tag);tag.putLong("NextJump",Long.MAX_VALUE);tag.putLong("NextShot",Long.MAX_VALUE);main.load(tag);
            check(level.addFreshEntity(main)&&level.addFreshEntity(mini),"main and mini admitted");
            awaitOwned(h,main,mini,0);
        },origin.offset(-8,-3,-8),origin.offset(12,8,8));
    }

    private static void awaitOwned(GameTestHelper h,TeslaEnergyBallEntity main,TeslaEnergyBallEntity mini,int ticks) {
        h.runAfterDelay(1,()->{
            if(!mini.hasChaseTarget()&&ticks<20){awaitOwned(h,main,mini,ticks+1);return;}
             check(mini.hasChaseTarget()&&"ENTITY".equals(mini.chaseTargetKind()),"registered mini acquired the live main entity");
             mini.setPos(main.getX()-7.5,mini.getY(),mini.getZ());
             main.setPos(main.getX()+10,main.getY(),main.getZ());
            double initialGap=mini.position().distanceTo(main.position());
            check(initialGap>9,"main moved ten blocks away from mini's eight-block orbit; actual separation="+initialGap);
            verifyClosure(h,main,mini,initialGap,0);
        });
    }
    private static void verifyClosure(GameTestHelper h,TeslaEnergyBallEntity main,TeslaEnergyBallEntity mini,double initialGap,int tick) {
        h.runAfterDelay(1,()->{
            check(main.isAlive()&&mini.isAlive()&&mini.hasChaseTarget()&&"ENTITY".equals(mini.chaseTargetKind()),"mini retains its live main owner while it is beyond ordinary reacquisition range");
            double gap=mini.position().distanceTo(main.position());
            if(tick==39)check(gap<initialGap-1,"normal entity ticks close actual main-mini separation instead of drifting: start="+initialGap+", end="+gap);
            else verifyClosure(h,main,mini,initialGap,tick+1);
            if(tick==39)h.succeed();
        });
    }
}
