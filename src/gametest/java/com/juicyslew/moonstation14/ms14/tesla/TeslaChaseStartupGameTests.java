package com.juicyslew.moonstation14.ms14.tesla;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"tesla"})
@PrefixGameTestTemplate(false)
public final class TeslaChaseStartupGameTests {
    private TeslaChaseStartupGameTests() { }
    private static void check(boolean ok,String message){if(!ok)throw new GameTestAssertException(message);}

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=200)
    public static void mainAcquiresAndMovesTowardRodOnFirstTick(GameTestHelper h) {
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();
        BlockPos origin=h.absolutePos(new BlockPos(3,120,3)),rodPos=origin.east(3);
        var lease=com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.acquire(h,
                new net.minecraft.world.phys.AABB(origin).inflate(26));
        var refs=new Object[2];
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            private void clean(){if(refs[1] instanceof TeslaEnergyBallEntity ball&&!ball.isRemoved())ball.discard();
                if(refs[0] instanceof net.minecraft.world.level.block.entity.BlockEntity rod
                        &&level.getBlockEntity(rodPos)==rod
                        &&level.getBlockState(rodPos).is(ModBlocks.TESLA_GROUNDING_ROD.get()))
                    level.setBlock(rodPos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
                lease.close();}
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        // Overall fixture timeout budgets this bounded setup wait; the success assertion still measures the first entity tick.
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->{
            level.setBlock(rodPos,ModBlocks.TESLA_GROUNDING_ROD.get().defaultBlockState(),3);
            var rod=level.getBlockEntity(rodPos);var source=ModEntities.TESLA_ENERGY_BALL.get().create(level);refs[0]=rod;refs[1]=source;
            check(rod!=null&&source!=null&&source.initializeSourceProfile(ModTeslaProfiles.MAIN_ID),"live rod and resolved main source create");
            source.moveTo(origin.getX()+.5,origin.getY()+.5,origin.getZ()+.5);
            var profile=ModTeslaProfiles.balls(level).get(ModTeslaProfiles.MAIN_ID);
            double range=profile.chasingWalk().range();
            var candidates=com.juicyslew.moonstation14.ms14.tesla.TeslaLightningSystem.targets(level,source.position(),range);
            check(candidates.size()==1&&candidates.get(0).blockEntity()==rod,"isolated chase candidate set must contain exactly own rod; candidates="
                    +candidates.stream().map(t->t.position()+"/"+t.blockEntity()).toList()+" origin="+origin);
            check(level.addFreshEntity(source),"main source joins native level");
            h.runAfterDelay(1,()->{
                long age=source.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getLong("AgeTicks");
                var toward=rodPos.getCenter().subtract(source.position());
                check(age==1&&source.hasChaseTarget()&&"BLOCK".equals(source.chaseTargetKind())
                                &&rodPos.equals(net.minecraft.core.BlockPos.containing(source.chaseTargetPosition()))
                                &&source.getDeltaMovement().lengthSqr()>0&&source.getDeltaMovement().dot(toward)>0,
                        "first native entity tick must acquire own rod and impulse toward it immediately; age="+age+" target="
                                +source.chaseTargetPosition()+" kind="+source.chaseTargetKind()+" candidates="+candidates.size()
                                +" origin="+source.position()+" velocity="+source.getDeltaMovement()+" entityTicking="+lease.entityTicking(origin));
                h.succeed();
            });
        },origin,rodPos);
    }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=20)
    public static void miniAcquiresMainBallOnFirstTick(GameTestHelper h) {
        BlockPos local=new BlockPos(4,4,4);
        var main=ModEntities.TESLA_ENERGY_BALL.get().create(h.getLevel());
        var mini=ModEntities.TESLA_ENERGY_BALL.get().create(h.getLevel());
        check(main!=null&&main.initializeSourceProfile(ModTeslaProfiles.MAIN_ID)
                &&mini!=null&&mini.initializeSourceProfile(ModTeslaProfiles.MINI_ID),"resolved main and mini profiles create");
        main.moveTo(h.absolutePos(local).getX()+.5,h.absolutePos(local).getY()+.5,h.absolutePos(local).getZ()+.5);
        mini.moveTo(main.getX()+2,main.getY(),main.getZ());
        check(h.getLevel().addFreshEntity(main)&&h.getLevel().addFreshEntity(mini),"main and mini join native level");
        h.runAfterDelay(1,()->{
            check(mini.hasChaseTarget()&&"ENTITY".equals(mini.chaseTargetKind())&&mini.getDeltaMovement().lengthSqr()>0,
                    "mini acquires main immediately and begins orbit steering rather than a direct chase impulse");
            mini.discard();main.discard();h.succeed();
        });
    }

    @GameTest(templateNamespace="moonstation14",template="singularity_fixture_clear",batch="tesla_motion",timeoutTicks=200)
    public static void targetlessVerticalVelocityHalvesWithoutHorizontalDrag(GameTestHelper h) {
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();
        var origin=h.absolutePos(new BlockPos(3,120,3));
        var lease=com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.acquire(h,
                new net.minecraft.world.phys.AABB(origin.getX()-2,origin.getY()-2,origin.getZ()-2,origin.getX()+3,origin.getY()+3,origin.getZ()+3));
        var ref=new TeslaEnergyBallEntity[1];
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            private void clean(){if(ref[0]!=null&&!ref[0].isRemoved())ref[0].discard();lease.close();}
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->{
        check(level.getEntitiesOfClass(TeslaEnergyBallEntity.class,new net.minecraft.world.phys.AABB(origin).inflate(25)).isEmpty(),"isolated lease has no nearby Tesla targets");
        var ball=ModEntities.TESLA_ENERGY_BALL.get().create(level);ref[0]=ball;
        check(ball!=null&&ball.initializeSourceProfile(ModTeslaProfiles.MINI_ID),"resolved mini profile creates");
        ball.moveTo(origin.getX()+.5,origin.getY()+.5,origin.getZ()+.5);
        ball.setDeltaMovement(1,2,3);check(level.addFreshEntity(ball),"ball joins level");
        h.runAfterDelay(20,()->{
            check(Math.abs(ball.getDeltaMovement().y-1)<.08,"targetless vertical velocity halves over twenty ticks");
            check(Math.abs(ball.getDeltaMovement().x-1)<1e-9&&Math.abs(ball.getDeltaMovement().z-3)<1e-9,"targetless damping preserves horizontal velocity");
            h.succeed();
        });
        },origin);
    }

    @GameTest(templateNamespace="moonstation14",template="singularity_fixture_clear",batch="tesla_motion",timeoutTicks=200)
    public static void validAboveTargetKeepsVerticalChaseImpulseUndamped(GameTestHelper h) {
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();var origin=h.absolutePos(new BlockPos(3,120,3));var rod=origin.above(6);
        var lease=com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(origin).inflate(10));
        var ref=new TeslaEnergyBallEntity[1];
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            private void clean(){if(ref[0]!=null&&!ref[0].isRemoved())ref[0].discard();level.setBlock(rod,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);lease.close();}
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->{
        level.setBlock(rod,ModBlocks.TESLA_GROUNDING_ROD.get().defaultBlockState(),3);
        var ball=ModEntities.TESLA_ENERGY_BALL.get().create(level);ref[0]=ball;
        check(ball!=null&&ball.initializeSourceProfile(ModTeslaProfiles.MAIN_ID),"resolved main profile creates");
        ball.moveTo(origin.getX()+.5,origin.getY()+.5,origin.getZ()+.5);ball.setDeltaMovement(0,1,0);
        check(level.addFreshEntity(ball),"ball joins level");
        h.runAfterDelay(1,()->{
            check(ball.getDeltaMovement().y>0,"valid target above receives immediate vertical pursuit impulse");
            double first=ball.getDeltaMovement().y;
            double firstX=ball.getDeltaMovement().x,firstZ=ball.getDeltaMovement().z;
            h.runAfterDelay(20,()->{
                check(Math.abs(ball.getDeltaMovement().y-first)<1e-8,"valid remembered target vertical pursuit is not damped between impulses");
                check(Math.abs(ball.getDeltaMovement().x-firstX)<1e-8&&Math.abs(ball.getDeltaMovement().z-firstZ)<1e-8,"valid target velocity remains unchanged between impulses");
                h.succeed();
            });
        });
        },origin);
    }

    @GameTest(templateNamespace="moonstation14",template="singularity_fixture_clear",batch="tesla_motion",timeoutTicks=200)
    public static void removedRodClearsRememberedTargetBeforeDamping(GameTestHelper h) {
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();var origin=h.absolutePos(new BlockPos(3,120,3));var rod=origin.above(6);
        var lease=com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(origin).inflate(10));
        var ref=new TeslaEnergyBallEntity[1];
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            private void clean(){if(ref[0]!=null&&!ref[0].isRemoved())ref[0].discard();level.setBlock(rod,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);lease.close();}
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->{
            level.setBlock(rod,ModBlocks.TESLA_GROUNDING_ROD.get().defaultBlockState(),3);
            var ball=ModEntities.TESLA_ENERGY_BALL.get().create(level);ref[0]=ball;
            check(ball!=null&&ball.initializeSourceProfile(ModTeslaProfiles.MAIN_ID),"resolved main profile creates");
            ball.moveTo(origin.getX()+.5,origin.getY()+.5,origin.getZ()+.5);check(level.addFreshEntity(ball),"ball joins level");
            h.runAfterDelay(1,()->{
                check(ball.getDeltaMovement().y>0,"first acquisition applies vertical chase impulse");
                var before=ball.getDeltaMovement();level.setBlock(rod,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
                h.runAfterDelay(20,()->{
                    check(Math.abs(ball.getDeltaMovement().y-before.y*.5)<.08,"removed remembered rod clears before targetless vertical half-life");
                    check(Math.abs(ball.getDeltaMovement().x-before.x)<1e-8&&Math.abs(ball.getDeltaMovement().z-before.z)<1e-8,"target loss damping leaves horizontal velocity unchanged");
                    h.succeed();
                });
            });
        },origin);
    }
}
