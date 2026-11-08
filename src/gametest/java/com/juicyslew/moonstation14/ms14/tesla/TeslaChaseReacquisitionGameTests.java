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
public final class TeslaChaseReacquisitionGameTests {
    private TeslaChaseReacquisitionGameTests() { }
    private static void check(boolean ok, String message) { if (!ok) throw new GameTestAssertException(message); }

    @GameTest(templateNamespace="moonstation14", template="singularity_fixture_clear", batch="tesla_chase_reacquisition", timeoutTicks=220)
    public static void taggedGenericMachineRemainsCachedTarget(GameTestHelper h) {
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();
        BlockPos origin=h.absolutePos(new BlockPos(3,120,3)), machine=origin.east(5);
        var lease=com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(origin).inflate(12));
        var ref=new TeslaEnergyBallEntity[1];
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){private void clean(){if(ref[0]!=null&&!ref[0].isRemoved())ref[0].discard();level.setBlock(machine,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);lease.close();} public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){} public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();} public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();} public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){clean();}});
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(h,lease,120,()->{
            check(level.getEntitiesOfClass(TeslaEnergyBallEntity.class,new net.minecraft.world.phys.AABB(origin).inflate(25)).isEmpty(),"isolated region has no other Tesla sources");
            level.setBlock(machine,ModBlocks.CHEM_MASTER.get().defaultBlockState(),3);
            var be=level.getBlockEntity(machine);
            check(!(be instanceof LightningConductor)&&TeslaLightningSystem.resolveBlockTarget(level,be)!=null,"registered tagged chem master resolves as nonconductor target");
            var source=ModEntities.TESLA_ENERGY_BALL.get().create(level);ref[0]=source;
            check(source!=null&&source.initializeSourceProfile(ModTeslaProfiles.MAIN_ID),"resolved main profile creates");
            source.moveTo(origin.getX()+.5,origin.getY()+.5,origin.getZ()+.5);check(level.addFreshEntity(source),"source added");
            h.runAfterDelay(1,()->{
                check("BLOCK".equals(source.chaseTargetKind())&&source.hasChaseTarget(),"tagged generic machine acquired");
                h.runAfterDelay(3,()->{
                var toward=new net.minecraft.world.phys.Vec3(machine.getX()+.5,machine.getY()+.5,machine.getZ()+.5).subtract(source.position());
                check(source.hasChaseTarget()&&"BLOCK".equals(source.chaseTargetKind()),"cached generic machine remains valid");
                check(source.getDeltaMovement().dot(toward)>0,"source continues toward cached generic machine");
                h.succeed();
            });
            });
        },origin);
    }

    @GameTest(templateNamespace="moonstation14", template="singularity_fixture_clear", batch="tesla_chase_reacquisition", timeoutTicks=220)
    public static void emptySourceReacquiresOnWalkImpulseCadence(GameTestHelper h) {
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();
        BlockPos sourcePos=h.absolutePos(new BlockPos(3,120,3));
        var lease=com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(sourcePos).inflate(15));
        var ref=new TeslaEnergyBallEntity[1];
        var ownedRod=new BlockPos[1];
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){private void clean(){if(ref[0]!=null&&!ref[0].isRemoved())ref[0].discard();if(ownedRod[0]!=null)level.setBlock(ownedRod[0],net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);lease.close();} public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){} public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();} public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();} public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){clean();}});
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(h,lease,120,()->{
        check(level.getEntitiesOfClass(TeslaEnergyBallEntity.class,new net.minecraft.world.phys.AABB(sourcePos).inflate(25)).isEmpty(),"isolated region has no nearby Tesla targets");
        var source=ModEntities.TESLA_ENERGY_BALL.get().create(h.getLevel());
        ref[0]=source;
        check(source!=null&&source.initializeSourceProfile(ModTeslaProfiles.MAIN_ID),"resolved main profile creates");
        source.moveTo(sourcePos.getX()+.5,sourcePos.getY()+.5,sourcePos.getZ()+.5);
        source.setDeltaMovement(1,0,0);
        check(level.addFreshEntity(source),"source added");
        h.runAfterDelay(3,()->{
            check(!source.hasChaseTarget(),"initial empty scan leaves source targetless");
            BlockPos rod=source.blockPosition().south(6);
            ownedRod[0]=rod;
            level.setBlock(rod,ModBlocks.TESLA_GROUNDING_ROD.get().defaultBlockState(),3);
            h.runAfterDelay(40,()->{
                var toward=new net.minecraft.world.phys.Vec3(rod.getX()+.5,rod.getY()+.5,rod.getZ()+.5).subtract(source.position());
                check(source.hasChaseTarget(),"targetless source reacquires before scheduled random retarget");
                check(source.getDeltaMovement().dot(toward)>0,"reacquired impulse turns toward rod");
                level.setBlock(rod,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);h.succeed();
            });
        });
        },sourcePos);
    }
}
