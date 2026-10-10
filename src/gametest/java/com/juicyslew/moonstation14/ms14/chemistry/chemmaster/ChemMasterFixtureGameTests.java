package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.item.custom.BeakerItem;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.fixture.ChemMasterFixtureService;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction.ChemMasterWorldDump;
import com.juicyslew.moonstation14.ms14.chemistry.beaker.ModBeakers;
import com.juicyslew.moonstation14.ms14.jug.ModJugs;
import com.juicyslew.moonstation14.item.custom.JugItem;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"chemmaster-fixture"})
@PrefixGameTestTemplate(false)
public final class ChemMasterFixtureGameTests {
    private ChemMasterFixtureGameTests() { }
    @GameTest(template="empty",templateNamespace="moonstation14",timeoutTicks=500)
    public static void openAirFixtureStocksPickupBeakersAndGetsRealPower(GameTestHelper helper) {
        ServerLevel level=(ServerLevel)helper.getLevel();
        BlockPos origin=helper.absolutePos(new BlockPos(3,1,2));
        assertBounds(helper,origin);
        String result=ChemMasterFixtureService.build(level,origin);
        helper.assertTrue(result.startsWith("Built open-air"),"build reports successful fixture: "+result);
        BlockPos machinePos=origin.offset(3,1,8);
        BlockPos[] jugPositions={origin.offset(1,1,8),origin.offset(5,1,9),origin.offset(1,1,2),origin.offset(2,1,2),origin.offset(6,1,2),origin.offset(7,1,2),origin.offset(1,1,3),origin.offset(2,1,3),origin.offset(6,1,3),origin.offset(7,1,3)};
        for(int index=0;index<jugPositions.length;index++) {
            BlockPos jugPos=jugPositions[index];
            if(index<2) helper.assertTrue(jugPos.distToCenterSqr(machinePos.getCenter())
                            <=4*ChemMasterWorldDump.DUMP_INTERACTION_RANGE*ChemMasterWorldDump.DUMP_INTERACTION_RANGE,
                    "initial water/sugar jugs start within source-and-target interaction reach: "+jugPos);
            helper.assertTrue(jugPos.getX()!=origin.getX()+4||jugPos.getZ()<origin.getZ()+2||jugPos.getZ()>origin.getZ()+8,
                    "jug position clears the post row: "+jugPos);
            for(BlockPos beakerPos:beakerPositions(origin)) {
                helper.assertTrue(!jugPos.equals(beakerPos),"jug supply positions are distinct from beakers");
            }
        }
        var machine=(ChemMasterBlockEntity)level.getBlockEntity(machinePos);
        helper.assertTrue(machine!=null&&machine.powerLoadWatts()==250.0,"resolved machine demand is 250 W");
        helper.assertTrue(machine.inputSlot().snapshot().stack().isEmpty()&&machine.buffer().snapshot().contents().centContents().isEmpty(),"machine input and buffer begin empty");
        int initial=count(level,origin);
        helper.assertTrue(initial==17,"seven beakers and ten ordinary loose jug supplies, found "+initial);
        int empty50=0,empty100=0,filled50=0,filled100=0,mixed100=0,jugs=0,other=0;
        java.util.Set<String> jugSpecies=new java.util.HashSet<>();
        var water=ModReagents.createKey("water");var sugar=ModReagents.createKey("sugar");
        helper.assertTrue(BeakerItem.capacityFromResolved(ModBeakers.resolved(level,ModBeakers.SMALL_ID))==50
                        &&BeakerItem.capacityFromResolved(ModBeakers.resolved(level,ModBeakers.LARGE_ID))==100,
                "actual items resolve to the 50u and 100u policies");
        for(var e:level.getEntitiesOfClass(LooseItemEntity.class, box(origin))) {
            var stack=e.getItem();
            if(stack.is(ModItems.BEAKER.get())) {var r=stack.get(ModDataComponents.REAGENT.get());if(r==null||r.centContents().isEmpty())empty50++;else if(r.centContents().size()==2&&r.centContents().getOrDefault(water,0L)==2000&&r.centContents().getOrDefault(sugar,0L)==1000)filled50++;else other++;}
            else if(stack.is(ModItems.BEAKER_LARGE.get())) {var r=stack.get(ModDataComponents.REAGENT.get());if(r==null||r.centContents().isEmpty())empty100++;else if(r.centContents().size()==2&&r.centContents().getOrDefault(water,0L)==4000&&r.centContents().getOrDefault(sugar,0L)==2000)filled100++;else if(r.centContents().size()==6&&r.centContents().values().stream().allMatch(v->v==1000L)&&java.util.Arrays.stream(new String[]{"ale","milk","coffee","cola","beer","absinthe"}).allMatch(id->r.centContents().getOrDefault(ModReagents.createKey(id),0L)==1000L))mixed100++;else other++;}
            else if(stack.is(ModItems.JUG.get())) {var r=stack.get(ModDataComponents.REAGENT.get());if(r!=null&&r.centContents().size()==1&&r.centContents().values().iterator().next()==20000L){for(String id:new String[]{"water","sugar","sodium","chlorine","oxygen","iron","ethanol","ammonia","carbon","silicon"})if(r.centContents().containsKey(ModReagents.createKey(id)))jugSpecies.add(id);jugs++;}else other++;}
            else other++;
        }
        helper.assertTrue(JugItem.capacityFromResolved(ModJugs.resolved(level))==200f&&empty50==2&&empty100==2&&filled50==1&&filled100==1&&mixed100==1&&jugs==10&&jugSpecies.size()==10&&other==0,
                "exact stock includes preserved samples, six-reagent 60u sample, and ten single-reagent 200u jugs; other="+other);
        String repeat=ChemMasterFixtureService.build(level,origin);
        helper.assertTrue(repeat.startsWith("Refused")&&count(level,origin)==initial,"repeat build refuses before duplicate grants");
        BlockPos obstruction=origin.offset(7,3,9);level.setBlock(obstruction,Blocks.GLASS.defaultBlockState(),3);
        String occupied=ChemMasterFixtureService.build(level,origin);
        helper.assertTrue(occupied.startsWith("Refused")&&count(level,origin)==initial,"occupied preflight refuses without additional supply");
        level.setBlock(obstruction,Blocks.AIR.defaultBlockState(),3);
        PowerFixtureReadiness.await(helper,level,400,()->PowerRuntime.hasFullFreshChemMasterPower(level,machine,machine.placementIdentity()),
                ()->ChemMasterFixtureService.status(level,origin),()->{
                    var meter=PowerRuntime.chemMasterMeter(level,machine);
                    helper.assertTrue(meter.known()&&meter.deliveredWatts()==250.0,"actual fresh meter delivers full 250 W: "+meter);
                    helper.succeed();
                });
    }
    @GameTest(template="empty",templateNamespace="moonstation14",timeoutTicks=30)
    public static void pristineOccupiedPreflightWritesNothing(GameTestHelper helper) {
        ServerLevel level=(ServerLevel)helper.getLevel();BlockPos origin=helper.absolutePos(new BlockPos(3,1,2));assertBounds(helper,origin);
        BlockPos obstacle=origin.offset(7,3,9);level.setBlock(obstacle,Blocks.GLASS.defaultBlockState(),3);
        String result=ChemMasterFixtureService.build(level,origin);
        helper.assertTrue(result.startsWith("Refused")&&level.getBlockState(origin).isAir()
                        &&level.getBlockEntity(origin.offset(3,1,8))==null&&count(level,origin)==0
                        &&level.getBlockState(obstacle).is(Blocks.GLASS),
                "occupied preflight on a pristine site refuses without floor, machine, source, or supplies: "+result);
        helper.succeed();
    }
    private static void assertBounds(GameTestHelper helper,BlockPos o){
        for(int x:new int[]{0,7})for(int y:new int[]{0,3})for(int z:new int[]{0,9})
            helper.assertTrue(helper.getBounds().contains(o.offset(x,y,z).getCenter()),"fixture footprint corner lies inside template: "+o.offset(x,y,z));
        var supplyBox=box(o);
        for(double x:new double[]{supplyBox.minX,supplyBox.maxX})for(double y:new double[]{supplyBox.minY,supplyBox.maxY})for(double z:new double[]{supplyBox.minZ,supplyBox.maxZ})
            helper.assertTrue(helper.getBounds().contains(new net.minecraft.world.phys.Vec3(x,y,z)),"supply entity bounds fit template");
        for(BlockPos p:beakerPositions(o))helper.assertTrue(helper.getBounds().contains(p.getCenter()),"supply point lies inside template: "+p);
        for(BlockPos p:new BlockPos[]{o.offset(1,1,8),o.offset(5,1,9),o.offset(1,1,2),o.offset(2,1,2),o.offset(6,1,2),o.offset(7,1,2),o.offset(1,1,3),o.offset(2,1,3),o.offset(6,1,3),o.offset(7,1,3)})helper.assertTrue(helper.getBounds().contains(p.getCenter()),"jug supply point lies inside template: "+p);
    }
    private static BlockPos[] beakerPositions(BlockPos o){return new BlockPos[]{o.offset(0,1,7),o.offset(0,1,8),o.offset(0,1,9),o.offset(7,1,7),o.offset(7,1,8),o.offset(7,1,9),o.offset(7,1,6)};}
    private static net.minecraft.world.phys.AABB box(BlockPos o){return new net.minecraft.world.phys.AABB(o.getX(),o.getY(),o.getZ(),o.getX()+8,o.getY()+4,o.getZ()+10);}
    private static int count(ServerLevel level,BlockPos origin){return level.getEntitiesOfClass(LooseItemEntity.class,box(origin)).size();}
}
