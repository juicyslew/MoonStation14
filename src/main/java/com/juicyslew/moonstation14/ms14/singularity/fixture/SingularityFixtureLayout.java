package com.juicyslew.moonstation14.ms14.singularity.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.custom.EmitterBlock;
import com.juicyslew.moonstation14.ms14.radiation.ModRadiationCollectors;
import com.juicyslew.moonstation14.ms14.radiation.RadiationCollectorBlockEntity;
import com.juicyslew.moonstation14.ms14.singularity.pa.ModParticleAccelerators;
import com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorAssembly;
import com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorPartIdentity;
import com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorFixturePlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import com.juicyslew.moonstation14.ms14.power.cable.CableChunkData;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock;

import java.util.List;

/** Shared, preflighted placement recipe; deliberately leaves wiring and operation to its caller. */
public final class SingularityFixtureLayout {
    private SingularityFixtureLayout() { }
    public enum Enclosure { COMPACT(2), WIDE(4); private final int halfSpan; Enclosure(int halfSpan){this.halfSpan=halfSpan;} }

    public record Placed(BlockPos origin, BlockPos reservedMin, BlockPos reservedMax, List<BlockPos> occupiedPositions, List<BlockPos> fieldGenerators,
                         BlockPos startupGenerator, List<BlockPos> emitters,
                         List<BlockPos> acceleratorParts, BlockPos acceleratorControl,
                         List<BlockPos> collectors, Enclosure enclosure) {
        public Placed {
            occupiedPositions = List.copyOf(occupiedPositions); fieldGenerators = List.copyOf(fieldGenerators);
            emitters = List.copyOf(emitters); acceleratorParts = List.copyOf(acceleratorParts);
            collectors = List.copyOf(collectors);
        }
    }

