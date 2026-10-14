package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterBlockEntity;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterBlock;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ModChemMasters;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.chemistry.beaker.ModBeakers;
import com.juicyslew.moonstation14.ms14.jug.ModJugs;
import com.juicyslew.moonstation14.item.custom.JugItem;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.power.cable.CableChunkData;
import net.minecraft.world.item.ItemStack;
import java.util.Map;

/** Sparse unroofed 8x10 platform with the normal resolved receiver and live APC feed. */
public final class ChemMasterFixtureService {
    private static final int WIDTH=8, DEPTH=10, AIR_HEIGHT=3;
    private static final BlockPos MACHINE=new BlockPos(3,1,8), APC=new BlockPos(3,1,6), SOURCE=new BlockPos(3,1,2), SUB=new BlockPos(3,1,4);
    private static final BlockPos[] SUPPLIES={new BlockPos(0,1,7),new BlockPos(0,1,8),new BlockPos(0,1,9),new BlockPos(7,1,7),new BlockPos(7,1,8),new BlockPos(7,1,9),new BlockPos(7,1,6)};
    private static final BlockPos[] JUG_SUPPLIES={new BlockPos(1,1,8),new BlockPos(5,1,9),new BlockPos(1,1,2),new BlockPos(2,1,2),new BlockPos(6,1,2),new BlockPos(7,1,2),new BlockPos(1,1,3),new BlockPos(2,1,3),new BlockPos(6,1,3),new BlockPos(7,1,3)};
    private static final String[] JUG_REAGENTS={"water","sugar","sodium","chlorine","oxygen","iron","ethanol","ammonia","carbon","silicon"};
    private static final String[] MIXTURE_REAGENTS={"ale","milk","coffee","cola","beer","absinthe"};
    private ChemMasterFixtureService() { }
    public static String build(ServerLevel level, BlockPos origin) {
        String invalid=preflight(level,origin); if(invalid!=null)return "Refused with no changes: "+invalid;
        var machinePolicy=ModChemMasters.resolved(level,ModChemMasters.CHEM_MASTER_ID);
        var small=ModBeakers.resolved(level,ModBeakers.SMALL_ID);var large=ModBeakers.resolved(level,ModBeakers.LARGE_ID);
        var jugPolicy=ModJugs.resolved(level);
        if(machinePolicy==null||small==null||small.solutionContainer().maxVolume()!=50||large==null||large.solutionContainer().maxVolume()!=100)return "Refused with no changes: resolved ChemMaster and 50u/100u beaker policies unavailable.";
        if(jugPolicy==null||!jugPolicy.drainableSolution().enabled()||!"drink".equals(jugPolicy.drainableSolution().solutionName())||JugItem.capacityFromResolved(jugPolicy)!=200f)return "Refused with no changes: active drainable drink jug policy with resolved 200u capacity unavailable.";
        for(String id:JUG_REAGENTS)if(ModReagents.catalog(level).get(ModReagents.createKey(id).location())==null)return "Refused with no changes: resolved reagent unavailable: "+id;
        for(String id:MIXTURE_REAGENTS)if(ModReagents.catalog(level).get(ModReagents.createKey(id).location())==null)return "Refused with no changes: resolved reagent unavailable: "+id;
        for(int x=0;x<WIDTH;x++)for(int z=0;z<DEPTH;z++)level.setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),3);
        for(int z=2;z<=8;z++)level.setBlock(origin.offset(4,1,z),Blocks.STONE.defaultBlockState(),3);
        for(int z=4;z<=6;z++)level.setBlock(origin.offset(2,1,z),Blocks.STONE.defaultBlockState(),3);
        place(level,origin.offset(SOURCE),PowerDeviceKind.HV_SOURCE,Direction.EAST);
        place(level,origin.offset(SUB),PowerDeviceKind.HV_MV_SUBSTATION,Direction.EAST);
        place(level,origin.offset(APC),PowerDeviceKind.APC,Direction.WEST);
        if(!CableStorage.place(level,origin.offset(4,1,2),Direction.WEST,CableTier.HV)||!CableStorage.place(level,origin.offset(4,1,3),Direction.WEST,CableTier.HV)||!CableStorage.place(level,origin.offset(4,1,4),Direction.WEST,CableTier.HV)
                ||!CableStorage.place(level,origin.offset(2,1,4),Direction.EAST,CableTier.MV)||!CableStorage.place(level,origin.offset(2,1,5),Direction.EAST,CableTier.MV)||!CableStorage.place(level,origin.offset(2,1,6),Direction.EAST,CableTier.MV)
                ||!CableStorage.place(level,origin.offset(4,1,6),Direction.WEST,CableTier.APC)||!CableStorage.place(level,origin.offset(4,1,7),Direction.WEST,CableTier.APC)||!CableStorage.place(level,origin.offset(4,1,8),Direction.WEST,CableTier.APC))return "PARTIAL: cable placement failed after cleared platform preflight at origin "+origin;
        level.setBlock(origin.offset(MACHINE),ModBlocks.CHEM_MASTER.get().defaultBlockState().setValue(ChemMasterBlock.FACING,Direction.SOUTH),3);
        if(!(level.getBlockEntity(origin.offset(MACHINE)) instanceof ChemMasterBlockEntity machine))return "PARTIAL: platform and power chain placed, but ChemMaster block entity failed at "+origin.offset(MACHINE);
        var water=ModReagents.createKey("water");var sugar=ModReagents.createKey("sugar");
        ItemStack[] stock={new ItemStack(ModItems.BEAKER.get()),new ItemStack(ModItems.BEAKER.get()),new ItemStack(ModItems.BEAKER_LARGE.get()),new ItemStack(ModItems.BEAKER_LARGE.get()),new ItemStack(ModItems.BEAKER.get()),new ItemStack(ModItems.BEAKER_LARGE.get()),new ItemStack(ModItems.BEAKER_LARGE.get())};
        stock[4].set(ModDataComponents.REAGENT.get(),new ReagentComponent(Map.of(water,20f,sugar,10f)));
        stock[5].set(ModDataComponents.REAGENT.get(),new ReagentComponent(Map.of(water,40f,sugar,20f)));
        Map<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>,Float> mixture=new java.util.LinkedHashMap<>();
        for(String id:MIXTURE_REAGENTS)mixture.put(ModReagents.createKey(id),10f);
        stock[6].set(ModDataComponents.REAGENT.get(),new ReagentComponent(mixture));
        for(int i=0;i<stock.length;i++)if(!level.addFreshEntity(LooseItemEntity.create(level,origin.offset(SUPPLIES[i]).getCenter(),stock[i])))return "PARTIAL: platform built; supply drop failed at "+origin.offset(SUPPLIES[i])+" after "+i+" beakers.";
        ItemStack[] jugs=new ItemStack[JUG_REAGENTS.length];
        for(int i=0;i<jugs.length;i++){
            var key=ModReagents.createKey(JUG_REAGENTS[i]);
            jugs[i]=new ItemStack(ModItems.JUG.get());
            jugs[i].set(ModDataComponents.REAGENT.get(),new ReagentComponent(Map.of(key,200f)));
        }
        for(int i=0;i<jugs.length;i++)if(!level.addFreshEntity(LooseItemEntity.create(level,origin.offset(JUG_SUPPLIES[i]).getCenter(),jugs[i])))return "PARTIAL: platform and beakers placed; jug supply drop failed at "+origin.offset(JUG_SUPPLIES[i])+".";
        return "Built open-air ChemMaster platform; origin="+origin+", bounds 8x10x4; machine="+origin.offset(MACHINE)+" facing SOUTH, resolved demand="+machine.powerLoadWatts()+" W; 17 ordinary loose supplies (7 beakers, 10 resolved 200u drink jugs). Use status after normal power-solve ticks.";
    }
    public static String status(ServerLevel level,BlockPos origin) {
        BlockPos pos=origin.offset(MACHINE);
        var chunk=level.getChunkSource().getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,false);
        if(chunk==null)return "NOT READY: target chunk is not already FULL-loaded at "+pos+" (origin="+origin+").";
        if(!(chunk.getBlockEntity(pos) instanceof ChemMasterBlockEntity machine))return "NOT READY: no ChemMaster at "+pos+" (origin is platform lowest corner "+origin+").";
        var meter=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.chemMasterMeter(level,machine);
        boolean powered=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.hasFullFreshChemMasterPower(level,machine,machine.placementIdentity());
        int loose=level.getEntitiesOfClass(LooseItemEntity.class,new net.minecraft.world.phys.AABB(origin.getX(),origin.getY(),origin.getZ(),origin.getX()+WIDTH,origin.getY()+AIR_HEIGHT+1,origin.getZ()+DEPTH)).size();
        return "origin="+origin+", machine="+pos+", demand="+machine.powerLoadWatts()+" W, meter="+meter+", fullFreshCurrentPlacement="+powered+", placementIdentity="+machine.placementIdentity()+", looseItemEntities currently in platform envelope="+loose+" (17 fixture supplies staged). Wait normal power ticks then repeat status; no forced grant.";
    }
    private static String preflight(ServerLevel l,BlockPos o) {
        for(int x=0;x<WIDTH;x++)for(int z=0;z<DEPTH;z++)for(int y=0;y<=AIR_HEIGHT;y++){
            BlockPos p=o.offset(x,y,z);
            if(p.getY()<l.getMinBuildHeight()||p.getY()>=l.getMaxBuildHeight()||!l.getWorldBorder().isWithinBounds(p)||l.getChunkSource().getChunk(p.getX()>>4,p.getZ()>>4,ChunkStatus.FULL,false)==null)return "all 8x10x4 footprint cells must be in bounds and already FULL-loaded (failed "+p+")";
            var state=l.getBlockState(p);
            if(!state.isAir()||!state.getFluidState().isEmpty()||l.getBlockEntity(p)!=null)return "footprint is occupied or contains fluid at "+p+"; build will not overwrite blocks";
        }
        var box=new net.minecraft.world.phys.AABB(o.getX(),o.getY(),o.getZ(),o.getX()+WIDTH,o.getY()+AIR_HEIGHT+1,o.getZ()+DEPTH);
        if(!l.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,box).isEmpty())return "fixture footprint contains an entity; move it before building";
        for(int cx=o.getX()>>4;cx<=(o.getX()+WIDTH-1)>>4;cx++)for(int cz=o.getZ()>>4;cz<=(o.getZ()+DEPTH-1)>>4;cz++){
            var chunk=l.getChunkSource().getChunk(cx,cz,ChunkStatus.FULL,false);
            var data=chunk==null?null:chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
            if(data instanceof CableChunkData cables)for(var record:cables.snapshot()){
                int wx=(cx<<4)+record.x(),wz=(cz<<4)+record.z();
                if(wx>=o.getX()&&wx<o.getX()+WIDTH&&wz>=o.getZ()&&wz<o.getZ()+DEPTH&&record.y()>=o.getY()&&record.y()<=o.getY()+AIR_HEIGHT)return "existing cable record conflicts with fixture footprint at "+new BlockPos(wx,record.y(),wz);
            }
        }
        return null;
    }
    private static void place(ServerLevel l,BlockPos p,PowerDeviceKind kind,Direction facing){if(kind==PowerDeviceKind.APC){if(!PowerDeviceFixturePlacement.placeApc(l,p,facing))throw new IllegalStateException("ChemMaster fixture APC support/placement failed at "+p);return;}var b=switch(kind){case HV_SOURCE->ModBlocks.HV_SOURCE.get();case HV_MV_SUBSTATION->ModBlocks.HV_MV_SUBSTATION.get();default->throw new IllegalArgumentException();};l.setBlock(p,b.defaultBlockState().setValue(PowerDeviceBlock.FACING,facing),3);}
}

