package com.juicyslew.moonstation14.ms14.singularity;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity;
import com.juicyslew.moonstation14.block.block_entity.SingularityGeneratorBlockEntity;
import com.juicyslew.moonstation14.entities.SingularityEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.singularity.containment.ContainmentFixture;
import com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime;
import com.juicyslew.moonstation14.ms14.singularity.pa.ModParticleAccelerators;
import com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorStrength;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestTags({"singularity-generator", "field-generator-adapter-fixture"})
@PrefixGameTestTemplate(false)
public final class SingularityGeneratorGameTests {
    private SingularityGeneratorGameTests() { }
    private static void check(boolean result, String message) { if(!result) throw new GameTestAssertException(message); }
    private static void clean(GameTestHelper h, List<FieldGeneratorBlockEntity> corners, BlockPos generatorPos) {
        BlockPos min=h.absolutePos(new BlockPos(0,0,0)), max=h.absolutePos(new BlockPos(8,8,8));
        h.getLevel().getEntitiesOfClass(SingularityEntity.class, new net.minecraft.world.phys.AABB(min.getX(),min.getY(),min.getZ(),max.getX()+1,max.getY()+1,max.getZ()+1)).forEach(entity -> entity.discard());
        h.setBlock(generatorPos, Blocks.AIR);
        for (var be: corners) h.getLevel().setBlock(be.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
        FieldContainmentRuntime.refresh(h.getLevel());
    }
    private static SingularityGeneratorBlockEntity generator(GameTestHelper h) {
        BlockPos local=new BlockPos(3,3,3); h.setBlock(local, ModBlocks.SINGULARITY_GENERATOR.get());
        if (!(h.getLevel().getBlockEntity(h.absolutePos(local)) instanceof SingularityGeneratorBlockEntity be)) throw new GameTestAssertException("registered singularity generator creates startup-only block entity");
        return be;
    }
    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=300)
    public static void startupRequiresSixFaceVolumeAndSpawnsOnceAtProfileEnergy(GameTestHelper h) {
        var corners=ContainmentFixture.placeBox(h); var gen=generator(h);
        check(!h.getLevel().getBlockState(gen.getBlockPos()).canOcclude(),"registered startup generator art must not occlude rays through its partial machinery geometry");
        try {
            SingularityGeneratorBlockEntity.class.getDeclaredMethod("receiveEmitterBolt");
            throw new GameTestAssertException("singularity generator must not expose field bolt charging");
        } catch(NoSuchMethodException expected) { }
        try {
            SingularityGeneratorBlockEntity.class.getDeclaredMethod("connectionEligible");
            throw new GameTestAssertException("singularity generator must not expose field connection role");
        } catch(NoSuchMethodException expected) { }
        try {
            FieldGeneratorBlockEntity.class.getDeclaredMethod("receiveParticle", com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorStrengthProfile.class);
            throw new GameTestAssertException("ordinary field generator must not expose singularity startup input");
        } catch(NoSuchMethodException expected) { }
        var center=h.absolutePos(new BlockPos(3,3,3));
        check(corners.stream().noneMatch(be->be.getBlockPos().equals(center)),"startup entity is not among the field topology's eight corner placements");
        var cornerPositions=corners.stream().map(FieldGeneratorBlockEntity::getBlockPos).collect(java.util.stream.Collectors.toSet());
        long localEdges=FieldContainmentRuntime.currentEdges(h.getLevel()).stream().filter(edge->cornerPositions.contains(edge.first())&&cornerPositions.contains(edge.second())).count();
        check(localEdges==12,"only registered field generators supply the enclosure's twelve links");
        var selected=ModParticleAccelerators.catalog(h.getLevel()).get(ModParticleAccelerators.TYPE.typeId());
        check(selected!=null,"resolved PA prototype exists");
        var strength=selected.strength(ParticleAcceleratorStrength.LEVEL_1);
        check(strength.generatorCharge()==2,"charge uses selected resolved PA strength");
        var id=gen.placementIdentity();
        for(int i=0;i<7;i++) check(!gen.receiveParticle(strength),"partial startup does not spawn");
        check(gen.receiveParticle(strength),"threshold particle spawns");
        var spawned=h.getLevel().getEntitiesOfClass(SingularityEntity.class,new net.minecraft.world.phys.AABB(gen.getBlockPos()).inflate(4));
        check(spawned.size()==1,"one registered singularity spawned");
        var singularity=spawned.getFirst();
        check(singularity.position().equals(new net.minecraft.world.phys.Vec3(gen.getBlockPos().getX()+.5,gen.getBlockPos().getY()+.5,gen.getBlockPos().getZ()+.5)),"spawn retains the shared generator-center position");
        check(singularity.profileId().equals(ModSingularityGenerators.catalog(h.getLevel()).get(ModSingularityGenerators.DEFAULT_ID).spawnProfile()),"spawn uses configured resolved gameplay profile");
        check(Double.isFinite(singularity.energy())&&singularity.energy()>0,"spawn energy is initialized from resolved gameplay profile");
        check(gen.placementIdentity().equals(id)&&gen.startupInert()&&gen.startupCharge()==0,"inert state and placement identity persist after one spawn");
        check(!gen.receiveParticle(strength)&&h.getLevel().getEntitiesOfClass(SingularityEntity.class,new net.minecraft.world.phys.AABB(gen.getBlockPos()).inflate(4)).size()==1,"inert generator never duplicates spawn");
        clean(h,corners,new BlockPos(3,3,3)); h.succeed();
    }
    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=80)
    public static void partialChargeAndPlacementIdentitySurviveBlockEntityReload(GameTestHelper h) {
        var corners=ContainmentFixture.placeBox(h); var gen=generator(h);
        var strength=ModParticleAccelerators.catalog(h.getLevel()).get(ModParticleAccelerators.TYPE.typeId()).strength(ParticleAcceleratorStrength.LEVEL_1);
        for(int i=0;i<4;i++) check(!gen.receiveParticle(strength),"partial charge remains below threshold");
        var expectedIdentity=gen.placementIdentity();
        var saved=gen.saveWithFullMetadata(h.getLevel().registryAccess());
        BlockPos local=new BlockPos(3,3,3); h.setBlock(local,Blocks.AIR); h.setBlock(local,ModBlocks.SINGULARITY_GENERATOR.get());
        var restored=(SingularityGeneratorBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(local));
        restored.loadWithComponents(saved,h.getLevel().registryAccess());
        check(restored.placementIdentity().equals(expectedIdentity)&&restored.startupCharge()==8,"NBT restores partial startup charge and placement UUID");
        for(int i=0;i<4;i++) restored.receiveParticle(strength);
        check(restored.startupInert()&&h.getLevel().getEntitiesOfClass(SingularityEntity.class,new net.minecraft.world.phys.AABB(restored.getBlockPos()).inflate(4)).size()==1,"restored partial charge completes one startup");
        clean(h,corners,local); h.succeed();
    }
    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=280)
    public static void brokenTopCooldownDoesNotExtendAndLateValidChargeSpawns(GameTestHelper h) {
        var corners=ContainmentFixture.placeBox(h); var gen=generator(h);
        BlockPos missing=new BlockPos(5,5,5); h.setBlock(missing,Blocks.AIR); FieldContainmentRuntime.refresh(h.getLevel());
        var strength=ModParticleAccelerators.catalog(h.getLevel()).get(ModParticleAccelerators.TYPE.typeId()).strength(ParticleAcceleratorStrength.LEVEL_1);
        check(!gen.receiveParticle(strength),"broken top face refuses startup");
        long deadline=gen.startupNextFailsafeTick();
        for(int pulse=1;pulse<=9;pulse++) h.runAfterDelay(pulse*20,()->{
            check(!gen.receiveParticle(strength),"blocked particle cannot charge during failsafe");
            check(gen.startupNextFailsafeTick()==deadline,"blocked pulses do not extend cooldown");
        });
        h.runAfterDelay(200,()->{
            h.setBlock(missing,ModBlocks.FIELD_GENERATOR.get());
            var restored=(FieldGeneratorBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(missing));
            restored.receiveEmitterBolt(); restored.receiveEmitterBolt(); restored.toggleEnabled();
            FieldContainmentRuntime.refresh(h.getLevel());
            check(FieldContainmentRuntime.closedVolumes(h.getLevel()).stream().anyMatch(v->v.min().getX()<=gen.getBlockPos().getX()&&v.max().getX()>=gen.getBlockPos().getX()),"repaired loaded eight-corner field closes");
            for(int i=0;i<8;i++) gen.receiveParticle(strength);
            check(gen.startupInert()&&h.getLevel().getEntitiesOfClass(SingularityEntity.class,new net.minecraft.world.phys.AABB(gen.getBlockPos()).inflate(4)).size()==1,"late valid charge starts exactly once after cooldown");
            clean(h,corners,new BlockPos(3,3,3)); h.succeed();
        });
    }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=50)
    public static void brokenBottomFaceRefusesStartup(GameTestHelper h) {
        var corners=ContainmentFixture.placeBox(h); var gen=generator(h);
        h.setBlock(new BlockPos(5,1,5),Blocks.AIR); FieldContainmentRuntime.refresh(h.getLevel());
        var strength=ModParticleAccelerators.catalog(h.getLevel()).get(ModParticleAccelerators.TYPE.typeId()).strength(ParticleAcceleratorStrength.LEVEL_1);
        for(int i=0;i<8;i++) check(!gen.receiveParticle(strength),"broken bottom face refuses startup");
        check(!gen.startupInert()&&gen.startupCharge()==0,"broken bottom does not charge or become inert");
        clean(h,corners,new BlockPos(3,3,3)); h.succeed();
    }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=50)
    public static void creativeTabContainsRegisteredGeneratorBlockOnce(GameTestHelper h) {
        long entries=com.juicyslew.moonstation14.item.ModCreativeModeTabs.MOONSTATION_BLOCK_ENTRIES.stream()
                .filter(entry -> entry == ModBlocks.SINGULARITY_GENERATOR).count();
        check(entries==1,"registered generator appears exactly once in canonical Moonstation Blocks tab");
        h.succeed();
    }
}
