package com.juicyslew.moonstation14.ms14.tesla;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"tesla", "tesla-contact"})
@PrefixGameTestTemplate(false)
public final class TeslaBlockContactGameTests {
    private TeslaBlockContactGameTests() { }
    private static void check(boolean ok,String message){if(!ok)throw new GameTestAssertException(message);}

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=30)
    public static void mainConsumesMachineAtBlockOnlyContactReach(GameTestHelper h) {
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();
        BlockPos local=new BlockPos(6,4,4),world=h.absolutePos(local);
        h.setBlock(local,ModBlocks.TESLA_COIL.get());
        var source=ModEntities.TESLA_ENERGY_BALL.get().create(level);
        check(source!=null&&source.initializeSourceProfile(ModTeslaProfiles.MAIN_ID),"resolved main profile initializes");
        // Initial centers are .8 blocks apart from the coil face: outside legacy .65 contact,
        // but inside the authored .85 block-consumer reach. Native entity ticks supply the sweep.
        source.moveTo(world.getX()-.8,world.getY()+.5,world.getZ()+.5);
        check(level.addFreshEntity(source),"main joins live server level");
        h.runAfterDelay(1,()->{
            check(level.getBlockState(world).isAir(),"native tick consumes machine at dedicated block radius");
            check(source.energy()==2&&source.lastEnergyInputKind()==TeslaEnergyBallEntity.EnergyInput.BLOCK,
                    "existing block-removal path credits resolved yield exactly once");
            source.discard();h.setBlock(local,Blocks.AIR);h.succeed();
        });
    }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=30)
    public static void mainConsumesDisconnectedFieldGeneratorAtBlockOnlyContactReach(GameTestHelper h) {
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();
        BlockPos local=new BlockPos(6,4,4),world=h.absolutePos(local);
        h.setBlock(local,ModBlocks.FIELD_GENERATOR.get());
        var generator=(com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity)level.getBlockEntity(world);
        check(generator!=null,"registered field generator block entity exists");
        generator.receiveEmitterBolt();generator.receiveEmitterBolt();
        check(generator.toggleEnabled(),"disconnected field generator enables through native API");
        check(!com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime.hasConnection(level,generator),"fixture generator has no actual field connection");
        var source=ModEntities.TESLA_ENERGY_BALL.get().create(level);
        check(source!=null&&source.initializeSourceProfile(ModTeslaProfiles.MAIN_ID),"resolved main profile initializes");
        source.moveTo(world.getX()-.8,world.getY()+.5,world.getZ()+.5);
        source.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        check(level.addFreshEntity(source),"main joins live server level");
        h.runAfterDelay(1,()->{
            check(level.getBlockState(world).isAir(),"native tick consumes disconnected generator at dedicated block radius");
            check(source.energy()==2&&source.lastEnergyInputKind()==TeslaEnergyBallEntity.EnergyInput.BLOCK,"existing block-removal path credits resolved yield exactly once");
            source.discard();h.setBlock(local,Blocks.AIR);h.succeed();
        });
    }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=30)
    public static void mainPreservesConnectedFieldGeneratorAtBlockOnlyContactReach(GameTestHelper h) {
        var corners=com.juicyslew.moonstation14.ms14.singularity.containment.ContainmentFixture.placeBox(h);
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();
        var protectedField=corners.stream().filter(be->be.getBlockPos().equals(h.absolutePos(new BlockPos(5,1,1)))).findFirst().orElseThrow();
        check(com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime.hasConnection(level,protectedField),"target has an actual connected projected containment edge");
        var source=ModEntities.TESLA_ENERGY_BALL.get().create(level);
        check(source!=null&&source.initializeSourceProfile(ModTeslaProfiles.MAIN_ID),"resolved main profile initializes");
        var world=protectedField.getBlockPos();
        source.moveTo(world.getX()-.8,world.getY()+.5,world.getZ()+.5);
        source.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        check(level.addFreshEntity(source),"main joins live server level");
        h.runAfterDelay(1,()->{
            check(level.getBlockState(world).is(ModBlocks.FIELD_GENERATOR.get()),"native contact does not consume connected generator");
            check(source.energy()==0&&source.lastEnergyInputKind()==TeslaEnergyBallEntity.EnergyInput.NONE,"connected generator yields no energy");
            source.discard();corners.forEach(be->level.setBlock(be.getBlockPos(),Blocks.AIR.defaultBlockState(),3));h.succeed();
        });
    }
}
