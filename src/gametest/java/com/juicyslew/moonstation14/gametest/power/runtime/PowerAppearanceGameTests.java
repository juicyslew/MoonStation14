package com.juicyslew.moonstation14.gametest.power.runtime;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.device.ModPowerAppearances;
import com.juicyslew.moonstation14.ms14.power.graph.LoadedPowerGraph;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"power"})
@PrefixGameTestTemplate(false)
public final class PowerAppearanceGameTests {
    private PowerAppearanceGameTests() { }

    @GameTest(template="power_live_chain_clear", timeoutTicks=400, templateNamespace="moonstation14")
    @GameTestTags({"power-machine-appearance"})
    public static void actualPowerChainProjectsDisplaysWithoutChangingTopologyOrBattery(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        BlockPos source=h.absolutePos(new BlockPos(3,1,2)), sub=h.absolutePos(new BlockPos(3,1,4));
        BlockPos apc=h.absolutePos(new BlockPos(3,1,6)), lamp=h.absolutePos(new BlockPos(3,1,8));
        for(int z=2;z<=8;z++) level.setBlock(h.absolutePos(new BlockPos(4,1,z)),Blocks.STONE.defaultBlockState(),3);
        for(int z=4;z<=6;z++) level.setBlock(h.absolutePos(new BlockPos(2,1,z)),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(source,ModBlocks.HV_SOURCE.get().defaultBlockState().setValue(PowerDeviceBlock.FACING,Direction.EAST),3);
        level.setBlock(sub,ModBlocks.HV_MV_SUBSTATION.get().defaultBlockState().setValue(PowerDeviceBlock.FACING,Direction.EAST),3);
        if(!PowerDeviceFixturePlacement.placeApc(level,apc,Direction.WEST)) fail("supported APC fixture placement failed");
        level.setBlock(lamp,ModBlocks.POWER_LAMP.get().defaultBlockState().setValue(PowerDeviceBlock.FACING,Direction.EAST),3);
        for(int z=2;z<=4;z++) require(CableStorage.place(level,h.absolutePos(new BlockPos(4,1,z)),Direction.WEST,CableTier.HV),"HV cable");
        for(int z=4;z<=6;z++) require(CableStorage.place(level,h.absolutePos(new BlockPos(2,1,z)),Direction.EAST,CableTier.MV),"MV cable");
        for(int z=6;z<=8;z++) require(CableStorage.place(level,h.absolutePos(new BlockPos(4,1,z)),Direction.WEST,CableTier.APC),"APC cable");
        PowerDeviceBlockEntity original=(PowerDeviceBlockEntity)level.getBlockEntity(apc);
        var sourceHv=new CableFaceNode(h.absolutePos(new BlockPos(4,1,2)),Direction.WEST,CableTier.HV);
        var subHv=new CableFaceNode(h.absolutePos(new BlockPos(4,1,4)),Direction.WEST,CableTier.HV);
        var subMv=new CableFaceNode(h.absolutePos(new BlockPos(2,1,4)),Direction.EAST,CableTier.MV);
        var apcMv=new CableFaceNode(h.absolutePos(new BlockPos(2,1,6)),Direction.EAST,CableTier.MV);
        var apcOutput=new CableFaceNode(h.absolutePos(new BlockPos(4,1,6)),Direction.WEST,CableTier.APC);
        PowerFixtureReadiness.await(h,level,600,
                ()->PowerFixtureReadiness.topologyReadyWithKnownPorts(level,sourceHv,subHv,subMv,apcMv,apcOutput)
                        &&level.getBlockState(source).getValue(PowerDeviceBlock.DISPLAY)==PowerDeviceBlock.Display.FIXED_ON
                        &&level.getBlockState(sub).getValue(PowerDeviceBlock.DISPLAY)==PowerDeviceBlock.Display.FULL
                         &&level.getBlockState(apc).getValue(PowerDeviceBlock.DISPLAY)==PowerDeviceBlock.Display.CHARGING
                         &&level.getBlockState(apc).getValue(PowerDeviceBlock.GLOW)
                        &&level.getBlockState(lamp).getValue(PowerDeviceBlock.LIT),
                ()->"source="+PowerGraphService.state(level,sourceHv)+", subHV="+PowerGraphService.state(level,subHv)
                        +", subMV="+PowerGraphService.state(level,subMv)+", apcMV="+PowerGraphService.state(level,apcMv)
                        +", output="+PowerGraphService.state(level,apcOutput)+", solve="+PowerRuntime.lastSolveDiagnostic(level),()->{
            require(level.getBlockState(source).getValue(PowerDeviceBlock.DISPLAY)==PowerDeviceBlock.Display.FIXED_ON,"live source state");
            require(level.getBlockState(sub).getValue(PowerDeviceBlock.DISPLAY)==PowerDeviceBlock.Display.FULL,"live substation conversion state");
             require(level.getBlockState(apc).getValue(PowerDeviceBlock.DISPLAY)==PowerDeviceBlock.Display.CHARGING,"APC reflects actual battery gain");
             require(level.getBlockState(apc).getValue(PowerDeviceBlock.GLOW),"powered APC glow follows its charged battery");
              var apcProfile = ModPowerAppearances.catalog(level).get(ModPowerAppearances.APC);
              require(ModBlocks.APC.get().getLightEmission(level.getBlockState(apc),level,apc)==apcProfile.lightLevel(),"APC uses resolved minor-light profile");
             require(level.getBrightness(LightLayer.BLOCK,apc.above())>0,"powered APC contributes nearby block light");
             require(level.getBlockState(lamp).getValue(PowerDeviceBlock.LIT),"lamp remains powered through full chain");
             require(level.getBlockEntity(apc)==original,"visual state transition retains BE identity");
             require(original.energyJoules()>500_000,"visual transition does not reset or replace APC battery");
             var node=new CableFaceNode(apc.relative(Direction.EAST),Direction.WEST,CableTier.APC);
             require(PowerGraphService.state(level,node).knowledge()==LoadedPowerGraph.Knowledge.KNOWN,"APC output graph remains known");
             require(original.toggleBreakerAuthorized(true,0),"fixture-authorized breaker open");
             PowerFixtureReadiness.await(h,level,160,
                     ()->level.getBlockState(apc).getValue(PowerDeviceBlock.GLOW),
                     ()->"charged battery should glow with breaker open; state="+level.getBlockState(apc)
                             +", energy="+original.energyJoules(),()->{
                 require(!original.breakerClosed(),"breaker remains open");
                 require(level.getBlockState(apc).getValue(PowerDeviceBlock.GLOW),"charged APC glows with breaker open");
                 level.setBlock(source,Blocks.AIR.defaultBlockState(),3);
                 original.setEnergyJoules(0);
                 PowerFixtureReadiness.await(h,level,160,
                         ()->!level.getBlockState(apc).getValue(PowerDeviceBlock.GLOW)
                                 &&ModBlocks.APC.get().getLightEmission(level.getBlockState(apc),level,apc)==0
                                 &&level.getBrightness(LightLayer.BLOCK,apc.above())==0,
                         ()->"uncharged APC should extinguish after world light updates; state="+level.getBlockState(apc)
                                 +", energy="+original.energyJoules()+", light="+level.getBrightness(LightLayer.BLOCK,apc.above())
                                 +", solve="+PowerRuntime.lastSolveDiagnostic(level),()->{
                     require(!level.getBlockState(apc).getValue(PowerDeviceBlock.GLOW),"empty APC without input does not glow");
                     require(ModBlocks.APC.get().getLightEmission(level.getBlockState(apc),level,apc)==0,"empty APC emits no block light");
                     require(level.getBrightness(LightLayer.BLOCK,apc.above())==0,"APC block light extinguishes after light-engine updates");
                     require(level.getBlockEntity(apc)==original,"breaker and power changes retain APC identity");
                     require(!original.breakerClosed(),"opening breaker does not get undone by power projection");
                     h.succeed();
                 });
             });
         });
    }

    @GameTest(template="empty", timeoutTicks=40, templateNamespace="moonstation14")
    @GameTestTags({"apc-wall-mount"})
    public static void exportedApcOutlineAndCollisionFollowWallFacingAndSupportRemoval(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        Direction[] facings={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
        for(int i=0;i<facings.length;i++) {
            BlockPos pos=h.absolutePos(new BlockPos(2+i*3,1,2)); Direction facing=facings[i];
            require(PowerDeviceFixturePlacement.placeApc(level,pos,facing),"supported APC placement "+facing);
            var shape=ModBlocks.APC.get().getShape(level.getBlockState(pos),level,pos,CollisionContext.empty()).bounds();
            var collision=ModBlocks.APC.get().getCollisionShape(level.getBlockState(pos),level,pos,CollisionContext.empty()).bounds();
            require(close(shape.minX,collision.minX)&&close(shape.maxX,collision.maxX)
                    &&close(shape.minZ,collision.minZ)&&close(shape.maxZ,collision.maxZ),"outline and collision match "+facing);
            if(facing==Direction.NORTH) require(close(shape.minZ,12.7/16)&&close(shape.maxZ,1),"north back sits at +Z wall");
            if(facing==Direction.SOUTH) require(close(shape.minZ,0)&&close(shape.maxZ,3.3/16),"south back sits at -Z wall");
            if(facing==Direction.EAST) require(close(shape.minX,0)&&close(shape.maxX,3.3/16),"east back sits at -X wall");
            if(facing==Direction.WEST) require(close(shape.minX,12.7/16)&&close(shape.maxX,1),"west back sits at +X wall");
        }
        BlockPos removable=h.absolutePos(new BlockPos(2,1,7));
        require(PowerDeviceFixturePlacement.placeApc(level,removable,Direction.NORTH),"APC before support removal");
        level.setBlock(removable.relative(Direction.SOUTH),Blocks.AIR.defaultBlockState(),3);
        require(level.getBlockState(removable).isAir(),"ordinary support removal removes wall-mounted APC");
        h.succeed();
    }
    private static boolean close(double a,double b){return Math.abs(a-b)<1e-5;}
    private static void require(boolean ok,String why){if(!ok)fail(why);}
    private static void fail(String why){throw new GameTestAssertException(why);}
}
