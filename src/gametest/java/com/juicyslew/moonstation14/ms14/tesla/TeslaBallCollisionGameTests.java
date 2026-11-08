package com.juicyslew.moonstation14.ms14.tesla;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"tesla", "tesla-contact"})
@PrefixGameTestTemplate(false)
public final class TeslaBallCollisionGameTests {
    private TeslaBallCollisionGameTests() { }
    private static void check(boolean ok,String message){if(!ok)throw new GameTestAssertException(message);}
    private static TeslaEnergyBallEntity spawn(GameTestHelper h,BlockPos local,net.minecraft.resources.ResourceLocation profile,Vec3 velocity) {
        var source=ModEntities.TESLA_ENERGY_BALL.get().create(h.getLevel());
        check(source!=null&&source.initializeSourceProfile(profile),"registered Tesla profile initializes");
        var pos=h.absolutePos(local);source.moveTo(pos.getX(),pos.getY(),pos.getZ());source.setDeltaMovement(velocity);
        check(h.getLevel().addFreshEntity(source),"source enters native server level");return source;
    }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=30)
    public static void mainPhysicalContactConsumesOneCoilAndCleansItsCableWithoutDrops(GameTestHelper h) {
        BlockPos near=new BlockPos(6,4,4),far=new BlockPos(8,4,4);
        h.setBlock(near,ModBlocks.TESLA_COIL.get());h.setBlock(far,ModBlocks.TESLA_COIL.get());
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();var worldNear=h.absolutePos(near);var worldFar=h.absolutePos(far);
        check(com.juicyslew.moonstation14.ms14.power.cable.CableStorage.place(level,worldNear,Direction.NORTH,
                com.juicyslew.moonstation14.ms14.power.cable.CableTier.HV),"test coil has a real host cable record");
        var source=spawn(h,new BlockPos(5,4,4),ModTeslaProfiles.MAIN_ID,Vec3.ZERO);
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            private void clean(){if(!source.isRemoved())source.discard();
                com.juicyslew.moonstation14.ms14.power.cable.CableStorage.removeHost(level,worldNear);
                level.setBlock(worldNear,Blocks.AIR.defaultBlockState(),3);level.setBlock(worldFar,Blocks.AIR.defaultBlockState(),3);}
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        source.setPos(worldNear.getX()-.7,worldNear.getY()+.5,worldNear.getZ()+.5);
        h.runAfterDelay(1,()->{
            // Let the immediate initial target acquisition run, then provide a deterministic
            // external impulse that exercises ordinary native segment contact geometry.
            source.setDeltaMovement(new Vec3(10,0,0));
            h.runAfterDelay(1,()->{
                source.setDeltaMovement(Vec3.ZERO);
                check(level.getBlockState(worldNear).isAir(),"main swept contact physically consumes adjacent eligible coil; source="+source.position()+" velocity="+source.getDeltaMovement()+" coil="+worldNear+" energy="+source.energy()+" input="+source.lastEnergyInputKind());
                check(!com.juicyslew.moonstation14.ms14.power.cable.CableStorage.has(level,worldNear,Direction.NORTH,
                        com.juicyslew.moonstation14.ms14.power.cable.CableTier.HV),"consumption removes every host cable record");
                check(level.getBlockEntity(worldFar) instanceof TeslaMachineBlockEntity,"distant second coil remains untouched");
                check(source.energy()==2&&source.lastEnergyInputKind()==TeslaEnergyBallEntity.EnergyInput.BLOCK
                        &&source.lastEnergyInputAmount()==2,"physical block removal credits exactly one native block-contact yield");
                var drops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(worldNear).inflate(1));
                check(drops.isEmpty(),"world contact removal does not drop consumed machine inventory");
                h.runAfterDelay(2,()->{
                    check(source.energy()==2&&source.lastEnergyInputAmount()==2,"persistent contact cannot credit the same removed block twice");
                    source.discard();h.setBlock(far,Blocks.AIR);h.succeed();
                });
            });
        });
    }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=30)
    public static void mainContactRejectsNonOptInChestAndBedrock(GameTestHelper h) {
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();
        BlockPos chest=new BlockPos(6,4,4), bedrock=new BlockPos(6,4,5);
        h.setBlock(chest,Blocks.CHEST);h.setBlock(bedrock,Blocks.BEDROCK);
        var worldChest=h.absolutePos(chest);var worldBedrock=h.absolutePos(bedrock);
        var source=spawn(h,new BlockPos(5,4,4),ModTeslaProfiles.MAIN_ID,new Vec3(14,0,0));
        source.setPos(source.getX(),source.getY(),source.getZ()+.5);
        h.runAfterDelay(1,()->{
            check(level.getBlockState(worldChest).is(Blocks.CHEST)&&level.getBlockEntity(worldChest)!=null,"swept main contact rejects non-opt-in inventory blocks");
            check(level.getBlockState(worldBedrock).is(Blocks.BEDROCK),"swept main contact rejects unbreakable bedrock");
            check(source.energy()==0&&source.lastEnergyInputKind()==TeslaEnergyBallEntity.EnergyInput.NONE,"rejected blocks provide no energy");
            source.discard();h.setBlock(chest,Blocks.AIR);h.setBlock(bedrock,Blocks.AIR);h.succeed();
        });
    }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=30)
    public static void mainContactPreservesConnectedFieldGenerator(GameTestHelper h) {
        var corners=com.juicyslew.moonstation14.ms14.singularity.containment.ContainmentFixture.placeBox(h);
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();
        var protectedField=corners.stream().filter(be->be.getBlockPos().equals(h.absolutePos(new BlockPos(5,1,1)))).findFirst().orElseThrow();
        check(com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime.hasConnection(level,protectedField),"field target is an actually connected powered-volume corner");
        var source=spawn(h,new BlockPos(4,1,1),ModTeslaProfiles.MAIN_ID,new Vec3(10,0,0));
        h.runAfterDelay(1,()->{
            check(level.getBlockState(protectedField.getBlockPos()).is(ModBlocks.FIELD_GENERATOR.get()),"main contact cannot consume a connected field generator");
            check(source.energy()==0&&source.lastEnergyInputKind()==TeslaEnergyBallEntity.EnergyInput.NONE,"connected field generator provides no energy");
            source.discard();corners.forEach(be->level.setBlock(be.getBlockPos(),Blocks.AIR.defaultBlockState(),3));h.succeed();
        });
    }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=40)
    public static void miniPassesFieldAndStoneWhileMainRemainsInClosedField(GameTestHelper h) {
        var corners=com.juicyslew.moonstation14.ms14.singularity.containment.ContainmentFixture.placeBox(h);
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();
        var initialVolumes=com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime.closedVolumes(level);
        check(!initialVolumes.isEmpty(),"native field fixture produces a real closed volume");
        var start=h.absolutePos(new BlockPos(3,3,3));
        var ownMin=corners.stream().map(net.minecraft.world.level.block.entity.BlockEntity::getBlockPos)
                .reduce(BlockPos::min).orElseThrow();
        var ownMax=corners.stream().map(net.minecraft.world.level.block.entity.BlockEntity::getBlockPos)
                .reduce(BlockPos::max).orElseThrow();
        var volume=initialVolumes.stream().filter(candidate->candidate.min().equals(ownMin)&&candidate.max().equals(ownMax))
                .findFirst().orElseThrow(()->new GameTestAssertException("owned generator corners have no matching closed volume "+ownMin+".."+ownMax+"; candidates="+initialVolumes));
        check(start.getX()+.5>=volume.min().getX()&&start.getX()+.5<=volume.max().getX()
                &&start.getY()+.5>=volume.min().getY()&&start.getY()+.5<=volume.max().getY()
                &&start.getZ()+.5>=volume.min().getZ()&&start.getZ()+.5<=volume.max().getZ(),
                "main and mini start inside their own live volume bounds");
        BlockPos wall=new BlockPos(8,3,3);h.setBlock(wall,Blocks.STONE);
        var miniRef=new TeslaEnergyBallEntity[1];var mainRef=new TeslaEnergyBallEntity[1];
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            private void clean(){if(miniRef[0]!=null&&!miniRef[0].isRemoved())miniRef[0].discard();if(mainRef[0]!=null&&!mainRef[0].isRemoved())mainRef[0].discard();
                corners.forEach(be->level.setBlock(be.getBlockPos(),Blocks.AIR.defaultBlockState(),3));h.setBlock(wall,Blocks.AIR);}
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        var mini=spawn(h,new BlockPos(3,3,3),ModTeslaProfiles.MINI_ID,Vec3.ZERO);miniRef[0]=mini;
        mini.setPos(mini.getX()+.5,mini.getY()+.5,mini.getZ()+.5);
        // No main source is present while the external impulse tests mini collision policy; a live
        // main target would correctly replace the impulse with continuous orbit steering.
        h.runAfterDelay(1,()->{
            check(mini.isMini()&&mini.energy()==0,"mini profile initializes normally");
            mini.setDeltaMovement(new Vec3(10,0,0));
            h.runAfterDelay(15,()->{
                var stone=h.absolutePos(wall);
                check(mini.getX()>stone.getX()+1,"profile NONE mini crosses live field and wall after impulse; pos="+mini.position()+" velocity="+mini.getDeltaMovement()+" wall="+stone+" faces="+com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime.currentFaces(level).size());
                check(level.getBlockState(stone).is(Blocks.STONE),"mini pass-through does not alter physical wall");
                var stillOwned=com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime.closedVolumes(level).stream().anyMatch(candidate->candidate.equals(volume));
                check(stillOwned,"the exact closed volume built by this fixture remains live");
                var main=spawn(h,new BlockPos(3,3,3),ModTeslaProfiles.MAIN_ID,Vec3.ZERO);mainRef[0]=main;main.setPos(start.getX()+.5,start.getY()+.5,start.getZ()+.5);
                main.setDeltaMovement(new Vec3(10,0,0));
                h.runAfterDelay(2,()->{
                check(main.getX()<=volume.max().getX()+.5&&main.getX()>=volume.min().getX()+.5,"main profile remains inside its own closed field while bouncing from the face; pos="+main.position()+" velocity="+main.getDeltaMovement()+" volume="+volume);
                check(main.isAlive()&&mini.isAlive()&&mini.energy()==0,"both profiles remain live and mini remains nonfeeding through contrasting collision behavior");
                h.succeed();
                });
            });
        });
    }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=30)
    public static void mainFoodConsumesRegisteredBeaconForResolvedFlatEnergyOnly(GameTestHelper h) {
        var item=ModBlocks.SINGULARITY_BEACON.get().asItem();
        check(item instanceof com.juicyslew.moonstation14.item.custom.SingularityBeaconItem,"portable beacon is the registered resolved SingularityFood implementation");
        var food=(com.juicyslew.moonstation14.item.custom.SingularityBeaconItem)item;
        check(food.singularityFoodEnergy()==120&&food.singularityFoodFactor()==1,"food values come from resolved beacon profile");
        BlockPos local=new BlockPos(4,4,4);var world=h.absolutePos(local);
        var source=ModEntities.TESLA_ENERGY_BALL.get().create(h.getLevel());
        check(source!=null&&source.initializeSourceProfile(ModTeslaProfiles.MAIN_ID),"resolved main source creates");
        source.moveTo(world.getX()+.5,world.getY()+.5,world.getZ()+.5);check(h.getLevel().addFreshEntity(source),"main source joins native level");
        var pa=com.juicyslew.moonstation14.ms14.singularity.pa.ModParticleAccelerators.catalog(h.getLevel()).get(com.juicyslew.moonstation14.ms14.singularity.pa.ModParticleAccelerators.TYPE.typeId());
        for(int i=0;i<3;i++)source.receiveParticleEnergy(pa.strength(com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorStrength.LEVEL_1));
        check(source.energy()==60,"three ordinary resolved PA receiver inputs establish exact source energy");
        var loose=com.juicyslew.moonstation14.entities.LooseItemEntity.create(h.getLevel(),source.position(),new net.minecraft.world.item.ItemStack(item));
        check(h.getLevel().addFreshEntity(loose),"registered beacon item is carried by native LooseItemEntity");
        h.runAfterDelay(1,()->{
            var balls=h.getLevel().getEntitiesOfClass(TeslaEnergyBallEntity.class,new net.minecraft.world.phys.AABB(world).inflate(2));
            var mini=balls.stream().filter(TeslaEnergyBallEntity::isMini).findFirst().orElse(null);
            check(loose.isRemoved()&&source.energy()==80&&source.lastEnergyInputKind()==TeslaEnergyBallEntity.EnergyInput.LOOSE_ITEM
                    &&source.lastEnergyInputAmount()==120,"native loose beacon contact uses flat food energy only and records LOOSE_ITEM; energy="+source.energy());
            check(mini!=null&&mini.energy()==0&&balls.size()==2,"resolved energy threshold makes exactly one ordinary mini");
            balls.forEach(TeslaEnergyBallEntity::discard);h.succeed();
        });
    }
}
