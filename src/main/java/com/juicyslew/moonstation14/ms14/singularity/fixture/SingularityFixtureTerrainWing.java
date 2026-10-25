package com.juicyslew.moonstation14.ms14.singularity.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.SingularityBeaconBlockEntity;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Small, self-contained sacrificial station/soil wing; refuses any pre-existing terrain or structures. */
final class SingularityFixtureTerrainWing {
    private SingularityFixtureTerrainWing() { }
    record Placed(List<BlockPos> owned, BlockPos breachCorner, BlockPos soil, BlockPos grass,
                  BlockPos beacon,List<CableFaceNode> beaconNodes,Map<BlockPos,net.minecraft.world.level.block.state.BlockState> ownedStates,
                  UUID beaconIdentity) {
        Placed { owned=List.copyOf(owned); beaconNodes=List.copyOf(beaconNodes); ownedStates=Map.copyOf(ownedStates); }
    }
    static boolean preflight(ServerLevel level,BlockPos origin) { return preflightFailure(level,origin)==null; }
    /** Read-only diagnosis preserving the same strict admission rules as preflight. */
    static String preflightFailure(ServerLevel level,BlockPos origin) {
        for(int x=27;x<=28;x++)if(!clear(level,origin.offset(x,0,8)))return obstruction(level,origin.offset(x,0,8));
        if(!clear(level,origin.offset(33,3,14)))return obstruction(level,origin.offset(33,3,14));
        BlockPos root=origin.offset(25,3,13);
        if(!clear(level,root)&&!level.getBlockState(root).is(Blocks.STONE))return obstruction(level,root);
        for(int x=25;x<=33;x++)if(hasAnyFaceRecordReadOnly(level,origin.offset(x,3,13),Direction.SOUTH))return "cable record at "+origin.offset(x,3,13)+" SOUTH";
        for(int x=26;x<=28;x++)if(!clear(level,origin.offset(x,3,13)))return obstruction(level,origin.offset(x,3,13));
        for(int x=29;x<=39;x++) for(int z=3;z<=13;z++) {
            if(!clear(level,origin.offset(x,0,z))) return obstruction(level,origin.offset(x,0,z));
            boolean perimeter=x==29||x==39||z==3||z==13, doorway=x==29&&z>=7&&z<=9;
            if(perimeter&&!doorway) for(int y=1;y<=4;y++) if(!clear(level,origin.offset(x,y,z)))return obstruction(level,origin.offset(x,y,z));
        }
        return null;
    }
    private static String obstruction(ServerLevel level,BlockPos pos) {
        if(!level.hasChunkAt(pos))return "unloaded block "+pos;
        var state=level.getBlockState(pos);
        return "occupied block "+pos+" state="+state+" fluid="+level.getFluidState(pos)+" blockEntity="+(level.getBlockEntity(pos)!=null);
    }
    static Placed build(ServerLevel level, BlockPos origin,SingularityFixtureLayout.Placed fixture) {
        // Independent of Moon world generation: use a bounded patch beside the existing fixture.
        // The command origin is the wing's terrain surface; the prior fixture already validates loaded envelope.
        var owned=new ArrayList<BlockPos>();
        BlockPos root=origin.offset(25,3,13);
        boolean ownRoot=level.getBlockState(root).isAir(); if(ownRoot)owned.add(root);
        owned.add(origin.offset(27,0,8)); owned.add(origin.offset(28,0,8));
        for(int x=26;x<=28;x++)owned.add(origin.offset(x,3,13));
        for(int x=29;x<=39;x++) for(int z=3;z<=13;z++) {
            BlockPos p=origin.offset(x,0,z);
            owned.add(p);
        }
        for(int x=29;x<=39;x++) for(int z=3;z<=13;z++) {
            boolean perimeter=x==29||x==39||z==3||z==13;
            // Leave a 3-wide doorway toward the containment breach; make one bounded two-block wall shell.
            boolean doorway=x==29&&z>=7&&z<=9;
            for(int y=1;y<=4;y++) {
                if(perimeter&&!doorway) {
                    BlockPos p=origin.offset(x,y,z); owned.add(p);
                }
            }
        }
        if(!preflight(level,origin)) return null;
        if(ownRoot)level.setBlock(root,Blocks.STONE.defaultBlockState(),3);
        BlockPos soil=origin.offset(34,0,8), grass=origin.offset(33,0,8);
        for(int x=27;x<=28;x++)level.setBlock(origin.offset(x,0,8),Blocks.STONE_BRICKS.defaultBlockState(),3);
        for(int x=29;x<=39;x++) for(int z=3;z<=13;z++) {
            BlockPos p=origin.offset(x,0,z);
            level.setBlock(p,(p.equals(grass)?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState(),3);
        }
        for(BlockPos p:owned) if(p.getY()>origin.getY()) level.setBlock(p,Blocks.STONE_BRICKS.defaultBlockState(),3);
        BlockPos beacon=origin.offset(33,3,14);
        // The fixture's export APC is at x=24,z=12. Extend its existing real APC tier
        // across the wing's supported north wall; the beacon remains an ordinary 5kW load.
        var nodes=new ArrayList<CableFaceNode>();
        for(int x=25;x<=33;x++) {
            BlockPos host=origin.offset(x,3,13);
            var node=new CableFaceNode(host,Direction.SOUTH,CableTier.APC);
            if(!CableStorage.place(level,host,Direction.SOUTH,CableTier.APC))
                throw new IllegalStateException("beacon APC branch placement failed at "+host);
            nodes.add(node);
        }
        level.setBlock(beacon,ModBlocks.SINGULARITY_BEACON.get().defaultBlockState(),3);
        if(!(level.getBlockEntity(beacon) instanceof SingularityBeaconBlockEntity))
            throw new IllegalStateException("resolved singularity beacon block entity missing at "+beacon);
        owned.add(beacon);
        var states=new java.util.LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(BlockPos p:owned)states.put(p,level.getBlockState(p));
        for(var node:nodes)states.putIfAbsent(node.host(),level.getBlockState(node.host()));
        var beaconIdentity=((SingularityBeaconBlockEntity)level.getBlockEntity(beacon)).placementIdentity();
        // Soil and grass are deliberately exposed as the outside sacrificial test strip.
        BlockPos breach=fixture.fieldGenerators().stream()
                .max(java.util.Comparator.<BlockPos>comparingInt(BlockPos::getX).thenComparingInt(p->-p.getY())
                        .thenComparingInt(p->-Math.abs(p.getZ()-(origin.getZ()+8)))
                        .thenComparingInt(p->-p.getZ())).orElseThrow();
        return new Placed(owned,breach,soil,grass,beacon,nodes,states,beaconIdentity);
    }
    private static boolean clear(ServerLevel level,BlockPos p) {
        return level.hasChunkAt(p)&&level.getBlockState(p).isAir()&&level.getFluidState(p).isEmpty()&&level.getBlockEntity(p)==null;
    }
    private static boolean hasAnyFaceRecordReadOnly(ServerLevel level,BlockPos pos,Direction face) {
        var chunk=level.getChunkSource().getChunk(pos.getX()>>4,pos.getZ()>>4,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false);
        if(!(chunk instanceof net.minecraft.world.level.chunk.LevelChunk loaded))return true;
        var data=loaded.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.POWER_CABLE_CHUNK.get());
        if(data==null)return false;
        return data.snapshot().stream().anyMatch(record->record.x()==(pos.getX()&15)&&record.y()==pos.getY()
                &&record.z()==(pos.getZ()&15)&&record.face()==face);
    }
}
