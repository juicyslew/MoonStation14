package com.juicyslew.moonstation14.ms14.tesla;

import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.singularity.containment.ContainmentFixture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"tesla", "tesla-main-range"})
@PrefixGameTestTemplate(false)
public final class TeslaMainRangeGameTests {
    private TeslaMainRangeGameTests() { }
    private static void check(boolean ok, String message) { if (!ok) throw new GameTestAssertException(message); }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=350)
    public static void resolvedMainShootsWithinAuthoredSevenBlockRange(GameTestHelper h) {
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();
        var center=h.absolutePos(new BlockPos(3,3,3));
        var lease=FixtureChunkTickets.acquire(h,new AABB(center.getX()-16,center.getY()-3,center.getZ()-16,center.getX()+16,center.getY()+8,center.getZ()+16));
         var sourceRef=new TeslaEnergyBallEntity[1];
         var pigRef=new net.minecraft.world.entity.animal.Pig[1];
         var fieldRefs=new java.util.ArrayList<com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity>();
         final long[] observedAttempts={0};
         final double[] shotDistance={Double.NaN};
         int[] waited={0};
         boolean[] setupComplete={false};
         // Install the permanent observer during test setup, before any scheduled
         // callbacks run. Registering onEachTick from the ticket callback mutates
         // GameTestInfo's callback map while its ticker is iterating it.
         h.onEachTick(()->{
             if(!setupComplete[0]) return;
             waited[0]++;
             var source=sourceRef[0];
             var pig=pigRef[0];
             if(source.lightningShotAttempts()>observedAttempts[0]) {
                 observedAttempts[0]=source.lightningShotAttempts();
                 shotDistance[0]=source.position().distanceTo(pig.position());
             }
             if(observedAttempts[0]==0) {
                 if(waited[0]>=220){var nbt=new net.minecraft.nbt.CompoundTag();source.saveWithoutId(nbt);throw new GameTestAssertException("natural MAIN first-shot timeout: waited="+waited[0]+", gameTime="+level.getGameTime()+", sourceAlive="+source.isAlive()+", added="+source.isAddedToLevel()+", entityTicking="+level.isPositionEntityTicking(source.blockPosition())+", age="+nbt.getLong("AgeTicks")+", nextShot="+nbt.getLong("NextShot")+", attempts="+source.lightningShotAttempts()+", energy="+source.energy());}
                 return;
             }
             double damage=com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(pig)
                     .getOrDefault(com.juicyslew.moonstation14.ms14.damage.DamageKeys.SHOCK,0f);
             double cageDistance=fieldRefs.stream().mapToDouble(f->pig.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(f.getBlockPos()))).min().orElseThrow();
             check(cageDistance>3,"pig remains outside mini range from every cage emitter: "+cageDistance);
             check(shotDistance[0]<7&&shotDistance[0]>3,"first natural shot range at shot-attempt transition (source="+source.position()+", target="+pig.position()+", distance="+shotDistance[0]+", attempts="+observedAttempts[0]+"): expected >3 and <7");
             check(pig.isAlive()&&Math.abs(damage-7)<.001,"normal MAIN lightning damages eligible pig once without consuming it: "+damage);
             check(source.isAlive()&&source.energy()==0&&source.lightningShotAttempts()>=1&&source.lightningBoltsEmitted()>=1,"normal MAIN emitted an authored lightning shot and remains alive");
             h.succeed();
         });
         h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            private void clean(){if(sourceRef[0]!=null&&!sourceRef[0].isRemoved())sourceRef[0].discard();if(pigRef[0]!=null&&!pigRef[0].isRemoved())pigRef[0].discard();for(var field:fieldRefs)if(!field.isRemoved()&&field.getLevel().getBlockEntity(field.getBlockPos())==field)field.getLevel().removeBlock(field.getBlockPos(),false);lease.close();}
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->{
             var fields=ContainmentFixture.placeBox(h,new BlockPos(0,0,0));fieldRefs.addAll(fields);
            var source=ModEntities.TESLA_ENERGY_BALL.get().create(level);
            var pig=net.minecraft.world.entity.EntityType.PIG.create(level);
            check(source!=null&&source.initializeSourceProfile(ModTeslaProfiles.MAIN_ID)&&pig!=null,"resolved registered MAIN and native pig create");
            sourceRef[0]=source;pigRef[0]=pig;
            source.moveTo(center.getX()+.5,center.getY()+.5,center.getZ()+.5);
            // Derive the source-center cage bounds from the actual field face planes
            // and the resolved MAIN collider radius.
            pig.moveTo(center.getX()+5.0,center.getY()+.5,center.getZ()+.5);pig.setNoAi(true);pig.setNoGravity(true);
            check(level.addFreshEntity(source)&&level.addFreshEntity(pig),"source and pig join live level");
            double nearestGenerator=fields.stream().mapToDouble(f->pig.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(f.getBlockPos()))).min().orElseThrow();
            check(nearestGenerator>3,"pig preflight is outside every field generator's mini-range: "+nearestGenerator);
            double radius=source.getBbWidth()*.5;
            double minX=fields.stream().mapToDouble(f->net.minecraft.world.phys.Vec3.atCenterOf(f.getBlockPos()).x).min().orElseThrow()+radius;
            double minY=fields.stream().mapToDouble(f->net.minecraft.world.phys.Vec3.atCenterOf(f.getBlockPos()).y).min().orElseThrow()+radius;
            double minZ=fields.stream().mapToDouble(f->net.minecraft.world.phys.Vec3.atCenterOf(f.getBlockPos()).z).min().orElseThrow()+radius;
            double maxX=fields.stream().mapToDouble(f->net.minecraft.world.phys.Vec3.atCenterOf(f.getBlockPos()).x).max().orElseThrow()-radius;
            double maxY=fields.stream().mapToDouble(f->net.minecraft.world.phys.Vec3.atCenterOf(f.getBlockPos()).y).max().orElseThrow()-radius;
            double maxZ=fields.stream().mapToDouble(f->net.minecraft.world.phys.Vec3.atCenterOf(f.getBlockPos()).z).max().orElseThrow()-radius;
            AABB allowed=new AABB(minX,minY,minZ,maxX,maxY,maxZ);
            check(allowed.getXsize()>0&&allowed.getYsize()>0&&allowed.getZsize()>0&&allowed.contains(source.position()),"fixture-derived MAIN center bounds contain initial source: "+allowed+" source="+source.position());
            double nearestMainBox=Math.sqrt(Math.pow(Math.max(0,Math.max(minX-pig.getX(),pig.getX()-maxX)),2)+Math.pow(Math.max(0,Math.max(minY-pig.getY(),pig.getY()-maxY)),2)+Math.pow(Math.max(0,Math.max(minZ-pig.getZ(),pig.getZ()-maxZ)),2));
            double farthestMainBox=0;
            for(double x:new double[]{minX,maxX})for(double y:new double[]{minY,maxY})for(double z:new double[]{minZ,maxZ})farthestMainBox=Math.max(farthestMainBox,Math.sqrt(Math.pow(pig.getX()-x,2)+Math.pow(pig.getY()-y,2)+Math.pow(pig.getZ()-z,2)));
             check(nearestMainBox>3&&farthestMainBox<7,"entire allowed MAIN source box keeps pig outside 3 and inside 7: "+nearestMainBox+"/"+farthestMainBox);
             setupComplete[0]=true;
        },center.offset(-16,-3,-16),center.offset(16,8,16));
    }
}