    /** Returns a refusal before any mutation unless the entire occupied/air envelope is clear and loaded. */
    public static Result build(ServerLevel level, BlockPos origin) {
        return build(level,origin,Enclosure.COMPACT);
    }
    public static Result build(ServerLevel level, BlockPos origin, Enclosure enclosure) {
        List<BlockPos> field = new java.util.ArrayList<>(), emitters = new java.util.ArrayList<>(), collectors = new java.util.ArrayList<>();
        // Both geometries share the original startup center; only the explicit wide
        // preset expands the field edge span for larger-stage movement.
        int half=enclosure.halfSpan;
        int minX=7-half,maxX=7+half,minY=5-half,maxY=5+half,minZ=9-half,maxZ=9+half;
        for (int x : new int[]{minX, maxX}) for (int z : new int[]{minZ, maxZ}) for (int y : new int[]{minY, maxY}) field.add(origin.offset(x,y,z));
        BlockPos startup=origin.offset(7,5,9), control=origin.offset(19,5,8);
        // Each ray shares Y/Z with a canonical corner and targets the first generator
        // on its inward X path. Keep emitter lane coordinates derived from the corners.
        var emitterLanes=new java.util.LinkedHashSet<BlockPos>();
        for(BlockPos corner:field) emitterLanes.add(new BlockPos(0,corner.getY()-origin.getY(),corner.getZ()-origin.getZ()));
        for(BlockPos lane:emitterLanes) {
            emitters.add(origin.offset(1,lane.getY(),lane.getZ()));
            emitters.add(origin.offset(13,lane.getY(),lane.getZ()));
        }
        for(BlockPos p:List.of(new BlockPos(2,2,8),new BlockPos(2,2,10),new BlockPos(12,2,8),new BlockPos(12,2,10))) collectors.add(origin.offset(p));
        var catalog=ModParticleAccelerators.catalog(level);
        var profile=catalog.get(ModParticleAccelerators.TYPE.typeId());
        if(profile==null) return Result.refused("resolved particle-accelerator profile unavailable");
        if(ModRadiationCollectors.catalog(level).get(ModRadiationCollectors.FULL_TANK_ID)==null) return Result.refused("resolved full-tank radiation collector profile unavailable");
        // Resolve positions from the same profile transform without requiring the parts to exist yet.
        List<BlockPos> paPositions=profile.parts().values().stream().map(part -> control.offset(ParticleAcceleratorAssembly.toWorldVec(part.offsetX(),part.offsetY(),Rotation.NONE))).toList();
        var footprint=new java.util.LinkedHashSet<BlockPos>();
        field.forEach(p->footprint.add(p)); footprint.add(startup); emitters.forEach(footprint::add); footprint.add(control); footprint.addAll(paPositions); collectors.forEach(footprint::add);
        BlockPos reservedMin=origin, reservedMax=origin.offset(26,11,18);
        // Reserve a clear, loaded shell plus a one-block neighbor halo so future feeds/exports cannot collide.
        for(int x=-1;x<=27;x++) for(int y=0;y<=11;y++) for(int z=-1;z<=19;z++) {
            BlockPos q=origin.offset(x,y,z);
            if(q.getY()<level.getMinBuildHeight()||q.getY()>=level.getMaxBuildHeight()||!level.getWorldBorder().isWithinBounds(q)
                    || !(level.getChunkSource().getChunk(q.getX()>>4,q.getZ()>>4,ChunkStatus.FULL,false) instanceof LevelChunk))
                return Result.refused("reserved placement envelope and halo must already be loaded and in bounds at "+q);
            if(!level.getBlockState(q).isAir()||!level.getFluidState(q).isEmpty()||level.getBlockEntity(q)!=null) return Result.refused("occupied reserved placement envelope at "+q);
        }
        var box=new net.minecraft.world.phys.AABB(origin.getX()-1,origin.getY(),origin.getZ()-1,origin.getX()+28,origin.getY()+12,origin.getZ()+20);
        if(!level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,box).isEmpty()) return Result.refused("placement footprint contains an entity");
        for(int cx=(origin.getX()-1)>>4;cx<=(origin.getX()+27)>>4;cx++) for(int cz=(origin.getZ()-1)>>4;cz<=(origin.getZ()+19)>>4;cz++) {
            var chunk=level.getChunkSource().getChunk(cx,cz,ChunkStatus.FULL,false);
            if(!(chunk instanceof LevelChunk loaded)) return Result.refused("reserved placement envelope cable scan requires loaded chunks");
            CableChunkData data=loaded.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
            if(data!=null) for(var record:data.snapshot()) { BlockPos cable=recordWorldPos(loaded,record);
                if(cable.getX()>=origin.getX()-1&&cable.getX()<=origin.getX()+27&&cable.getY()>=origin.getY()&&cable.getY()<=origin.getY()+11&&cable.getZ()>=origin.getZ()-1&&cable.getZ()<=origin.getZ()+19)
                    return Result.refused("cable record occupies reserved placement envelope at "+cable);
            }
        }
        for(BlockPos p:field) level.setBlock(p,ModBlocks.FIELD_GENERATOR.get().defaultBlockState(),3);
        level.setBlock(startup,ModBlocks.SINGULARITY_GENERATOR.get().defaultBlockState(),3);
        for(BlockPos p:emitters) level.setBlock(p,ModBlocks.EMITTER.get().defaultBlockState().setValue(EmitterBlock.FACING,p.getX()<origin.getX()+7?Direction.EAST:Direction.WEST),3);
        ParticleAcceleratorFixturePlacement.place(level,control,Rotation.NONE);
        for(BlockPos p:collectors) {
            level.setBlock(p,ModBlocks.RADIATION_COLLECTOR.get().defaultBlockState(),3);
            if(level.getBlockEntity(p) instanceof RadiationCollectorBlockEntity collector) {
                collector.selectProfile(ModRadiationCollectors.FULL_TANK_ID);
                collector.tankSlot(); // Create the canonical profile tank before startup captures its fuel baseline.
            }
        }
        return Result.placed(new Placed(origin,reservedMin,reservedMax,List.copyOf(footprint),field,startup,emitters,paPositions,control,collectors,enclosure));
    }

    private static BlockPos recordWorldPos(LevelChunk chunk, CableChunkData.Record record) {
        return new BlockPos(chunk.getPos().getMinBlockX() + record.x(), record.y(), chunk.getPos().getMinBlockZ() + record.z());
    }

    public record Result(Placed layout,String refusal) {
        static Result refused(String why){return new Result(null,why);} static Result placed(Placed p){return new Result(p,null);}
        public boolean successful(){return layout!=null;}
    }
}
