package com.juicyslew.moonstation14.ms14.singularity.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity;
import com.juicyslew.moonstation14.block.block_entity.SingularityBeaconBlockEntity;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.entities.SingularityEntity;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Live consumer tests: natural entity tick, registered profiles, real power allocation, and actual containment graph. */
@GameTestTags({"singuloosing", "singularity-horizon", "singularity-prototype", "singularity-fixture"})
@PrefixGameTestTemplate(false)
public final class SinguloosingLiveConsumptionGameTests {
    private SinguloosingLiveConsumptionGameTests() { }
    private static void check(boolean ok,String message){if(!ok)throw new GameTestAssertException(message);}

    @GameTest(template="empty",templateNamespace="moonstation14",timeoutTicks=40)
    public static void liveWaveCreditsDirtLessThanStationAndConsumesBeaconAndItemOnce(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel(); BlockPos p=h.absolutePos(new BlockPos(6,5,6));
        var ownedBlocks=new ArrayList<BlockPos>(); var ownedEntities=new ArrayList<net.minecraft.world.entity.Entity>();
        h.testInfo.addListener(new GameTestListener(){private void clean(){ownedEntities.forEach(net.minecraft.world.entity.Entity::discard);ownedBlocks.forEach(pos->level.setBlock(pos,Blocks.AIR.defaultBlockState(),3));}
            public void testStructureLoaded(GameTestInfo i){} public void testPassed(GameTestInfo i,GameTestRunner r){clean();}
            public void testFailed(GameTestInfo i,GameTestRunner r){clean();} public void testAddedForRerun(GameTestInfo a,GameTestInfo b,GameTestRunner r){clean();}});
        var source=ModEntities.SINGULARITY.get().create(level); check(source!=null,"registered singularity created");
        source.setPos(Vec3.atCenterOf(p)); check(level.addFreshEntity(source),"singularity admitted"); ownedEntities.add(source); source.feed(21);
        BlockPos dirt=p.east(), station=p.west(), beacon=p.south(), bedrock=p.above(), fieldNear=p.north(),fieldFar=p.north(2);
        level.setBlock(dirt,Blocks.DIRT.defaultBlockState(),3); level.setBlock(station,Blocks.STONE_BRICKS.defaultBlockState(),3); ownedBlocks.add(dirt);ownedBlocks.add(station);
        level.setBlock(bedrock,Blocks.BEDROCK.defaultBlockState(),3);ownedBlocks.add(bedrock);
        level.setBlock(beacon,ModBlocks.SINGULARITY_BEACON.get().defaultBlockState(),3);ownedBlocks.add(beacon);
        check(level.getBlockEntity(beacon) instanceof SingularityBeaconBlockEntity food&&food.resolvedProfile()!=null,"beacon resolves its typed food profile");
        level.setBlock(fieldNear,ModBlocks.FIELD_GENERATOR.get().defaultBlockState(),3);ownedBlocks.add(fieldNear);
        level.setBlock(fieldFar,ModBlocks.FIELD_GENERATOR.get().defaultBlockState(),3);ownedBlocks.add(fieldFar);
        var a=(FieldGeneratorBlockEntity)level.getBlockEntity(fieldNear); var b=(FieldGeneratorBlockEntity)level.getBlockEntity(fieldFar);
        a.receiveEmitterBolt();a.receiveEmitterBolt();check(a.toggleEnabled(),"first test field enabled");
        b.receiveEmitterBolt();b.receiveEmitterBolt();check(b.toggleEnabled(),"second test field enabled");
        FieldContainmentRuntime.refresh(level);
        check(FieldContainmentRuntime.hasConnection(level,a),"field-generator pair has a real resolved containment edge");
        var item=LooseItemEntity.create(level,Vec3.atCenterOf(p).add(0,0,-.25),new ItemStack(Items.STONE,64));
        check(level.addFreshEntity(item),"loose stack admitted");ownedEntities.add(item);
        double before=source.energy();int drainLevel=source.singularityLevel();long startTick=level.getGameTime(); var horizon=source.gravityProfile().eventHorizon();
        double flat= ((SingularityBeaconBlockEntity)level.getBlockEntity(beacon)).singularityFoodEnergy();
         double factor=((SingularityBeaconBlockEntity)level.getBlockEntity(beacon)).singularityFoodFactor();
         Vec3 initialPos=source.position(); double initialRadius=source.gravityProfile().eventHorizonRadiusAtLevel(drainLevel);
         double envelope=initialRadius+source.gravityProfile().maxSpeedAtLevel(drainLevel)*.5;
         String initialInventory=inventory(level,initialPos,envelope);
         var beaconId=((SingularityBeaconBlockEntity)level.getBlockEntity(beacon)).getBlockPos().asLong();
         var itemId=item.getUUID();
        h.runAfterDelay(1,()->{
            check(level.getBlockState(dirt).isAir()&&level.getBlockState(station).isAir(),"natural source tick removes both dirt and ordinary station block");
            check(level.getBlockState(beacon).isAir(),"natural source tick removes beacon food block");
            check(level.getBlockState(bedrock).is(Blocks.BEDROCK),"unbreakable bedrock boundary remains and receives no false credit");
            check(item.isRemoved(),"natural source tick removes loose item entity");
            check(level.getBlockEntity(fieldNear) instanceof FieldGeneratorBlockEntity&&level.getBlockEntity(fieldFar) instanceof FieldGeneratorBlockEntity,
                    "connected field generators remain protected below breach stage");
            double expected=source.gravityProfile().energyAfterSeconds(before,drainLevel,(level.getGameTime()-startTick)*.05)
                    +horizon.entityEnergy()+horizon.lowYieldTerrainEnergy()+horizon.terrainEnergy();
            expected=com.juicyslew.moonstation14.ms14.singularity.SingularityFoodMath.after(expected,flat,factor);
            check(Math.abs(source.energy()-expected)<.03,"live energy delta accounts for resolved stage drain, low-yield terrain, regular station yield, one item entity, and beacon food profile");
            double after=source.energy();int afterLevel=source.singularityLevel();long afterTick=level.getGameTime();
            h.runAfterDelay(11,()->{
                double noRepeat=source.gravityProfile().energyAfterSeconds(after,afterLevel,(level.getGameTime()-afterTick)*.05);
                if(Math.abs(source.energy()-noRepeat)>=.03) {
                    String diagnostic="horizon second-wave delta="+(source.energy()-noRepeat)+" energy="+after+"->"+source.energy()+" predictedDrainOnly="+noRepeat+
                            " pos="+initialPos+"->"+source.position()+" levels="+afterLevel+"->"+source.singularityLevel()+
                            " originalRemoved="+(level.getBlockState(dirt).isAir()&&level.getBlockState(station).isAir()&&level.getBlockState(beacon).isAir())+
                            " itemRemoved="+item.isRemoved()+" itemUUID="+itemId+" beaconPos="+beaconId+
                            " initialInventory="+initialInventory+" secondInventory="+inventory(level,source.position(),source.gravityProfile().eventHorizonRadiusAtLevel(source.singularityLevel()));
                    System.out.println(diagnostic);
                    throw new GameTestAssertException("next 0.5-second horizon wave does not credit already consumed matter again: "+diagnostic.substring(0,Math.min(820,diagnostic.length())));
                }
                check(level.getBlockState(dirt).isAir()&&level.getBlockState(station).isAir()&&level.getBlockState(beacon).isAir()&&item.isRemoved(),"original finite matter remains consumed after second wave");
                source.discard();h.succeed();
            });
        });
    }
    private static String inventory(ServerLevel level,Vec3 center,double radius) {
        var box=new net.minecraft.world.phys.AABB(center.x-radius,center.y-radius,center.z-radius,center.x+radius,center.y+radius,center.z+radius);
        var entries=new ArrayList<String>(); int total=0;
        var min=BlockPos.containing(box.minX,box.minY,box.minZ); var max=BlockPos.containing(box.maxX,box.maxY,box.maxZ);
        for(var pos:BlockPos.betweenClosed(min,max)) if(!level.getBlockState(pos).isAir()) { total++; if(entries.size()<32) entries.add("B"+pos+"="+net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock())); }
        for(var entity:level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,box,e->true)) { total++; if(entries.size()<64) entries.add("E"+entity.getUUID()+"="+entity.getType()+"@"+entity.position()); }
        return "center="+center+"r="+radius+"total="+total+"truncated="+(total>entries.size())+entries;
    }

     @GameTest(template="disposal_power_clear",templateNamespace="moonstation14",timeoutTicks=1000)
    public static void realFiniteApcCircuitPowersFiveKilowattBeaconThenPublishesUnpoweredAllocation(GameTestHelper h) {
         ServerLevel level=(ServerLevel)h.getLevel();
         BlockPos ticketOrigin=h.absolutePos(new BlockPos(3,1,2));
        var lease=com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(
                ticketOrigin.getX()-1,ticketOrigin.getY(),ticketOrigin.getZ()-1,ticketOrigin.getX()+8,ticketOrigin.getY()+6,ticketOrigin.getZ()+32));
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(h,lease,80,()->{
        BlockPos source=h.absolutePos(new BlockPos(3,1,2)),sub=h.absolutePos(new BlockPos(3,1,4)),apc=h.absolutePos(new BlockPos(3,1,6)),beaconPos=h.absolutePos(new BlockPos(5,1,8));
         List<CableFaceNode> nodes=new ArrayList<>(); var owned=new ArrayList<BlockPos>(); var ownedEntities=new ArrayList<SingularityEntity>();
         var bounds=h.testInfo.getStructureBounds();
         for(int z=2;z<=8;z++)h.assertTrue(bounds.contains(Vec3.atCenterOf(h.absolutePos(new BlockPos(2,1,z)))),"structure bounds contain circuit/support local x=2,z="+z);
         for(int z=2;z<=8;z++)h.assertTrue(bounds.contains(Vec3.atCenterOf(h.absolutePos(new BlockPos(5,1,z)))),"structure bounds contain circuit/support local x=5,z="+z);
         h.assertTrue(bounds.contains(Vec3.atCenterOf(h.absolutePos(new BlockPos(5,1,14)))),"structure bounds contain powered source start");
         h.assertTrue(bounds.contains(Vec3.atCenterOf(h.absolutePos(new BlockPos(5,1,30)))),"structure bounds contain unpowered source start and seeded first-walk margin");
         h.testInfo.addListener(new GameTestListener(){private void clean(){ownedEntities.forEach(net.minecraft.world.entity.Entity::discard);for(var n:nodes)CableStorage.remove(level,n.host(),n.face(),n.tier());owned.forEach(p->level.setBlock(p,Blocks.AIR.defaultBlockState(),3));}
            public void testStructureLoaded(GameTestInfo i){} public void testPassed(GameTestInfo i,GameTestRunner r){clean();}
            public void testFailed(GameTestInfo i,GameTestRunner r){clean();} public void testAddedForRerun(GameTestInfo a,GameTestInfo b,GameTestRunner r){clean();}});
        for(int z=2;z<=8;z++){BlockPos support=source.offset(1,0,z-2);level.setBlock(support,Blocks.STONE.defaultBlockState(),3);owned.add(support);}
        for(int z=4;z<=6;z++){BlockPos support=sub.offset(-1,0,z-4);level.setBlock(support,Blocks.STONE.defaultBlockState(),3);owned.add(support);}
        BlockPos branchTurn=source.offset(0,0,5); // local absolute (3,1,7), first SOUTH-facing node
        for(BlockPos support:List.of(branchTurn,branchTurn.east(),branchTurn.east(2))){level.setBlock(support,Blocks.STONE.defaultBlockState(),3);owned.add(support);}
        level.setBlock(source,ModBlocks.HV_SOURCE.get().defaultBlockState().setValue(PowerDeviceBlock.FACING,Direction.EAST),3);owned.add(source);
        level.setBlock(sub,ModBlocks.HV_MV_SUBSTATION.get().defaultBlockState().setValue(PowerDeviceBlock.FACING,Direction.EAST),3);owned.add(sub);
        check(PowerDeviceFixturePlacement.placeApc(level,apc,Direction.WEST),"ordinary supported APC placed");owned.add(apc);
        level.setBlock(beaconPos,ModBlocks.SINGULARITY_BEACON.get().defaultBlockState(),3);owned.add(beaconPos);
        check(level.getBlockEntity(beaconPos) instanceof SingularityBeaconBlockEntity,"real beacon BE placed");
        for(int z=2;z<=4;z++)place(level,nodes,source.offset(1,0,z-2),Direction.WEST,CableTier.HV);
        for(int z=4;z<=6;z++)place(level,nodes,sub.offset(-1,0,z-4),Direction.EAST,CableTier.MV);
        for(int z=6;z<=8;z++)place(level,nodes,apc.offset(1,0,z-6),Direction.WEST,CableTier.APC);
        for(int x=3;x<=5;x++)place(level,nodes,source.offset(x-3,0,5),Direction.SOUTH,CableTier.APC);
        var beacon=(SingularityBeaconBlockEntity)level.getBlockEntity(beaconPos);
        PowerFixtureReadiness.await(h,level,500,()->beacon.allocationKnown()&&beacon.receivedWatts()>=5000&&beacon.fullyPowered(),
                ()->"beaconKnown="+beacon.allocationKnown()+" watts="+beacon.receivedWatts()+" fullyPowered="+beacon.fullyPowered(),()->{
                    check(beacon.resolvedProfile()!=null&&beacon.resolvedProfile().powerWatts()==5000,"resolved default demand is exactly 5kW");
                     var attracted=ModEntities.SINGULARITY.get().create(level);check(attracted!=null,"powered test source created");
                     attracted.setPos(beaconPos.getX()+.5,beaconPos.getY()+.5,beaconPos.getZ()+6.5);
                      var poweredWalk=attracted.gravityProfile().randomWalk();
                      long poweredSeed=seedWithMinimumWalk(poweredWalk,60);
                      attracted.getRandom().setSeed(poweredSeed);
                      ownedEntities.add(attracted);
                      assertEntityInside(bounds,attracted,"powered source");
                      check(level.addFreshEntity(attracted),"powered test source admitted outside food contact range");
                     var firstWalkToward=Vec3.atCenterOf(beaconPos).subtract(attracted.position()).normalize();
                     BoundedGameTestWait.await(h,120,()->"powered source has not made its seeded first native walk",
                             ()->attracted.getDeltaMovement().lengthSqr()>0,()->{
                         check(beacon.fullyPowered()&&beacon.receivedWatts()>=5000,"beacon retains its fresh 5kW allocation at the first native walk");
                          check(!attracted.isRemoved()&&attracted.getDeltaMovement().normalize().dot(firstWalkToward)>.85,
                                  "fresh 5kW allocation biases the source's first native random-walk direction toward the beacon");
                         attracted.discard();
                         level.setBlock(source,Blocks.AIR.defaultBlockState(),3);
                        if(level.getBlockEntity(apc) instanceof com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity device)device.setEnergyJoules(0);
                        PowerFixtureReadiness.await(h,level,100,()->beacon.allocationKnown()&&beacon.receivedWatts()==0&&!beacon.fullyPowered(),
                                ()->"unpoweredKnown="+beacon.allocationKnown()+" watts="+beacon.receivedWatts()+" fullyPowered="+beacon.fullyPowered(),()->{
                                    check(!beacon.fullyPowered(),"fresh zero allocation deactivates attraction after actual source loss");
                                    var unpowered=ModEntities.SINGULARITY.get().create(level);check(unpowered!=null,"unpowered counterfactual source created");
                                     var walk=unpowered.gravityProfile().randomWalk();long seed=counterfactualSeed(walk,unpowered.gravityProfile().minSpeedAtLevel(1),unpowered.gravityProfile().maxSpeedAtLevel(1));
                                      unpowered.setPos(beaconPos.getX()+.5,beaconPos.getY()+.5,beaconPos.getZ()+22.5);
                                      unpowered.getRandom().setSeed(seed);
                                      ownedEntities.add(unpowered);
                                      assertEntityInside(bounds,unpowered,"unpowered source");
                                      check(level.addFreshEntity(unpowered),"unpowered counterfactual source admitted");
                                     long walkStartTick=level.getGameTime();
                                     var expected=counterfactualWalk(walk,seed,unpowered.gravityProfile().minSpeedAtLevel(1),unpowered.gravityProfile().maxSpeedAtLevel(1));
                                      BoundedGameTestWait.await(h,expected.ticks()+2,
                                              ()->"unpowered source did not make its seeded first native walk within "+(expected.ticks()+2)+" ticks; seed="+seed+" expectedTicks="+expected.ticks()+" expected="+expected.direction()+" actual="+unpowered.getDeltaMovement()+" removed="+unpowered.isRemoved()+" alive="+unpowered.isAlive()+" tickCount="+unpowered.tickCount+" energy="+unpowered.energy()+" level="+unpowered.singularityLevel()+" pos="+unpowered.position()+" chunk="+new net.minecraft.world.level.ChunkPos(unpowered.blockPosition())+" entityTicking="+level.isPositionEntityTicking(unpowered.blockPosition())+" gameTime="+walkStartTick+"->"+level.getGameTime()+" allocationKnown="+beacon.allocationKnown()+" watts="+beacon.receivedWatts()+" fullyPowered="+beacon.fullyPowered(),
                                             ()->unpowered.getDeltaMovement().lengthSqr()>0,()->{
                                         Vec3 actual=unpowered.getDeltaMovement();
                                         check(beacon.allocationKnown()&&beacon.receivedWatts()==0&&!beacon.fullyPowered(),
                                                 "beacon retains fresh zero allocation at first unpowered native walk; allocationKnown="+beacon.allocationKnown()+" watts="+beacon.receivedWatts()+" fullyPowered="+beacon.fullyPowered());
                                          check(actual.subtract(expected.direction()).length()<1e-6,
                                                  "unpowered node contributes no bias to the next native random walk; expected="+expected.direction()+" actual="+actual+" allocationKnown="+beacon.allocationKnown()+" watts="+beacon.receivedWatts()+" fullyPowered="+beacon.fullyPowered());
                                          unpowered.discard();
                                          h.succeed();
                                     });
                                });
                    });
                });
        },ticketOrigin,ticketOrigin.offset(5,1,6),h.absolutePos(new BlockPos(5,1,30)));
    }
     private record ExpectedWalk(int ticks,Vec3 direction) { }
     private static long seedWithMinimumWalk(com.juicyslew.moonstation14.ms14.singularity.SingularityGameplayProfile.RandomWalk walk,int minimumTicks) {
         for(long seed=1;seed<10000;seed++) {
             int ticks=walk.minimumIntervalTicks()+net.minecraft.util.RandomSource.create(seed)
                     .nextInt(walk.maximumIntervalTicks()-walk.minimumIntervalTicks()+1);
             if(ticks>=minimumTicks)return seed;
         }
         throw new GameTestAssertException("deterministic first-walk seed with sufficient beacon approach window unavailable");
     }
     private static void assertEntityInside(net.minecraft.world.phys.AABB bounds,net.minecraft.world.entity.Entity entity,String label) {
         var box=entity.getBoundingBox();
         if(!bounds.contains(box.minX,box.minY,box.minZ)||!bounds.contains(box.maxX,box.maxY,box.maxZ))
             throw new GameTestAssertException(label+" bounding box is outside actual structure bounds: entity="+box+" bounds="+bounds);
     }
    private static long counterfactualSeed(com.juicyslew.moonstation14.ms14.singularity.SingularityGameplayProfile.RandomWalk walk,double minimumSpeed,double maximumSpeed) {
        Vec3 toward=new Vec3(0,0,-1);
        for(long seed=1;seed<10000;seed++)if(counterfactualWalk(walk,seed,minimumSpeed,maximumSpeed).direction().normalize().dot(toward)<-.25)return seed;
        throw new GameTestAssertException("deterministic unpowered counterfactual seed unavailable");
    }
    private static ExpectedWalk counterfactualWalk(com.juicyslew.moonstation14.ms14.singularity.SingularityGameplayProfile.RandomWalk walk,long seed,double minimumSpeed,double maximumSpeed) {
        var random=net.minecraft.util.RandomSource.create(seed);
        int ticks=walk.minimumIntervalTicks()+random.nextInt(walk.maximumIntervalTicks()-walk.minimumIntervalTicks()+1);
        double speed=minimumSpeed+random.nextDouble()*(maximumSpeed-minimumSpeed);
        double x=random.nextDouble()*2-1,y=random.nextDouble()*2-1,z=random.nextDouble()*2-1;
        Vec3 direction=com.juicyslew.moonstation14.ms14.singularity.SingularityWalkMath.direction(x,y,z,walk.verticalScale(),
                walk.initialEscapeHorizontalBias(),Vec3.ZERO).scale(speed);
        return new ExpectedWalk(ticks,direction);
    }
    private static void place(ServerLevel level,List<CableFaceNode> nodes,BlockPos host,Direction face,CableTier tier){
        CableFaceNode node=new CableFaceNode(host,face,tier);check(CableStorage.place(level,host,face,tier),"real cable placement at "+node);nodes.add(node);
    }
}
