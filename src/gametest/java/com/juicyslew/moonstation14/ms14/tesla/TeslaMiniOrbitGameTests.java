package com.juicyslew.moonstation14.ms14.tesla;

import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.juicyslew.moonstation14.ms14.singularity.containment.ContainmentFixture;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;

@GameTestTags({"tesla","tesla-motion"})
@PrefixGameTestTemplate(false)
public final class TeslaMiniOrbitGameTests {
    private static final int OBSERVATION_TICKS=1800;
    private TeslaMiniOrbitGameTests() { }
    private static void check(boolean ok,String message){if(!ok)throw new GameTestAssertException(message);}

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=2100)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"merge-main-remaining", "merge-main-stage-two"})
    public static void registeredMiniContinuouslyOrbitsItsLiveMainTarget(GameTestHelper h) {
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();
        int baseY=level.getMaxBuildHeight()-40;
        var origin=h.absolutePos(new BlockPos(3,baseY+3,3));
        var lease=FixtureChunkTickets.acquire(h,new AABB(origin.offset(-10,-4,-10)).expandTowards(20,12,20));
        var refs=new TeslaEnergyBallEntity[2];
        var fieldRefs=new java.util.ArrayList<com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity>();
        var floorRefs=new java.util.ArrayList<BlockPos>();
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            private void clean(){for(var entity:refs)if(entity!=null&&!entity.isRemoved())entity.discard();
                for(var field:fieldRefs)if(!field.isRemoved())field.getLevel().removeBlock(field.getBlockPos(),false);
                for(var pos:floorRefs)if(level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.STONE))level.removeBlock(pos,false);lease.close();}
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->{
        fieldRefs.addAll(ContainmentFixture.placeBox(h,new BlockPos(0,baseY,0)));
         // Floor is below the enclosure's bottom emitters and spans the mini's full radius-eight envelope.
        for(int x=-6;x<=13;x++)for(int z=-6;z<=13;z++){BlockPos local=new BlockPos(x,baseY,z);h.setBlock(local,net.minecraft.world.level.block.Blocks.STONE);floorRefs.add(h.absolutePos(local));}
        var main=ModEntities.TESLA_ENERGY_BALL.get().create(level);
        var mini=ModEntities.TESLA_ENERGY_BALL.get().create(level);
        check(main!=null&&mini!=null&&main.initializeSourceProfile(ModTeslaProfiles.MAIN_ID)
                &&mini.initializeSourceProfile(ModTeslaProfiles.MINI_ID),"normal catalog profiles initialize");
        refs[0]=main;refs[1]=mini;
        main.moveTo(origin.getX()+.5,origin.getY()+.5,origin.getZ()+.5);
        mini.moveTo(origin.getX()+4.5,origin.getY()+.5,origin.getZ()+.5);
        check(level.addFreshEntity(main)&&level.addFreshEntity(mini),"live main and mini are admitted to level");
         double[] min={Double.POSITIVE_INFINITY},max={0},minY={Double.POSITIVE_INFINITY},maxY={Double.NEGATIVE_INFINITY},maxStep={0},maxAccel={0},maxPlaneVolume={0};
          Vec3[] planeSamples=new Vec3[4];
        Vec3[] prevPos={mini.position()},prevVelocity={Vec3.ZERO};
         awaitTarget(h,main,mini,fieldRefs,0,min,max,minY,maxY,maxStep,maxAccel,maxPlaneVolume,planeSamples,prevPos,prevVelocity);
        },origin.offset(-8,-3,-8),origin.offset(8,8,8));
    }
    private static void awaitTarget(GameTestHelper h,TeslaEnergyBallEntity main,TeslaEnergyBallEntity mini,
            java.util.List<com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity> fields,int waited,
             double[] min,double[] max,double[] minY,double[] maxY,double[] maxStep,double[] maxAccel,double[] maxPlaneVolume,Vec3[] planeSamples,Vec3[] prevPos,Vec3[] prevVelocity) {
        h.runAfterDelay(1,()->{
             if(!mini.hasChaseTarget()&&waited<20){awaitTarget(h,main,mini,fields,waited+1,min,max,minY,maxY,maxStep,maxAccel,maxPlaneVolume,planeSamples,prevPos,prevVelocity);return;}
            check(main.isAlive()&&mini.isAlive()&&mini.hasChaseTarget()&&"ENTITY".equals(mini.chaseTargetKind()),"mini acquires its live main target within one second of normal ticking");
            prevPos[0]=mini.position();prevVelocity[0]=mini.getDeltaMovement();
             sample(h,main,mini,fields,0,min,max,minY,maxY,maxStep,maxAccel,maxPlaneVolume,planeSamples,prevPos,prevVelocity);
        });
    }
    private static void sample(GameTestHelper h,TeslaEnergyBallEntity main,TeslaEnergyBallEntity mini,java.util.List<com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity> fields,int tick,
             double[] min,double[] max,double[] minY,double[] maxY,double[] maxStep,double[] maxAccel,double[] maxPlaneVolume,Vec3[] planeSamples,Vec3[] prevPos,Vec3[] prevVelocity) {
        h.runAfterDelay(1,()->{
            check(main.isAlive()&&mini.isAlive()&&mini.hasChaseTarget()&&"ENTITY".equals(mini.chaseTargetKind()),"mini retains its live main target");
            if(tick>0&&tick%200==0)for(var field:fields)field.receiveEmitterBolt();
            Vec3 pos=mini.position(),velocity=mini.getDeltaMovement();
              Vec3 relative=pos.subtract(main.position());
              double radius=relative.length();min[0]=Math.min(min[0],radius);max[0]=Math.max(max[0],radius);
             if(tick%40==0){int slot=(tick/40)%4;planeSamples[slot]=relative;
                  if(tick>=120&&planeSamples[0]!=null&&planeSamples[1]!=null&&planeSamples[2]!=null&&planeSamples[3]!=null)
                      maxPlaneVolume[0]=Math.max(maxPlaneVolume[0],Math.abs(planeSamples[0].subtract(planeSamples[3]).dot(planeSamples[1].subtract(planeSamples[3]).cross(planeSamples[2].subtract(planeSamples[3])))));}
            minY[0]=Math.min(minY[0],pos.y-main.getY());maxY[0]=Math.max(maxY[0],pos.y-main.getY());
            maxStep[0]=Math.max(maxStep[0],pos.distanceTo(prevPos[0]));maxAccel[0]=Math.max(maxAccel[0],velocity.subtract(prevVelocity[0]).length());
            check(Double.isFinite(pos.y)&&Double.isFinite(velocity.length())&&velocity.length()<=7.001,"finite bounded mini trajectory");
            prevPos[0]=pos;prevVelocity[0]=velocity;
              if(tick<OBSERVATION_TICKS-1){sample(h,main,mini,fields,tick+1,min,max,minY,maxY,maxStep,maxAccel,maxPlaneVolume,planeSamples,prevPos,prevVelocity);return;}
              check(min[0]<=3.5&&max[0]>=5.5,"orbit bands min="+min[0]+" max="+max[0]+" ticks="+OBSERVATION_TICKS+" uuid="+mini.getUUID()+" radial=14s angular=10s response=1.5 profile="+mini.profileId()+" main="+main.position()+" mini="+mini.position()+" velocity="+mini.getDeltaMovement());
             check(maxY[0]-minY[0]>1,"orbit varies in 3D: "+minY[0]+".."+maxY[0]);
             check(maxPlaneVolume[0]>.5,"orbit trajectory is not confined to a single orbital plane: "+maxPlaneVolume[0]);
            check(maxStep[0]<=.351,"continuous bounded fixed-step motion step="+maxStep[0]);
            check(maxAccel[0]<=.401,"continuous orbit acceleration is bounded without periodic chase resets: "+maxAccel[0]);
            check(mini.energy()==0,"mini orbit cannot reproduce/feed energy");h.succeed();
        });
    }
}
